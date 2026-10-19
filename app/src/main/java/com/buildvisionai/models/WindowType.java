package com.buildvisionai.models;

public enum WindowType {
    SLIDING("Sliding", 1500, 1200),
    CASEMENT("Casement", 1200, 1200),
    FIXED("Fixed", 1200, 1200);

    public final String label;
    public final double defaultWidthMm;
    public final double defaultHeightMm;

    WindowType(String label, double defaultWidthMm, double defaultHeightMm) {
        this.label = label;
        this.defaultWidthMm = defaultWidthMm;
        this.defaultHeightMm = defaultHeightMm;
    }

    public double defaultSillMm() { return 900; }
}
