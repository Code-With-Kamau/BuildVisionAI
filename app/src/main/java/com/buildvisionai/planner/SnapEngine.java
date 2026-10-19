package com.buildvisionai.planner;

import com.buildvisionai.geometry.GeoUtils;
import com.buildvisionai.models.PlanElement;
import com.buildvisionai.models.Vec2;
import com.buildvisionai.models.Wall;

import java.util.Set;

/**
 * Magnetic snapping for drawing and dragging. Priority: existing wall endpoints, midpoints, points
 * on a wall (T-junction), then direction lock from an anchor, then alignment with other endpoints,
 * then the grid. The radius is in screen pixels so snapping feels the same at every zoom.
 */
public final class SnapEngine {
    private SnapEngine() {}

    public static SnapResult snap(FloorPlan fp, Vec2 raw, Vec2 anchor, Set<String> exclude,
                                  SnapOptions o, ViewTransform vt) {
        if (!o.enabled) return SnapResult.none(raw);
        double r = vt.pxToMm(o.radiusPx);

        // 1. object snaps 
        double bestD = Double.MAX_VALUE;
        int bestPri = Integer.MAX_VALUE;
        Vec2 bestP = null;
        SnapKind bestK = SnapKind.NONE;
        for (PlanElement e : fp.near(raw.x, raw.y, r + 50)) {
            if (!(e instanceof Wall) || (exclude != null && exclude.contains(e.id))) continue;
            Wall w = (Wall) e;
            if (o.endpoints) {
                for (Vec2 p : new Vec2[]{w.start(), w.end()}) {
                    double d = p.distanceTo(raw);
                    if (d <= r && better(0, d, bestPri, bestD)) { bestPri = 0; bestD = d; bestP = p; bestK = SnapKind.ENDPOINT; }
                }
            }
            if (o.midpoints) {
                Vec2 m = new Vec2(w.midX(), w.midY());
                double d = m.distanceTo(raw);
                if (d <= r && better(1, d, bestPri, bestD)) { bestPri = 1; bestD = d; bestP = m; bestK = SnapKind.MIDPOINT; }
            }
            if (o.onWall) {
                double d = GeoUtils.distToSegment(raw.x, raw.y, w.startX, w.startY, w.endX, w.endY);
                if (d <= r && better(2, d, bestPri, bestD)) {
                    bestPri = 2; bestD = d;
                    bestP = GeoUtils.closestOnSegment(raw.x, raw.y, w.startX, w.startY, w.endX, w.endY);
                    bestK = SnapKind.ON_WALL;
                }
            }
        }
        if (bestP != null) return new SnapResult(bestP, bestK, null, null);

        // 2. direction lock from the anchor
        if (anchor != null && (o.angle || o.orthoLock)) {
            Vec2 d = raw.minus(anchor);
            double len = d.length();
            if (len > 1) {
                double ang = Math.toDegrees(Math.atan2(d.y, d.x));
                double snapped = Math.round(ang / o.angleStepDeg) * o.angleStepDeg;
                double diff = Math.abs(GeoUtils.angleDiffDeg(ang, snapped));
                double perp = len * Math.sin(Math.toRadians(diff));
                if (o.orthoLock || perp <= r) {
                    double rad = Math.toRadians(snapped);
                    Vec2 dir = new Vec2(Math.cos(rad), Math.sin(rad));
                    double useLen = len;
                    if (o.grid) useLen = Math.max(o.gridStepMm, GeoUtils.snap(len, o.gridStepMm));
                    Vec2 p = anchor.plus(dir.times(useLen));
                    return new SnapResult(p, SnapKind.ANGLE, anchor, anchor.plus(dir.times(Math.max(useLen, len) + r * 4)));
                }
            }
        }

        // 3. alignment with other endpoints
        if (o.align) {
            double bx = Double.MAX_VALUE, by = Double.MAX_VALUE;
            Vec2 sx = null, sy = null;
            for (Wall w : fp.walls) {
                if (exclude != null && exclude.contains(w.id)) continue;
                for (Vec2 p : new Vec2[]{w.start(), w.end()}) {
                    double dx = Math.abs(p.x - raw.x), dy = Math.abs(p.y - raw.y);
                    if (dx <= r && dx < bx) { bx = dx; sx = p; }
                    if (dy <= r && dy < by) { by = dy; sy = p; }
                }
            }
            if (sx != null || sy != null) {
                double px = sx != null ? sx.x : raw.x, py = sy != null ? sy.y : raw.y;
                Vec2 p = new Vec2(px, py);
                SnapKind k = sx != null && sy != null ? SnapKind.ALIGN_XY : sx != null ? SnapKind.ALIGN_X : SnapKind.ALIGN_Y;
                Vec2 ga = sx != null ? sx : sy;
                return new SnapResult(p, k, ga, p);
            }
        }

        // 4. grid 
        if (o.grid) {
            return new SnapResult(new Vec2(GeoUtils.snap(raw.x, o.gridStepMm), GeoUtils.snap(raw.y, o.gridStepMm)),
                    SnapKind.GRID, null, null);
        }
        return SnapResult.none(raw);
    }

    private static boolean better(int pri, double d, int bestPri, double bestD) {
        return pri < bestPri || (pri == bestPri && d < bestD);
    }
}
