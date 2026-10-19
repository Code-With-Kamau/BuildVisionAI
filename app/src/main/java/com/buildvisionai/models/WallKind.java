package com.buildvisionai.models;

/** Structural role of a wall; drives default thickness/material and which quantities it feeds. */
public enum WallKind {
    EXTERNAL("External", WallMaterial.BLOCK_200),
    INTERNAL("Internal", WallMaterial.BLOCK_150),
    PARTITION("Partition", WallMaterial.BLOCK_100);

    public final String label;
    public final WallMaterial defaultMaterial;

    WallKind(String label, WallMaterial defaultMaterial) {
        this.label = label;
        this.defaultMaterial = defaultMaterial;
    }

    public double defaultThicknessMm() { return defaultMaterial.thicknessMm; }

    /** External and internal walls carry load and get strip footings; partitions do not. */
    public boolean loadBearing() { return this != PARTITION; }
}
