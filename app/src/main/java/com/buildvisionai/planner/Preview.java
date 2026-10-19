package com.buildvisionai.planner;

import com.buildvisionai.models.PlanElement;
import com.buildvisionai.models.Vec2;

/** Transient, non-persistent feedback drawn on top of the plan while a tool is active. */
public final class Preview {
    /** Rubber-band wall while drawing (either may be null). */
    public Vec2 wallStart, wallEnd;
    public double wallThicknessMm = 200;
    /** Wall chain start marker (closing the chain on it closes the loop). */
    public Vec2 chainStart;
    /** Ghost of an element about to be placed (not in the document). */
    public PlanElement ghost;
    /** Latest snap result, drawn as an indicator. */
    public SnapResult snap;

    public void clear() {
        wallStart = wallEnd = chainStart = null;
        ghost = null;
        snap = null;
    }

    public boolean isEmpty() { return wallStart == null && chainStart == null && ghost == null && snap == null; }
}
