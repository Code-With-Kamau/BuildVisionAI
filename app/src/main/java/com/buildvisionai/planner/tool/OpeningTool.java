package com.buildvisionai.planner.tool;

import com.buildvisionai.models.Door;
import com.buildvisionai.models.PlanElement;
import com.buildvisionai.models.Vec2;
import com.buildvisionai.models.Wall;
import com.buildvisionai.models.Window;
import com.buildvisionai.planner.EditTransaction;
import com.buildvisionai.planner.ElementFactory;
import com.buildvisionai.planner.OpeningPlacer;
import com.buildvisionai.planner.ToolType;

/** Places doors or windows on walls, snapping to the nearest legal gap on the wall. */
public final class OpeningTool extends Tool {
    private final boolean door;
    private boolean dragging;
    private Wall ghostWall;
    private double ghostOffset;

    public OpeningTool(boolean door) { this.door = door; }

    private double width(EditorContext c) {
        return door ? c.state().doorType.defaultWidthMm : c.state().windowType.defaultWidthMm;
    }

    private Wall wallAt(EditorContext c, double wx, double wy) {
        double reach = Math.max(300, c.view().pxToMm(c.touchPx() * 1.8));
        return OpeningPlacer.nearestWall(c.plan(), wx, wy, reach);
    }

    @Override public boolean onTap(EditorContext c, double wx, double wy) {
        if (!c.canEdit()) return false;
        Wall w = wallAt(c, wx, wy);
        if (w == null) {
            c.message(door ? "Tap a wall to place the door" : "Tap a wall to place the window");
            return true;
        }
        place(c, w, OpeningPlacer.offsetOf(w, wx, wy));
        return true;
    }

    @Override public boolean onDragStart(EditorContext c, double wx, double wy) {
        if (!c.canEdit() || wallAt(c, wx, wy) == null) return false;
        dragging = true;
        onDrag(c, wx, wy);
        return true;
    }

    @Override public void onDrag(EditorContext c, double wx, double wy) {
        if (!dragging) return;
        Wall w = wallAt(c, wx, wy);
        if (w == null) { c.preview().ghost = null; ghostWall = null; c.invalidate(); return; }
        double off = OpeningPlacer.place(c.plan(), w, OpeningPlacer.offsetOf(w, wx, wy), width(c), null);
        if (Double.isNaN(off)) { c.preview().ghost = null; ghostWall = null; c.invalidate(); return; }
        ghostWall = w;
        ghostOffset = off;
        c.preview().ghost = makeGhost(c, w, off);
        c.invalidate();
    }

    @Override public void onDragEnd(EditorContext c) {
        if (!dragging) return;
        dragging = false;
        Wall w = ghostWall;
        double off = ghostOffset;
        ghostWall = null;
        c.preview().ghost = null;
        if (w != null) place(c, w, off);
        else c.message("No room on this wall");
        c.invalidate();
    }

    private void place(EditorContext c, Wall w, double desiredOffset) {
        double off = OpeningPlacer.place(c.plan(), w, desiredOffset, width(c), null);
        if (Double.isNaN(off)) {
            c.message("No room on this wall");
            return;
        }
        PlanElement e = door
                ? ElementFactory.door(c.floorId(), w.id, off, c.state().doorType)
                : ElementFactory.window(c.floorId(), w.id, off, c.state().windowType);
        EditTransaction tx = new EditTransaction(door ? "Add door" : "Add window");
        tx.add(e);
        c.commit(tx, false);
        c.selection().set(e.id);
        c.selectionChanged();
    }

    private PlanElement makeGhost(EditorContext c, Wall w, double off) {
        Vec2 p = w.pointAt(off);
        if (door) {
            Door d = ElementFactory.door(c.floorId(), w.id, off, c.state().doorType);
            d.worldX = p.x; d.worldY = p.y; d.angleDeg = w.rotationDeg(); d.wallThicknessMm = w.thicknessMm; d.attached = true;
            return d;
        }
        Window win = ElementFactory.window(c.floorId(), w.id, off, c.state().windowType);
        win.worldX = p.x; win.worldY = p.y; win.angleDeg = w.rotationDeg(); win.wallThicknessMm = w.thicknessMm; win.attached = true;
        return win;
    }

    @Override public void cancel(EditorContext c) {
        dragging = false;
        ghostWall = null;
        c.preview().clear();
        c.invalidate();
    }

    /** Tool type this instance serves. */
    public ToolType type() { return door ? ToolType.DOOR : ToolType.WINDOW; }
}
