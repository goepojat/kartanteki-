package fi.goepojat;

import org.lastools.*;
import java.util.Arrays;
import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;

public class Kartantekia {
	public static void main(String[] args) throws Exception {

		
		
		
		
		LASlibJNI.initialize();

		try (LASReader reader = new LASReader("K:\\koodi\\pullautin3\\U4234A2.laz")) {
			int count = 0;
			long st = System.currentTimeMillis();

			double[] bbox = { reader.getMinX(), reader.getMinY(), reader.getMinZ(), reader.getMaxX(), reader.getMaxY(),
					reader.getMaxZ() };

			System.out.println(Arrays.toString(bbox));
			
			float binsize = 2.0f;
			float binbuffer = 0.2f;
			
			LaserPointStore pointStore = new LaserPointStore(bbox,binsize,binbuffer);
			
			int histbinsize = 10;
			int histmin = (int) (bbox[2] - bbox[2] % histbinsize);
			int histmax = (int) (bbox[5] - bbox[5] % histbinsize);
			int histbins = (histmax - histmin) / histbinsize + 1;
			
			System.out.println("histbins: " + histbinsize);
			long[] histogram = new long[histbins];
			
			while (reader.readPoint()) {
				LASPoint point = reader.getPoint();

				LaserPoint lp = new LaserPoint(point.getX(), point.getY(), (float) point.getZ(),
						point.getClassification(), point.isFirst(), point.isIntermediate(), point.isLast(),
						point.isSingle(), point.isFirstOfMany(), point.isLastOfMany());
				
				pointStore.addPoint(lp);
								
				int zbin = ((int)(lp.z-lp.z%histbinsize)-histmin)/histbinsize;
				try {
					histogram[zbin]++;
				} catch (Exception e) {
					System.out.println(lp.toString());
					System.out.println("zbin: " + zbin);
					throw e;
				}

				if (count % 1000000 == 0) {
					System.out.println(count + " / " + (System.currentTimeMillis() - st));
				}

				count++;

			}

			System.out.println(count);
			System.out.println(System.currentTimeMillis() - st);
			System.out.println(pointStore.getNumBins() + " bins");
			System.out.println(pointStore.getNumPoints() + " points");
			System.out.println(Arrays.toString(histogram));

		}
	}
}
