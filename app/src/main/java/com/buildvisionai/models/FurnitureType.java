package com.buildvisionai.models;

public enum FurnitureType {
    BED("Bed", 1500, 1900, false),
    SOFA("Sofa", 2100, 900, false),
    DINING_TABLE("Dining Table", 1600, 900, false),
    WARDROBE("Wardrobe", 1200, 600, false),
    KITCHEN_COUNTER("Kitchen Counter", 2400, 600, false),
    SINK("Sink", 600, 500, true),
    TOILET("Toilet", 400, 700, true),
    SHOWER("Shower", 900, 900, true),
    BATHTUB("Bathtub", 1700, 750, true),
    TV_UNIT("TV Unit", 1500, 450, false),
    DESK("Desk", 1400, 700, false);

    public final String label;
    public final double defaultWidthMm;
    public final double defaultDepthMm;
    /** Plumbing fixtures feed the plumbing quantities. */
    public final boolean plumbingFixture;

    FurnitureType(String label, double defaultWidthMm, double defaultDepthMm, boolean plumbingFixture) {
        this.label = label;
        this.defaultWidthMm = defaultWidthMm;
        this.defaultDepthMm = defaultDepthMm;
        this.plumbingFixture = plumbingFixture;
    }
}
