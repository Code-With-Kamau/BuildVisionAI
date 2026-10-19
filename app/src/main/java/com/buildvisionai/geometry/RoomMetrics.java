package com.buildvisionai.geometry;

/** Finishing quantities of one detected room. */
public final class RoomMetrics {
    public final DetectedRoom shape;
    /** Length-weighted average height of the bounding walls, metres. */
    public double wallHeightM;
    /** Net perimeter x height, before deducting openings. */
    public double grossWallSurfaceM2;
    public double openingAreaM2;
    public double netWallSurfaceM2;
    public int doorCount;
    public int windowCount;
    public double doorWidthTotalM;

    RoomMetrics(DetectedRoom shape) { this.shape = shape; }
}
