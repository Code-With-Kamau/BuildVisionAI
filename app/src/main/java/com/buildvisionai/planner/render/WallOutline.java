package com.buildvisionai.planner.render;

import com.buildvisionai.models.Vec2;
import com.buildvisionai.models.Wall;

/**
 * The four corners of a wall as a rectangle (centreline, thickness, optional square caps). Every
 * wall is wound the same way, so all walls can go into one fill path with the non-zero rule and
 * overlaps merge instead of cancelling.
 */
public final class WallOutline {
    private WallOutline() {}

    /**
     * @return {x0, y0, x1, y1, x2, y2, x3, y3} in plan space (mm)
     */
    public static double[] corners(Wall w, boolean capStart, boolean capEnd) {
        Vec2 d = w.direction();
        Vec2 n = d.perp();
        double h = w.thicknessMm / 2;
        Vec2 a = w.start().minus(d.times(capStart ? h : 0));
        Vec2 b = w.end().plus(d.times(capEnd ? h : 0));
        Vec2 a1 = a.plus(n.times(h)), b1 = b.plus(n.times(h)), b2 = b.minus(n.times(h)), a2 = a.minus(n.times(h));
        return new double[]{a1.x, a1.y, b1.x, b1.y, b2.x, b2.y, a2.x, a2.y};
    }

    /** Shoelace area of {@link #corners}: tests use it to check the geometry and the winding sign. */
    public static double signedArea(double[] c) {
        double s = 0;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            s += c[2 * i] * c[2 * j + 1] - c[2 * j] * c[2 * i + 1];
        }
        return s / 2;
    }
}
