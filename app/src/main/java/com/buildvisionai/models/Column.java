package com.buildvisionai.models;

/** A structural column. For circular columns {@code widthMm} is the diameter. */
public final class Column extends PlacedElement {
    public ColumnShape shape;
    public double heightMm = 2700;

    public Column(String id, String floorId, double x, double y, ColumnShape shape) {
        super(id, floorId, x, y, shape.defaultWidthMm, shape.defaultDepthMm);
        this.shape = shape;
    }

    @Override public boolean isElliptical() { return shape == ColumnShape.CIRCULAR; }

    @Override public ElementType type() { return ElementType.COLUMN; }

    /** Plan area of the section in m2. */
    public double sectionAreaM2() {
        double w = widthMm / 1000.0, d = depthMm / 1000.0;
        return shape == ColumnShape.CIRCULAR ? Math.PI * w * d / 4 : w * d;
    }

    @Override public Column copy() {
        Column c = new Column(id, floorId, x, y, shape);
        c.widthMm = widthMm;
        c.depthMm = depthMm;
        c.heightMm = heightMm;
        copyPlacedInto(c);
        return c;
    }
}
