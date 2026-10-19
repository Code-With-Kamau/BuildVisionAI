package com.buildvisionai.planner.render;

import com.buildvisionai.models.Vec2;
import com.buildvisionai.models.Wall;

import java.util.List;

/**
 * Which ends of which walls meet another wall's end (a corner or an in-line joint). Those ends are
 * drawn with a square cap so two perpendicular walls close the corner exactly, while a free end or a
 * T-junction end is left flat. Walls that merely cross, or end on the side of another wall, need no
 * cap: the other wall's body already covers the joint.
 */
public final class WallJoins {
    public static final double TOL_MM = 5.0;

    public final boolean[] capStart;
    public final boolean[] capEnd;

    private WallJoins(int n) {
        capStart = new boolean[n];
        capEnd = new boolean[n];
    }

    /** O(n^2) over the walls of one floor; a house has dozens, and the result is cached per revision. */
    public static WallJoins compute(List<Wall> walls) {
        int n = walls.size();
        WallJoins j = new WallJoins(n);
        for (int a = 0; a < n; a++) {
            Wall wa = walls.get(a);
            for (int b = 0; b < n; b++) {
                if (a == b) continue;
                Wall wb = walls.get(b);
                if (!wa.bounds().intersects(wb.bounds())) continue;
                Vec2 s = wa.start(), e = wa.end();
                if (s.distanceTo(wb.start()) <= TOL_MM || s.distanceTo(wb.end()) <= TOL_MM) j.capStart[a] = true;
                if (e.distanceTo(wb.start()) <= TOL_MM || e.distanceTo(wb.end()) <= TOL_MM) j.capEnd[a] = true;
            }
        }
        return j;
    }
}
