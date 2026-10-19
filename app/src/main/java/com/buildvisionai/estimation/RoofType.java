package com.buildvisionai.estimation;

/** Roof shape + covering. Pitched iron-sheet roofs are the common Kenyan default. */
public enum RoofType {
    GABLED_IRON_SHEET("Gabled - iron sheets (G28)", Cover.IRON_SHEET, false),
    HIPPED_IRON_SHEET("Hipped - iron sheets (G28)", Cover.IRON_SHEET, true),
    GABLED_TILE("Gabled - concrete tiles", Cover.TILE, false),
    HIPPED_TILE("Hipped - concrete tiles", Cover.TILE, true),
    FLAT_SLAB("Flat concrete slab", Cover.SLAB, false);

    public enum Cover { IRON_SHEET, TILE, SLAB }

    public final String label;
    public final Cover cover;
    public final boolean hipped;

    RoofType(String label, Cover cover, boolean hipped) {
        this.label = label;
        this.cover = cover;
        this.hipped = hipped;
    }

    public boolean pitched() { return cover != Cover.SLAB; }
}
