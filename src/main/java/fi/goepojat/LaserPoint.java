package fi.goepojat;

public class LaserPoint {
	double x;
	double y;
	float z;
	char pointClass;
	byte returns = 0x00;

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
	
}
