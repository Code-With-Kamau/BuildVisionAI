package com.buildvisionai.estimation;

/** The cost buckets shown to the user. Superstructure concrete (columns, beams, slabs, stairs) sits under WALLING. */
public enum CostCategory {
    FOUNDATION("Foundation"),
    WALLING("Walling & superstructure"),
    ROOFING("Roofing"),
    FINISHES("Finishes, doors & windows"),
    PLUMBING("Plumbing & drainage"),
    ELECTRICAL("Electrical"),
    LABOUR("Labour");

    public final String label;

    CostCategory(String label) { this.label = label; }
}
