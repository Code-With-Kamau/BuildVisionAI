package com.buildvisionai.planner.tool;

import com.buildvisionai.models.Vec2;
import com.buildvisionai.models.Wall;
import com.buildvisionai.planner.EditTransaction;
import com.buildvisionai.planner.ElementFactory;
import com.buildvisionai.planner.Preview;
import com.buildvisionai.planner.RoomSync;
import com.buildvisionai.planner.SnapResult;
import com.buildvisionai.utils.LengthFormatter;

/**
 * Draws walls. Two ways to use it:
 * <ul>
 *   <li>Tap-tap: tap the start, then tap each corner; the chain continues until you close it on
 *       the first point, tap the last point again, or press Done.</li>
 *   <li>Drag: press at the start and drag to the end for a single wall; while a chain is in
 *       progress a drag just positions the next corner.</li>
 * </ul>
 * Every wall is its own undo step, so undo walks the chain back one wall at a time.
 */
public final class WallTool extends Tool {
    public static final double MIN_LENGTH_MM = 100;
    private static final double TOL_MM = 5;

    private Vec2 chainStart;
    private Vec2 last;
    private int chainCount;
    private boolean dragging;
    private boolean singleShot;
    private Vec2 pending;

    @Override public boolean onTap(EditorContext c, double wx, double wy) {
        if (!c.canEdit()) return false;
        SnapResult s = c.snap(new Vec2(wx, wy), last, null);
        Preview pv = c.preview();
        pv.snap = s;
        if (last == null) {
            chainStart = last = s.point;
            chainCount = 0;
            pv.chainStart = chainStart;
            pv.wallStart = last;
            pv.wallEnd = null;
            c.message("Tap the next corner. Tap the last point again to finish.");
            c.invalidate();
            return true;
        }
        placeTo(c, s.point);
        return true;
    }

    @Override public boolean onDragStart(EditorContext c, double wx, double wy) {
        if (!c.canEdit()) return false;
        Preview pv = c.preview();
        if (last == null) {
            SnapResult s = c.snap(new Vec2(wx, wy), null, null);
            chainStart = last = s.point;
            chainCount = 0;
            singleShot = true;
            pv.chainStart = chainStart;
        } else {
            singleShot = false;
        }
        dragging = true;
        pending = null;
        return true;
    }

    @Override public void onDrag(EditorContext c, double wx, double wy) {
        if (!dragging || last == null) return;
        SnapResult s = c.snap(new Vec2(wx, wy), last, null);
        pending = s.point;
        Preview pv = c.preview();
        pv.snap = s;
        pv.wallStart = last;
        pv.wallEnd = s.point;
        pv.wallThicknessMm = c.state().wallKind.defaultThicknessMm();
        c.invalidate();
    }

    @Override public void onDragEnd(EditorContext c) {
        if (!dragging) return;
        dragging = false;
        Vec2 end = pending;
        pending = null;
        if (end != null && end.distanceTo(last) >= MIN_LENGTH_MM) {
            placeTo(c, end);
            if (singleShot) finish(c);
        } else if (singleShot) {
            // a drag that went nowhere behaves like a first tap: keep the start, wait for the next point
            singleShot = false;
            Preview pv = c.preview();
            pv.wallStart = last;
            pv.wallEnd = null;
            c.invalidate();
        }
    }

    private void placeTo(EditorContext c, Vec2 p) {
        Preview pv = c.preview();
        if (p.distanceTo(last) < MIN_LENGTH_MM) { // tapped the last point again: done
            finish(c);
            return;
        }
        for (Wall w : c.plan().walls) {
            boolean same = (w.start().distanceTo(last) < TOL_MM && w.end().distanceTo(p) < TOL_MM)
                    || (w.end().distanceTo(last) < TOL_MM && w.start().distanceTo(p) < TOL_MM);
            if (same) {
                c.message("A wall already exists there");
                return;
            }
        }
        Wall w = ElementFactory.wall(c.floorId(), last.x, last.y, p.x, p.y, c.state().wallKind);
        EditTransaction tx = new EditTransaction("Add wall");
        tx.add(w);
        RoomSync.Result r = c.commit(tx, false);
        boolean closes = chainCount >= 1 && p.distanceTo(chainStart) < TOL_MM;
        if (r.added > 0) {
            c.message("Room created (" + LengthFormatter.format(w.lengthMm(), c.state().units) + " wall closed the loop)");
        }
        chainCount++;
        last = p;
        pv.snap = null;
        if (closes) {
            finish(c);
        } else {
            pv.wallStart = last;
            pv.wallEnd = null;
            c.invalidate();
        }
    }

    public boolean isDrawing() { return last != null; }

    @Override public void cancel(EditorContext c) {
        chainStart = last = pending = null;
        chainCount = 0;
        dragging = singleShot = false;
        c.preview().clear();
        c.invalidate();
    }
}
