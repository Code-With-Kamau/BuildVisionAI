package com.buildvisionai.planner;

import com.buildvisionai.geometry.DetectedRoom;
import com.buildvisionai.geometry.GeoUtils;
import com.buildvisionai.models.Column;
import com.buildvisionai.models.Door;
import com.buildvisionai.models.Furniture;
import com.buildvisionai.models.PlacedElement;
import com.buildvisionai.models.PlanElement;
import com.buildvisionai.models.Room;
import com.buildvisionai.models.Stair;
import com.buildvisionai.models.Vec2;
import com.buildvisionai.models.Wall;
import com.buildvisionai.models.Window;

/**
 * Finds what is under a finger. Handles of the single selected element win; then free-standing
 * items (smallest first, so a column inside a stair is reachable), openings, walls, and finally the
 * room fill. Tolerances are in screen pixels so fingers can hit thin walls at any zoom.
 */
public final class HitTester {
    private HitTester() {}

    public static final double ROTATE_OFFSET_PX = 34;

    public static HitResult hit(FloorPlan fp, Selection sel, double wx, double wy, ViewTransform vt, double touchPx) {
        double tol = vt.pxToMm(touchPx);

        // 1. handles of the primary selected element
        if (sel != null && sel.isSingle()) {
            PlanElement e = fp.wallsById.get(sel.primaryId());
            if (e == null) e = findPlaced(fp, sel.primaryId());
            if (e != null) {
                double best = vt.pxToMm(touchPx * 1.4);
                Handle bestH = Handle.NONE;
                for (Handle h : HandleGeometry.handlesOf(e)) {
                    Vec2 p = HandleGeometry.position(e, h, vt.pxToMm(ROTATE_OFFSET_PX));
                    if (p == null) continue;
                    double d = p.distanceTo(new Vec2(wx, wy));
                    if (d <= best) { best = d; bestH = h; }
                }
                if (bestH != Handle.NONE) return new HitResult(e, bestH);
            }
        }

        // 2. free-standing items: smallest area wins
        PlacedElement bestPlaced = null;
        double bestArea = Double.MAX_VALUE;
        for (PlanElement e : fp.near(wx, wy, tol + 100)) {
            if (e instanceof Column || e instanceof Furniture || e instanceof Stair) {
                PlacedElement p = (PlacedElement) e;
                if (p.containsPoint(wx, wy, tol * 0.4)) {
                    double area = p.widthMm * p.depthMm;
                    if (area < bestArea) { bestArea = area; bestPlaced = p; }
                }
            }
        }
        if (bestPlaced != null) return new HitResult(bestPlaced, Handle.NONE);

        // 3. openings
        PlanElement bestOpening = null;
        double bestD = Double.MAX_VALUE;
        for (PlanElement e : fp.near(wx, wy, tol + 600)) {
            double d = Double.MAX_VALUE;
            double reach = tol;
            if (e instanceof Door) {
                Door o = (Door) e;
                if (!o.attached) continue;
                d = distToOpening(o.worldX, o.worldY, o.angleDeg, o.widthMm, wx, wy);
                reach = Math.max(o.wallThicknessMm / 2, tol);
            } else if (e instanceof Window) {
                Window o = (Window) e;
                if (!o.attached) continue;
                d = distToOpening(o.worldX, o.worldY, o.angleDeg, o.widthMm, wx, wy);
                reach = Math.max(o.wallThicknessMm / 2, tol);
            }
            if (d <= reach && d < bestD) { bestD = d; bestOpening = e; }
        }
        if (bestOpening != null) return new HitResult(bestOpening, Handle.NONE);

        // 4. walls
        Wall bestWall = null;
        bestD = Double.MAX_VALUE;
        for (PlanElement e : fp.near(wx, wy, tol + 400)) {
            if (!(e instanceof Wall)) continue;
            Wall w = (Wall) e;
            double d = GeoUtils.distToSegment(wx, wy, w.startX, w.startY, w.endX, w.endY);
            if (d <= Math.max(w.thicknessMm / 2, tol) && d < bestD) { bestD = d; bestWall = w; }
        }
        if (bestWall != null) return new HitResult(bestWall, Handle.NONE);

        // 5. room fill
        DetectedRoom shape = fp.topology().roomContaining(wx, wy);
        if (shape != null) {
            for (Room r : fp.rooms) {
                if (fp.shapeOf(r) == shape) return new HitResult(r, Handle.NONE);
            }
        }
        return null;
    }

    private static PlanElement findPlaced(FloorPlan fp, String id) {
        for (PlanElement e : fp.columns) if (e.id.equals(id)) return e;
        for (PlanElement e : fp.stairs) if (e.id.equals(id)) return e;
        for (PlanElement e : fp.furniture) if (e.id.equals(id)) return e;
        return null;
    }

    static double distToOpening(double cx, double cy, double angleDeg, double width, double px, double py) {
        double a = Math.toRadians(angleDeg);
        double hx = Math.cos(a) * width / 2, hy = Math.sin(a) * width / 2;
        return GeoUtils.distToSegment(px, py, cx - hx, cy - hy, cx + hx, cy + hy);
    }
}
