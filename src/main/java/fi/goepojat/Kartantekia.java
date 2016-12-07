package fi.goepojat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.lastools.LASPoint;
import org.lastools.LASReader;
import org.lastools.LASlibJNI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.goepojat.tiff.LAS2TIFF;
import fi.goepojat.util.ColorUtils;
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
    private LAS2TIFF las2Tiff; 
    
    public Kartantekia(Path lasFile, int concurrency) {
        if (!initialized)
            throw new IllegalStateException("LASlibJNI not initialized!");
        if (!Files.exists(lasFile))
            throw new IllegalArgumentException(lasFile.toAbsolutePath() + " does not exist!");
        this.lasFile = lasFile;
        this.binProcessor = Executors.newFixedThreadPool(concurrency);
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
                if (point.isWithheld())
                    continue;
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
            
            BufferedImage image = new BufferedImage(pointStore.getWidth(), pointStore.getHeight(), BufferedImage.TYPE_INT_ARGB);
            
            
            pointStore.getBins().entrySet().forEach(entry -> {
                Integer bin = entry.getKey();
                ObjectBigArrayBigList<LaserPoint> points = entry.getValue();
                Future<?> future = binProcessor.submit(() -> {
                    
                    // Jyrkänteen laskenta jotenkin näin:
                    // filtteröidään maapisteet
                    Collection<LaserPoint> groundPoints = points.stream().filter(LaserPoint::isGround).collect(Collectors.toList());
                    
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
                        
                        // ja minimi
                        Optional<LaserPoint> minPoint = groundPoints.stream().min((lp1, lp2) -> {
                            if (lp1.getZ() < lp2.getZ())
                                return -1;
                            else if (lp1.getZ() > lp2.getZ())
                                return 1;
                            // Ok, löytyi kaksi samaa Z arvolla, kuinkas tässä edetään
    //                        LOGGER.warn("Found two LaserPoints with same min Z value: {}, {}", lp1, lp2);
                            return 0;
                        });
                        
                        // lasketaan etäisyys sekä korkeusero
                        LaserPoint maxLP = maxPoint.get();
                        LaserPoint minLP = minPoint.get();
                        
                        double heightDelta = maxLP.getZ() - minLP.getZ();
                        
                        if (heightDelta > 20.0) {
                            // Ok, tämä pikseli on jyrkänne
                            addPixelToImage(pointStore.binflat2xy(bin), image, (char)1);
                            return; // jatketaan muiden prosessointia
                        }
                        
                        
                        double distance = Math.sqrt(Math.pow(maxLP.getX() - minLP.getX(), 2.0) + Math.pow(maxLP.getY() - minLP.getY(), 2.0) + Math.pow(maxLP.getZ() - minLP.getZ(), 2.0));
                    }
                    
                    
                    Map<Character, List<LaserPoint>> groupedPoints = points.stream().collect(Collectors.groupingBy(LaserPoint::getClassification));
                    
                    Optional<Entry<Character, List<LaserPoint>>> dominant = groupedPoints.entrySet().stream().max(new Comparator<Map.Entry<Character, List<LaserPoint>>>() {

                        @Override
                        public int compare(Entry<Character, List<LaserPoint>> o1, Entry<Character, List<LaserPoint>> o2) {
                            if (applyFactor(o1) > applyFactor(o2))
                                return -1;
                            if (applyFactor(o1) < applyFactor(o2))
                                return 1;
                            return 0;
                        }
                        
                        private int applyFactor(Map.Entry<Character, List<LaserPoint>> first) {
                            // Here a factor could be assigned per classification
                            int amount = first.getValue().size();
                            switch (first.getKey()) {
                            default:
                               amount = amount * 1;
                            }
                            return amount;
                        }
                    });
                    
                    Entry<Character, List<LaserPoint>> domin = dominant.get();
                    addPixelToImage(pointStore.binflat2xy(bin), image, domin.getKey());
                    
                    
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

            try {
                Path output = Paths.get("C:/Users/Jani Simomaa/Desktop/output.tiff");
                Files.deleteIfExists(output);
                Files.createFile(output);
                las2Tiff.writeTIFF(output.toFile(), image);
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            binProcessor.shutdown();
            binProcessor.awaitTermination(5, TimeUnit.MINUTES);
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
        if (args.length != 1)
            throw new IllegalArgumentException("arg should be path to las file");
        new Kartantekia(Paths.get(args[0]), Runtime.getRuntime().availableProcessors()).process();
    }
}
