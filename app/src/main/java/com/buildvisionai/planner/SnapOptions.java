package com.buildvisionai.planner;

/** User-controllable snapping behaviour. */
public final class SnapOptions {
    public boolean enabled = true;
    public boolean grid = true;
    public boolean endpoints = true;
    public boolean midpoints = true;
    public boolean onWall = true;
    public boolean align = true;
    public boolean angle = true;
    /** Always lock drawing directions to {@link #angleStepDeg} (like holding Shift in CAD). */
    public boolean orthoLock = false;
    public double angleStepDeg = 15;
    /** Capture radius in screen pixels (converted to mm with the current zoom). */
    public double radiusPx = 16;
    /** Grid step in mm; the controller keeps it in step with the zoom via ScaleModel. */
    public double gridStepMm = 100;
}
