package fi.goepojat;

public class LaserPoint {
    private final double x;
    private final double y;
    private final float z;
    private final char pointClass;
    private byte returns = 0x00;

	LaserPoint(double x, double y, float z, char pointClass, boolean isFirst, boolean isIntermediate, boolean isLast,
			boolean isSingle, boolean isFirstOfMany, boolean isLastOfMany) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.pointClass = pointClass;

		if (isFirst) {
			this.returns |= 1 << 0;
		}
		if (isIntermediate) {
			this.returns |= 1 << 1;
		}
		if (isLast) {
			this.returns |= 1 << 2;
		}

		if (isSingle) {
			this.returns |= 1 << 3;
		}

		if (isFirstOfMany) {
			this.returns |= 1 << 4;
		}

		if (isLastOfMany) {
			this.returns |= 1 << 5;
		}
	}

	public boolean isFirst() {
		return ((this.returns >> 0) & 0x01) == 1;
	}

	public boolean isIntermediate() {
		return ((this.returns >> 1) & 0x01) == 1;
	}

	public boolean isLast() {
		return ((this.returns >> 2) & 0x01) == 1;
	}

	public boolean isSingle() {
		return ((this.returns >> 3) & 0x01) == 1;
	}

	public boolean isFirstOfMany() {
		return ((this.returns >> 4) & 0x01) == 1;
	}

	public boolean isLastOfMany() {
		return ((this.returns >> 5) & 0x01) == 1;
	}
	
	public String toString() {
		return "<LaserPoint " + this.x + ", " + this.y + ", " + this.z + " / " + (short)this.pointClass + ">";
		
	}

	public double getX() {
		return this.x;
	}

	public double getY() {
		return this.y;
	}
	
	public double getZ() {
	    return z;
	}
	
	/*
	 * HELPER FUNCTIONS FOR CLASSIFICATIONS
	 * 
	 *   Classification Value    Meaning
     *   0                       Created, never classified
     *   1                       Unclassified3
     *   2                       Ground
     *   3                       Low Vegetation
     *   4                       Medium Vegetation
     *   5                       High Vegetation
     *   6                       Building
     *   7                       Low Point (noise)
     *   8                       Reserved
     *   9                       Water
     *   10                      Rail
     *   11                      Road Surface
     *   12                      Reserved
     *   13                      Wire – Guard (Shield)
     *   14                      Wire – Conductor (Phase)
     *   15                      Transmission Tower
     *   16                      Wire-structure Connector (e.g. Insulator)
     *   17                      Bridge Deck
     *   18                      High Noise
     *   19-63                   Reserved
     *   64-255                  User definable
     */

    public boolean isGround() {
        return pointClass == 2;
    }

    public boolean isLowVegetation() {
        return pointClass == 3;
    }
    
    public boolean isMediumVegetation() {
        return pointClass == 4;
    }
    
    public boolean isHighVegetation() {
        return pointClass == 5;
    }
    
    public boolean isVegetation() {
        return isLowVegetation() || isMediumVegetation() || isHighVegetation();
    }
    
    public boolean isBuilding() {
        return pointClass == 6;
    }
    
    public boolean isWater() {
        return pointClass == 9;
    }
    
    public boolean isRoad() {
        return pointClass == 11;
    }

    public char getClassification() {
        return pointClass;
    }
}
