package com.buildvisionai.geometry;

import com.buildvisionai.models.Vec2;

import java.util.Collections;
import java.util.List;

/** Outer outline of one connected group of walls, including wall thickness. */
public final class Footprint {
    /** Positively oriented outer outline (mm). */
    public final List<Vec2> outline;
    public final double areaM2;
    public final double perimeterM;
    /** False when the outward offset was degenerate and the values are approximated. */
    public final boolean exact;

    Footprint(List<Vec2> outline, double areaM2, double perimeterM, boolean exact) {
        this.outline = Collections.unmodifiableList(outline);
        this.areaM2 = areaM2;
        this.perimeterM = perimeterM;
        this.exact = exact;
    }
}
