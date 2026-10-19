package com.buildvisionai.estimation;

/** Wastage fractions added to net quantities. Defaults are common site allowances; all are editable. */
public final class Wastage {
    public double masonryUnits = 0.05;
    public double concrete = 0.05;
    /** Mortar is lost to droppings and uneven bedding, so this one is higher. */
    public double mortar = 0.25;
    public double steel = 0.05;
    public double tiles = 0.10;
    public double paint = 0.05;
    public double roofSheets = 0.05;
    public double boards = 0.10;
    public double timber = 0.10;

    public Wastage copy() {
        Wastage w = new Wastage();
        w.masonryUnits = masonryUnits; w.concrete = concrete; w.mortar = mortar; w.steel = steel;
        w.tiles = tiles; w.paint = paint; w.roofSheets = roofSheets; w.boards = boards; w.timber = timber;
        return w;
    }
}
