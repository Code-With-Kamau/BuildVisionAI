package com.buildvisionai.models;

/** A staircase occupying a rotated rectangle; the drawn shape depends on {@link StairType}. */
public final class Stair extends PlacedElement {
    public StairType stairType;
    /** Floor-to-floor height the stair must climb. */
    public double riseMm = 3000;
    public double riserMm = 175;

    public Stair(String id, String floorId, double x, double y, StairType stairType) {
        super(id, floorId, x, y, stairType.defaultWidthMm, stairType.defaultDepthMm);
        this.stairType = stairType;
    }

    @Override public ElementType type() { return ElementType.STAIR; }

    public int stepCount() { return (int) Math.max(1, Math.round(riseMm / riserMm)); }

    @Override public Stair copy() {
        Stair s = new Stair(id, floorId, x, y, stairType);
        s.widthMm = widthMm;
        s.depthMm = depthMm;
        s.riseMm = riseMm;
        s.riserMm = riserMm;
        copyPlacedInto(s);
        return s;
    }
}
