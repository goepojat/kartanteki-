package fi.goepojat;

import java.util.Arrays;
import java.util.HashSet;

import it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;

public class LaserPointStore {
	// ObjectBigArrayBigList<LaserPoint> points = new ObjectBigArrayBigList<>();

	private Long2ObjectAVLTreeMap<ObjectBigArrayBigList> bins;
	private double[] bbox;
	private float binsize;
	private long ncols;
	private long nrows;
	private float binbuffer;
	private float bintotalsize;

	public LaserPointStore(double[] bbox, float binsize, float binbuffer) {
		this.bbox = bbox;
		this.binsize = binsize;
		this.binbuffer = binbuffer;

		this.bintotalsize = this.binsize + 2 * this.binbuffer;

		System.out.println("bbox: " + Arrays.toString(bbox));
		System.out.println("binsize: " + binsize);
		System.out.println("binbuffer: " + binbuffer);
		System.out.println("bintotalsize: " + bintotalsize);

		double sizex = (bbox[3] - bbox[0]);
		double sizey = (bbox[4] - bbox[1]);

		System.out.println("sizex: " + sizex);
		System.out.println("sizey: " + sizey);

		ncols = (long) Math.ceil(sizex / binsize);
		nrows = (long) Math.ceil(sizey / binsize);

		System.out.println("ncols: " + ncols);
		System.out.println("nrows: " + nrows);

		long nbins = ncols * nrows;

		System.out.println("nbins: " + nbins);

		bins = new Long2ObjectAVLTreeMap<>();

	}

	private long binxy2flat(long binx, long biny) {
		return biny * ncols + binx;
	}

	private long[] binflat2xy(long flatbin) {
		return new long[] { flatbin % ncols, (long) Math.floor(flatbin / nrows) };
	}

	private long getBinForDeltas(double dx, double dy, int bindx, int bindy) {
		float dxtrans = bindx * binbuffer;
		float dytrans = bindy * binbuffer;

		long binx = Math.max(0, (int) Math.floor((dx + dxtrans) / binsize));
		long biny = Math.max(0, (int) Math.floor((dy + dytrans) / binsize));

		return binxy2flat(binx, biny);

	}

	private HashSet<Long> calcBinsForPoint(LaserPoint lp) {
		double dx = lp.getX() - bbox[0];
		double dy = lp.getY() - bbox[1];
		
		HashSet<Long> binset= new HashSet<Long>(Arrays.asList(getBinForDeltas(dx, dy, -1, -1), getBinForDeltas(dx, dy, -1, 0),
				getBinForDeltas(dx, dy, -1, 1), getBinForDeltas(dx, dy, 0, -1), getBinForDeltas(dx, dy, 0, 0),
				getBinForDeltas(dx, dy, 0, 1), getBinForDeltas(dx, dy, 1, -1), getBinForDeltas(dx, dy, 1, 0),
				getBinForDeltas(dx, dy, 1, 1)));


		return binset;

	}

	public void addPoint(LaserPoint lp) {
		HashSet<Long> point_bins = calcBinsForPoint(lp);
		
		
		for (long bin : point_bins) {
			if (!bins.containsKey(bin)) {
				bins.put(bin, new ObjectBigArrayBigList<LaserPoint>());
			}
			bins.get(bin).add(lp);
		}

	}

	public long getNumPoints() {
		long psum = 0;
		for (ObjectBigArrayBigList lps : bins.values()) {
			psum += lps.size64();
		}

		return psum;
	}

	public int getNumBins() {
		return bins.size();
	}
}
