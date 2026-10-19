package com.buildvisionai.planner;

import com.buildvisionai.geometry.GeoUtils;
import com.buildvisionai.models.Door;
import com.buildvisionai.models.Wall;
import com.buildvisionai.models.Window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Finds a legal position for a door or window on a wall: inside the wall and clear of other openings. */
public final class OpeningPlacer {
    private OpeningPlacer() {}

    /** Clearance kept from wall ends (for the abutting wall) and between openings. */
    public static final double END_MARGIN_MM = 150;
    public static final double GAP_MM = 100;

    /** Offset along the wall from its start (mm) of the legal position closest to desired, or NaN if none fits. */
    public static double place(FloorPlan fp, Wall wall, double desiredOffsetMm, double widthMm, String ignoreOpeningId) {
        double len = wall.lengthMm();
        List<double[]> busy = new ArrayList<>();
        for (Door d : fp.doorsOn(wall.id)) {
            if (!d.id.equals(ignoreOpeningId)) busy.add(new double[]{d.offsetMm - d.widthMm / 2 - GAP_MM, d.offsetMm + d.widthMm / 2 + GAP_MM});
        }
        for (Window w : fp.windowsOn(wall.id)) {
            if (!w.id.equals(ignoreOpeningId)) busy.add(new double[]{w.offsetMm - w.widthMm / 2 - GAP_MM, w.offsetMm + w.widthMm / 2 + GAP_MM});
        }
        Collections.sort(busy, Comparator.comparingDouble(a -> a[0]));

        double best = Double.NaN, bestDist = Double.MAX_VALUE;
        double cursor = END_MARGIN_MM;
        List<double[]> free = new ArrayList<>();
        for (double[] b : busy) {
            if (b[0] > cursor) free.add(new double[]{cursor, Math.min(b[0], len - END_MARGIN_MM)});
            cursor = Math.max(cursor, b[1]);
        }
        if (cursor < len - END_MARGIN_MM) free.add(new double[]{cursor, len - END_MARGIN_MM});

        for (double[] f : free) {
            if (f[1] - f[0] + 1e-6 < widthMm) continue;
            double c = Math.max(f[0] + widthMm / 2, Math.min(f[1] - widthMm / 2, desiredOffsetMm));
            double d = Math.abs(c - desiredOffsetMm);
            if (d < bestDist) { bestDist = d; best = c; }
        }
        return best;
    }

    /** Nearest wall within maxDistMm of the point, or null. */
    public static Wall nearestWall(FloorPlan fp, double x, double y, double maxDistMm) {
        Wall best = null;
        double bestD = maxDistMm;
        for (Wall w : fp.walls) {
            double d = GeoUtils.distToSegment(x, y, w.startX, w.startY, w.endX, w.endY);
            if (d <= bestD) { bestD = d; best = w; }
        }
        return best;
    }

    /** Offset along the wall (mm) of the projection of (x, y). */
    public static double offsetOf(Wall w, double x, double y) {
        return GeoUtils.projectT(x, y, w.startX, w.startY, w.endX, w.endY) * w.lengthMm();
    }
}
