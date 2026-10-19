package com.buildvisionai.planner.tool;

import com.buildvisionai.models.PlacedElement;
import com.buildvisionai.models.Vec2;
import com.buildvisionai.planner.EditTransaction;
import com.buildvisionai.planner.ElementFactory;
import com.buildvisionai.planner.ToolType;

/** Places free-standing items (columns, stairs, furniture) at the snapped tap or drag position. */
public final class PlaceTool extends Tool {
    private final ToolType kind;
    private boolean dragging;
    private Vec2 at;

    public PlaceTool(ToolType kind) {
        if (kind != ToolType.COLUMN && kind != ToolType.STAIR && kind != ToolType.FURNITURE) {
            throw new IllegalArgumentException("not a placeable tool: " + kind);
        }
        this.kind = kind;
    }

    private PlacedElement make(EditorContext c, Vec2 p) {
        switch (kind) {
            case COLUMN: return ElementFactory.column(c.floorId(), p.x, p.y, c.state().columnShape);
            case STAIR: return ElementFactory.stair(c.floorId(), p.x, p.y, c.state().stairType);
            default: return ElementFactory.furniture(c.floorId(), p.x, p.y, c.state().furnitureType);
        }
    }

    @Override public boolean onTap(EditorContext c, double wx, double wy) {
        if (!c.canEdit()) return false;
        place(c, c.snap(new Vec2(wx, wy), null, null).point);
        return true;
    }

    @Override public boolean onDragStart(EditorContext c, double wx, double wy) {
        if (!c.canEdit()) return false;
        dragging = true;
        onDrag(c, wx, wy);
        return true;
    }

    @Override public void onDrag(EditorContext c, double wx, double wy) {
        if (!dragging) return;
        at = c.snap(new Vec2(wx, wy), null, null).point;
        c.preview().ghost = make(c, at);
        c.invalidate();
    }

    @Override public void onDragEnd(EditorContext c) {
        if (!dragging) return;
        dragging = false;
        c.preview().ghost = null;
        if (at != null) place(c, at);
        at = null;
        c.invalidate();
    }

    private void place(EditorContext c, Vec2 p) {
        PlacedElement e = make(c, p);
        EditTransaction tx = new EditTransaction("Add " + kind.label.toLowerCase());
        tx.add(e);
        c.commit(tx, false);
        c.selection().set(e.id);
        c.selectionChanged();
    }

    @Override public void cancel(EditorContext c) {
        dragging = false;
        at = null;
        c.preview().clear();
        c.invalidate();
    }
}
