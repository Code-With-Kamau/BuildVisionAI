package com.buildvisionai.models;

public enum ColumnShape {
    CIRCULAR("Circular", 300, 300),
    SQUARE("Square", 225, 225),
    RECTANGULAR("Rectangular", 225, 450);

    public final String label;
    public final double defaultWidthMm;
    public final double defaultDepthMm;

    ColumnShape(String label, double defaultWidthMm, double defaultDepthMm) {
        this.label = label;
        this.defaultWidthMm = defaultWidthMm;
        this.defaultDepthMm = defaultDepthMm;
    }
}
