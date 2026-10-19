package com.buildvisionai.models;

public enum StairType {
    STRAIGHT("Straight", 1000, 3000),
    L_SHAPED("L-shaped", 2000, 2000),
    U_SHAPED("U-shaped", 2100, 2800);

    public final String label;
    /** Default bounding-box size (mm) before rotation. */
    public final double defaultWidthMm;
    public final double defaultDepthMm;

    StairType(String label, double defaultWidthMm, double defaultDepthMm) {
        this.label = label;
        this.defaultWidthMm = defaultWidthMm;
        this.defaultDepthMm = defaultDepthMm;
    }
}
