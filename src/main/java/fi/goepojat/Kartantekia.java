package fi.goepojat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import java.util.Collection;
import java.util.DoubleSummaryStatistics;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.lastools.LASPoint;
import org.lastools.LASReader;
import org.lastools.LASlibJNI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    
    public Kartantekia(Path lasFile, int concurrency) {
        if (!initialized)
            throw new IllegalStateException("LASlibJNI not initialized!");
        if (!Files.exists(lasFile))
            throw new IllegalArgumentException(lasFile.toAbsolutePath() + " does not exist!");
        this.lasFile = lasFile;
        this.binProcessor = Executors.newFixedThreadPool(concurrency);
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
            
//            Long2ObjectAVLTreeMap<ObjectBigArrayBigList<LaserPoint>> asd = ;
            
            
            pointStore.getBins().values().forEach(points -> {
                binProcessor.submit(() -> {
                    
                    // Jyrkänteen laskenta jotenkin näin:
                    // filtteröidään maapisteet
                    Collection<LaserPoint> groundPoints = points.stream().filter(LaserPoint::isGround).collect(Collectors.toList());
                    // ja haetaan maksimi
                    Optional<LaserPoint> maxPoint = groundPoints.stream().max((lp1, lp2) -> {
                        if (lp1.getZ() > lp2.getZ())
                            return -1;
                        else if (lp1.getZ() < lp2.getZ())
                            return 1;
                        // Ok, löytyi kaksi samaa Z arvolla, kuinkas tässä edetään
                        LOGGER.warn("Found two LaserPoints with same max Z value: {}, {}", lp1, lp2);
                        return 0;
                    });
                    
                    // ja minimi
                    Optional<LaserPoint> minPoint = groundPoints.stream().min((lp1, lp2) -> {
                        if (lp1.getZ() < lp2.getZ())
                            return -1;
                        else if (lp1.getZ() > lp2.getZ())
                            return 1;
                        // Ok, löytyi kaksi samaa Z arvolla, kuinkas tässä edetään
                        LOGGER.warn("Found two LaserPoints with same min Z value: {}, {}", lp1, lp2);
                        return 0;
                    });
                    
                    // lasketaan etäisyys sekä korkeusero
                    LaserPoint maxLP = maxPoint.get();
                    LaserPoint minLP = minPoint.get();
                    double heightDelta = maxLP.getZ() - minLP.getZ();
                    double distance = Math.sqrt(Math.pow(maxLP.getX() - minLP.getX(), 2.0) + Math.pow(maxLP.getY() - minLP.getY(), 2.0) + Math.pow(maxLP.getZ() - minLP.getZ(), 2.0));
                    
                    DoubleSummaryStatistics summary = points.stream().collect(Collectors.summarizingDouble(LaserPoint::getZ));
                    
                    
                    
                    summary.getAverage();
                });
            });
            
//            asd.entrySet().forEach(entry -> {
//                long bin = entry.getKey();
//                DoubleSummaryStatistics summary = entry.getValue().stream().collect(Collectors.summarizingDouble(LaserPoint::getZ));
//                summary.getAverage();
////                LOGGER.info("bin=" + bin + ", avg=" + summary.getAverage());
//            });
            
            binProcessor.shutdown();
            binProcessor.awaitTermination(5, TimeUnit.MINUTES);
            LOGGER.info((System.currentTimeMillis() - st) + "ms");
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        if (args.length != 1)
            throw new IllegalArgumentException("arg should be path to las file");
        new Kartantekia(Paths.get(args[0]), Runtime.getRuntime().availableProcessors()).process();
    }
}
