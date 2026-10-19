package com.buildvisionai.models;

public enum DoorType {
    SINGLE("Single door", 900),
    DOUBLE("Double door", 1500),
    SLIDING("Sliding door", 1800);

    public final String label;
    public final double defaultWidthMm;

    DoorType(String label, double defaultWidthMm) {
        this.label = label;
        this.defaultWidthMm = defaultWidthMm;
    }

    public double defaultHeightMm() { return 2100; }
}
