package fi.goepojat;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.DoubleSummaryStatistics;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.lastools.LASPoint;
import org.lastools.LASReader;
import org.lastools.LASlibJNI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.goepojat.contour.ContourTest;
import fi.goepojat.tiff.LAS2TIFF;
import fi.goepojat.util.ColorUtils;
import fi.goepojat.util.LASClassification;
import fi.goepojat.util.OClassification;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;

public class Kartantekia {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(Kartantekia.class);
    private static boolean initialized = false;

    static {
        try {
            LASlibJNI.initialize();
            initialized = true;
        } catch (Exception e) {
            initialized = false;
            LOGGER.error("Could not initialize LASlibJNI!", e);
        }
    }
    
    private final ExecutorService binProcessor;
    private final Path lasFile;
    private final LAS2TIFF las2Tiff;
    private final Path output; 
    
    public Kartantekia(Path lasFile, Path output, int concurrency) {
        if (!initialized)
            throw new IllegalStateException("LASlibJNI not initialized!");
        if (!Files.exists(lasFile))
            throw new IllegalArgumentException(lasFile.toAbsolutePath() + " does not exist!");
        this.lasFile = lasFile;
        this.output = output;
        this.binProcessor = Executors.newFixedThreadPool(concurrency, new ThreadFactory() {
            
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                return thread;
            }
        });
        this.las2Tiff = new LAS2TIFF();
    }
    
    public void process() throws InterruptedException {
        try (LASReader reader = new LASReader(lasFile.toAbsolutePath().toString())) {
            int count = 0;
            long st = System.currentTimeMillis();

            double[] bbox = { reader.getMinX(), reader.getMinY(), reader.getMinZ(), reader.getMaxX(), reader.getMaxY(),
                    reader.getMaxZ() };

            float binsize = 2.0f;
            float binbuffer = 0.2f;

            LaserPointStore pointStore = new LaserPointStore(bbox, binsize, binbuffer);

            int histbinsize = 10;
            int histmin = (int) (bbox[2] - bbox[2] % histbinsize);
            int histmax = (int) (bbox[5] - bbox[5] % histbinsize);
            int histbins = (histmax - histmin) / histbinsize + 1;

            LOGGER.info("histbins: " + histbinsize);
            long[] histogram = new long[histbins];

            while (reader.readPoint()) {
                LASPoint point = reader.getPoint();
//                if (point.isWithheld())
//                    continue;
                LaserPoint lp = new LaserPoint(point.getX(), point.getY(), (float) point.getZ(),
                        point.getClassification(), point.isFirst(), point.isIntermediate(), point.isLast(),
                        point.isSingle(), point.isFirstOfMany(), point.isLastOfMany());

                pointStore.addPoint(lp);

                int zbin = ((int) (lp.getZ() - lp.getZ() % histbinsize) - histmin) / histbinsize;
                try {
                    histogram[zbin]++;
                } catch (Exception e) {
                    LOGGER.info(lp.toString());
                    LOGGER.info("zbin: " + zbin);
                    throw e;
                }

                if (count % 1000000 == 0) {
                    LOGGER.info(count + " / " + (System.currentTimeMillis() - st));
                }
                count++;
            }

            LOGGER.info("Total count {}", count);
            LOGGER.info((System.currentTimeMillis() - st) + "ms");
            LOGGER.info(pointStore.getNumBins() + " bins");
            LOGGER.info(pointStore.getNumPoints() + " points");
            LOGGER.info(Arrays.toString(histogram));
            
            ObjectArrayList<Future<?>> futures = new ObjectArrayList<>(); 
            
            int w = pointStore.getWidth();
            int h = pointStore.getHeight();
            int bands = 3;
            int[] bandOffsets = { 0, 1, 2 };
            
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            
            ColorModel cm = ColorModel.getRGBdefault();
            WritableRaster contourRaster = cm.createCompatibleWritableRaster(w, h);
            
            
            AtomicInteger maxVegeDensity = new AtomicInteger(0);
            AtomicInteger minVegeDensity = new AtomicInteger(0);
            
            pointStore.getBins().entrySet().forEach(entry -> {
                Integer bin = entry.getKey();
                ObjectBigArrayBigList<LaserPoint> points = entry.getValue();
                Future<?> future = binProcessor.submit(() -> {
                    
                    // Jyrkänteen laskenta jotenkin näin:
                    // filtteröidään maapisteet
                    Collection<LaserPoint> groundPoints = points.stream().filter(LaserPoint::isGround).collect(Collectors.toList());
                    
                    // Lasketaan maapisteiden korkeuden keskiarvo contoureja varten
                    DoubleSummaryStatistics stats = groundPoints.stream().collect(Collectors.summarizingDouble(LaserPoint::getZ));
                    
                    int[] xy = pointStore.binflat2xy(bin);
                    int value = -1;
                    if (stats.getCount() > 0)
                        value = (int) Math.floor(stats.getAverage());
                    
                    // synkronointi koska monesta säikeestä arvon asetus
                    synchronized (contourRaster) {
                        contourRaster.setPixel(xy[0], xy[1], new int[] { value, 0, 0, 0 });
                    }
                    
                    // Minimi tarvitaan yleensä aina metsän määrittelyyn:
                    Optional<LaserPoint> minPoint = groundPoints.stream().min((lp1, lp2) -> {
                        if (lp1.getZ() < lp2.getZ())
                            return -1;
                        else if (lp1.getZ() > lp2.getZ())
                            return 1;
                        // Ok, löytyi kaksi samaa Z arvolla, kuinkas tässä edetään
//                        LOGGER.warn("Found two LaserPoints with same min Z value: {}, {}", lp1, lp2);
                        return 0;
                    });
                    
                    // voi olla vain yksi piste, silloin ei jyrkännettä
                    if (groundPoints.size() > 1) {
                    
                        // ja haetaan maksimi
                        Optional<LaserPoint> maxPoint = groundPoints.stream().max((lp1, lp2) -> {
                            if (lp1.getZ() > lp2.getZ())
                                return -1;
                            else if (lp1.getZ() < lp2.getZ())
                                return 1;
                            // Ok, löytyi kaksi samaa Z arvolla, kuinkas tässä edetään
    //                        LOGGER.warn("Found two LaserPoints with same max Z value: {}, {}", lp1, lp2);
                            return 0;
                        });
                        
                        // lasketaan etäisyys sekä korkeusero
                        LaserPoint maxLP = maxPoint.get();
                        LaserPoint minLP = minPoint.get();
                        
                        double heightDelta = maxLP.getZ() - minLP.getZ();
                        
                        if (heightDelta > 20.0) {
                            // Ok, tämä pikseli on jyrkänne
                            addPixelToImage(xy, image, OClassification.CLIFF);
                            return; // jatketaan muiden prosessointia
                        }
                        
                        double distance = Math.sqrt(Math.pow(maxLP.getX() - minLP.getX(), 2.0) + Math.pow(maxLP.getY() - minLP.getY(), 2.0) + Math.pow(maxLP.getZ() - minLP.getZ(), 2.0));
                    }
                    long amountOfPoints = points.size64();
                    
                    Map<Character, List<LaserPoint>> groupedPoints = points.stream().collect(Collectors.groupingBy(LaserPoint::getClassification));
                    
                    Optional<Entry<Character, List<LaserPoint>>> dominant = groupedPoints.entrySet().stream().max((entry1, entry2) -> {
                        char entry1Classification = entry1.getKey();
                        char entry2Classification = entry2.getKey();
                        List<LaserPoint> entry1Points = entry1.getValue();
                        List<LaserPoint> entry2Points = entry2.getValue();
                        
                        // tulkitaan maapisteeksi jos maapisteitä 80% kokonaismäärästä
                        if (entry1Classification == LASClassification.GROUND) {
                            int totalSize1 = entry1Points.size() + entry2Points.size();
                            if (entry1Points.size() / totalSize1 > 0.8) {
                                return -1;
                            } else {
                                return 1;
                            }
                        }
                        if (entry2Classification == LASClassification.GROUND) {
                            int totalSize2 = entry1Points.size() + entry2Points.size();
                            if (entry2Points.size() / totalSize2 > 0.8) {
                                return 1;
                            } else {
                                return -1;
                            }
                        }
                        if (entry1Points.size() > entry2Points.size())
                            return -1;
                        if (entry1Points.size() < entry2Points.size())
                            return 1;
                        return 0;
                    });
                    
                    Entry<Character, List<LaserPoint>> domin = dominant.get();
                    
                    char classification = domin.getKey();
                    
                    switch (classification) {
                    case LASClassification.WATER:
                        addPixelToImage(xy, image, OClassification.WATER);
                        break;
                    case LASClassification.GROUND:
                        addPixelToImage(xy, image, OClassification.GROUND);
                        break;
                    case LASClassification.BUILDING:
                    case LASClassification.ROAD_SURGFACE:
                    case LASClassification.RAIL:
                        addPixelToImage(xy, image, OClassification.MANMADE);
                    case LASClassification.LOW_VEGETATION:
                    case LASClassification.MEDIUM_VEGETATION:
                    case LASClassification.HIGH_VEGETATAION:
                        // Ok, kasvillisuutta, täytyy luokitella tiheyden mukaan taikka metsää
                        
                        List<LaserPoint> vegetationPoints = domin.getValue();
                        
                        // Katsotaan ekaksi onko metsää, metsää jos alimman maapisteen korkeus ylimpään vegepisteeseen esim. 8metriä
                        if (minPoint.isPresent()) {
                            LaserPoint lowestGroundPoint = minPoint.get();
                            LaserPoint maxVegetationPoint;
                            if (vegetationPoints.size() > 1) {
                                Optional<LaserPoint> opMaxVegetation = vegetationPoints.stream().max((lp1, lp2) -> {
                                    if (lp1.getZ() > lp2.getZ())
                                        return -1;
                                    if (lp1.getZ() < lp2.getZ())
                                        return 1;
                                    return 0;
                                });
                                maxVegetationPoint = opMaxVegetation.get();
                            } else {
                                maxVegetationPoint = vegetationPoints.iterator().next();
                            }
                            
                            if (maxVegetationPoint.getZ() - lowestGroundPoint.getZ() > 8) {
                                addPixelToImage(xy, image, OClassification.FOREST);
                                return;
                            }

                            
                        } else {
                            // hmm, hankalampi homma, pisteet vegeä mutta ei maapistettä
                            // TODO:
                        }
                        
                        float vegetationDensity = vegetationPoints.size() / amountOfPoints;
                        
                        int dens = Float.floatToIntBits(vegetationDensity);
                        if (dens > maxVegeDensity.get())
                            maxVegeDensity.set(dens);
                        if (dens < minVegeDensity.get())
                            minVegeDensity.set(dens);
                        
                        if (0.0 <= vegetationDensity && vegetationDensity < 0.25) {
                            addPixelToImage(xy, image, OClassification.VEGETATION_1);
                            return;
                        }
                        if (0.25 <= vegetationDensity && vegetationDensity < 0.5) {
                            addPixelToImage(xy, image, OClassification.VEGETATION_2);
                            return;
                        }
                        if (0.5 <= vegetationDensity && vegetationDensity < 0.75) {
                            addPixelToImage(xy, image, OClassification.VEGETATION_3);
                            return;
                        }
                        if (0.75 <= vegetationDensity && vegetationDensity <= 1.0) {
                            addPixelToImage(xy, image, OClassification.VEGETATION_4);
                            return;
                        }
                        
                    default:
                        break;
                    }
                    
                    // lasketaan kasvillisuuden tiheys
//                    Collection<LaserPoint> vegetationPoints = points.stream().filter(LaserPoint::isVegetation).collect(Collectors.toList());
//                    
//                    Collection<LaserPoint> buildingPoints = points.stream().filter(LaserPoint::isBuilding).collect(Collectors.toList());
//                    
//                    Collection<LaserPoint> roadPoints = points.stream().filter(LaserPoint::isRoad).collect(Collectors.toList());
//                    
//                    
//                    
//                    if (vegetationPoints.size() >= groundPoints.size()) {
//                        // Ok, kasvillisuus
//                        addPixelToImage(pointStore.binflat2xy(bin), image, (char)2);
//                        return; // jatketaan muiden prosessointia
//                    }
                    
//                    DoubleSummaryStatistics summary = points.stream().collect(Collectors.summarizingDouble(LaserPoint::getZ));
//                    
//                    summary.getAverage();
                });
                
                futures.add(future);
            });
            
            
            // Lets wait for all bins to be processes
            futures.forEach(future -> {
                try {
                    future.get();
                } catch (InterruptedException | ExecutionException e) {
                    LOGGER.error("Could not wait for completion", e);
                }
            });
            
            // Time to write tiff
            
            LOGGER.info("minVegeDensity " + Float.intBitsToFloat(minVegeDensity.get()));
            LOGGER.info("maxVegeDensity " + Float.intBitsToFloat(maxVegeDensity.get()));

            // Time to free some memory now
            System.gc();
            binProcessor.shutdown();
            binProcessor.awaitTermination(5, TimeUnit.MINUTES);
            pointStore.clear();
            futures.clear();
            System.gc();
            System.gc();
            
            // piirretään contourit
            // Create a color model compatible with this sample model/raster (TYPE_FLOAT)
            // Note that the number of bands must equal the number of color components in the 
            // color space (3 for RGB) + 1 extra band if the color model contains alpha 
//            ColorSpace colorSpace = ColorSpace.getInstance(ColorSpace.CS_sRGB);
            
            BufferedImage contourImage = new BufferedImage(cm, contourRaster, cm.isAlphaPremultiplied(), null);
            Collection<Integer> levels = Arrays.asList(0, 1, 2, 3, 4);
            BufferedImage contours = ContourTest.createContour(contourImage, levels);
            
            try {
                Files.deleteIfExists(output);
                Files.createFile(output);
                las2Tiff.writeTIFF(output.toFile(), image);
                
                Path contOutput = output.getParent().resolve(output.getFileName().toString() + "_cont.png");
                
                Files.deleteIfExists(contOutput);
                Files.createFile(contOutput);
                writePNG(contOutput.toFile(), contours);
            } catch (IOException e) {
                e.printStackTrace();
            }
            LOGGER.info((System.currentTimeMillis() - st) + "ms");
        }
    }

    private static void addPixelToImage(int[] xy, BufferedImage image, char c) {
        int rgb = resolveFromClassification(c);
        image.setRGB(xy[0], xy[1], rgb);
    }
    
    private static int resolveFromClassification(char classification) {
        return ColorUtils.getRGBIntOfClassification(classification);
    }

    public static void main(String[] args) throws InterruptedException {
        if (args.length != 2)
            throw new IllegalArgumentException("args should be path to las file and tiff output path");
        new Kartantekia(Paths.get(args[0]), Paths.get(args[1]), Runtime.getRuntime().availableProcessors()).process();
    }
    
    public void writePNG(File file, BufferedImage image) throws IOException {
        // Get the writer
        String format = "png";
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);

        if (!writers.hasNext()) {
            throw new IllegalArgumentException("No writer for: " + format);
        }
        ImageWriter writer = writers.next();

        try {
            // Create output stream
            ImageOutputStream output = ImageIO.createImageOutputStream(file);

            try {
                writer.setOutput(output);

                // Optionally, listen to progress, warnings, etc.

                ImageWriteParam param = writer.getDefaultWriteParam();

                // Optionally, control format specific settings of param (requires casting), or
                // control generic write settings like sub sampling, source region, output type etc.

                // Optionally, provide thumbnails and image/stream metadata
                writer.write(null, new IIOImage(image, null, null), param);
            } finally {
                // Close stream in finally block to avoid resource leaks
                output.close();
            }
        } finally {
            // Dispose writer in finally block to avoid memory leaks
            writer.dispose();
        }
    }
}
