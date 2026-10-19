package com.buildvisionai.models;

/**
 * Masonry used for a wall. Unit sizes are nominal Kenyan market sizes (mm). The estimator
 * prices each unit via rate code {@code UNIT_<NAME>}.
 */
public enum WallMaterial {
    BLOCK_100("Hollow block 4\" (100 mm)", 100, 400, 200, 1),
    BLOCK_150("Hollow block 6\" (150 mm)", 150, 400, 200, 1),
    BLOCK_200("Hollow block 8\" (200 mm)", 200, 400, 200, 1),
    STONE_225("Machine-cut stone 9\" (225 mm)", 225, 450, 225, 1),
    BRICK_112("Clay brick, half-brick wall (112 mm)", 112, 225, 75, 1),
    BRICK_225("Clay brick, one-brick wall (225 mm)", 225, 225, 75, 2);

    public final String label;
    public final double thicknessMm;
    public final double unitLengthMm;
    public final double unitHeightMm;
    /** Number of unit layers through the wall thickness (2 for a one-brick wall). */
    public final int layers;

    WallMaterial(String label, double thicknessMm, double unitLengthMm, double unitHeightMm, int layers) {
        this.label = label;
        this.thicknessMm = thicknessMm;
        this.unitLengthMm = unitLengthMm;
        this.unitHeightMm = unitHeightMm;
        this.layers = layers;
    }

    /** Units per m2 of wall face for the given mortar joint thickness. */
    public double unitsPerM2(double jointMm) {
        double l = (unitLengthMm + jointMm) / 1000.0;
        double h = (unitHeightMm + jointMm) / 1000.0;
        return layers / (l * h);
    }

    /** Volume of one unit (m3), including all layers' share of thickness. */
    public double unitVolumeM3() {
        return (unitLengthMm / 1000.0) * (unitHeightMm / 1000.0) * (thicknessMm / layers / 1000.0);
    }
}
