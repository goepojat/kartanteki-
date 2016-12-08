package fi.goepojat;

import java.util.Arrays;
import java.util.HashSet;

import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;

public class LaserPointStore {
	// ObjectBigArrayBigList<LaserPoint> points = new ObjectBigArrayBigList<>();

	private Int2ObjectAVLTreeMap<ObjectBigArrayBigList<LaserPoint>> bins;
	private double[] bbox;
	private float binsize;
	private int ncols;
	private int nrows;
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

		ncols = (int) Math.ceil(sizex / binsize);
		nrows = (int) Math.ceil(sizey / binsize);

		System.out.println("ncols: " + ncols);
		System.out.println("nrows: " + nrows);

		int nbins = ncols * nrows;

		System.out.println("nbins: " + nbins);

		bins = new Int2ObjectAVLTreeMap<>();

	}
	
	public int getWidth() {
	    return ncols;
	}
	
	public int getHeight() {
	    return nrows;
	}

	private int binxy2flat(int binx, int biny) {
		return biny * ncols + binx;
	}

    public int[] binflat2xy(int flatbin) {
        int x = flatbin % ncols;
        double y = (nrows - 1) - Math.floor(flatbin / nrows);
        return new int[] { x, (int) y };
    }

    private int getBinForDeltas(double dx, double dy, int bindx, int bindy) {
        float dxtrans = bindx * binbuffer;
        float dytrans = bindy * binbuffer;

        int binx = Math.min(ncols - 1, Math.max(0, (int) Math.floor((dx + dxtrans) / binsize)));
        int biny = Math.min(nrows - 1, Math.max(0, (int) Math.floor((dy + dytrans) / binsize)));

        int bin = binxy2flat(binx, biny);
        return bin;
    }

	private HashSet<Integer> calcBinsForPoint(LaserPoint lp) {
		double dx = lp.getX() - bbox[0];
		double dy = lp.getY() - bbox[1];
		
		
		
		HashSet<Integer> binset= new HashSet<>(Arrays.asList(getBinForDeltas(dx, dy, -1, -1), getBinForDeltas(dx, dy, -1, 0),
				getBinForDeltas(dx, dy, -1, 1), getBinForDeltas(dx, dy, 0, -1), getBinForDeltas(dx, dy, 0, 0),
				getBinForDeltas(dx, dy, 0, 1), getBinForDeltas(dx, dy, 1, -1), getBinForDeltas(dx, dy, 1, 0),
				getBinForDeltas(dx, dy, 1, 1)));


		return binset;

	}

	public void addPoint(LaserPoint lp) {
	    calcBinsForPoint(lp).forEach(bin -> {
	        bins.compute(bin, (t, list) -> {
                if (list == null)
                    list = new ObjectBigArrayBigList<>();
                list.add(lp);
                return list;
            });
        });
	}

	public long getNumPoints() {
		long psum = 0;
		for (ObjectBigArrayBigList<LaserPoint> lps : bins.values()) {
			psum += lps.size64();
		}

		return psum;
	}

	public int getNumBins() {
		return bins.size();
	}
	
    public Int2ObjectAVLTreeMap<ObjectBigArrayBigList<LaserPoint>> getBins() {
        return bins;
    }

    /**
     * Should be called for freeing memory
     */
    public void clear() {
        bins.clear();
        bins = null;
    }
}
