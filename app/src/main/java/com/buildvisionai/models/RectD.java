package com.buildvisionai.models;

/** Immutable axis-aligned rectangle in plan space (mm). */
public final class RectD {
    public final double minX, minY, maxX, maxY;

    public RectD(double x1, double y1, double x2, double y2) {
        this.minX = Math.min(x1, x2);
        this.maxX = Math.max(x1, x2);
        this.minY = Math.min(y1, y2);
        this.maxY = Math.max(y1, y2);
    }

    public double width() { return maxX - minX; }
    public double height() { return maxY - minY; }
    public double centerX() { return (minX + maxX) / 2; }
    public double centerY() { return (minY + maxY) / 2; }

    public boolean contains(double x, double y) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }

    public boolean intersects(RectD o) {
        return o.minX <= maxX && o.maxX >= minX && o.minY <= maxY && o.maxY >= minY;
    }

    public RectD union(RectD o) {
        return new RectD(Math.min(minX, o.minX), Math.min(minY, o.minY),
                Math.max(maxX, o.maxX), Math.max(maxY, o.maxY));
    }

    public RectD inflate(double m) { return new RectD(minX - m, minY - m, maxX + m, maxY + m); }

    @Override public String toString() { return "[" + minX + "," + minY + " - " + maxX + "," + maxY + "]"; }
}
