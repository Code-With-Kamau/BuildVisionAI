package com.buildvisionai.planner.tool;

/**
 * One editing tool. Coordinates are plan space (mm). Input methods return true when the tool
 * consumed the gesture; an unconsumed drag falls through to panning the view.
 */
public abstract class Tool {
    public boolean onTap(EditorContext c, double wx, double wy) { return false; }

    public boolean onLongPress(EditorContext c, double wx, double wy) { return false; }

    /** Finger went down and started moving at (wx, wy). Return true to receive onDrag/onDragEnd. */
    public boolean onDragStart(EditorContext c, double wx, double wy) { return false; }

    public void onDrag(EditorContext c, double wx, double wy) { }

    public void onDragEnd(EditorContext c) { }

    /** Abort any gesture/chain in progress without committing. */
    public void cancel(EditorContext c) { }

    /** Finish a multi-step operation (e.g. a wall chain) keeping what was committed. */
    public void finish(EditorContext c) { cancel(c); }
}
