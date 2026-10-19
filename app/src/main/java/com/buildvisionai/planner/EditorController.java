package com.buildvisionai.planner;

import com.buildvisionai.models.Door;
import com.buildvisionai.models.Floor;
import com.buildvisionai.models.PlacedElement;
import com.buildvisionai.models.PlanElement;
import com.buildvisionai.models.Room;
import com.buildvisionai.models.Vec2;
import com.buildvisionai.models.Wall;
import com.buildvisionai.models.Window;
import com.buildvisionai.planner.tool.EditorContext;
import com.buildvisionai.planner.tool.OpeningTool;
import com.buildvisionai.planner.tool.PlaceTool;
import com.buildvisionai.planner.tool.RoomTool;
import com.buildvisionai.planner.tool.SelectTool;
import com.buildvisionai.planner.tool.Tool;
import com.buildvisionai.planner.tool.WallTool;
import com.buildvisionai.utils.Ids;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Pure-Java brain of the editor. The Android {@code FloorPlanView} only forwards raw touch events
 * (in screen pixels) to this class and draws what it exposes; everything else - tools, snapping,
 * hit testing, undo/redo, room detection, locked-version protection - lives here.
 */
public final class EditorController implements EditorContext {
    private final PlanDocument doc;
    private final CommandStack stack = new CommandStack();
    private final Selection selection = new Selection();
    private final ViewTransform view = new ViewTransform();
    private final EditorState state = new EditorState();
    private final Preview preview = new Preview();
    private final Map<ToolType, Tool> tools = new EnumMap<>(ToolType.class);
    private EditorListener listener = new NoOpListener();

    private String floorId;
    private boolean readOnly;
    private double density = 1.0;
    private Tool dragTool;

    public EditorController(PlanDocument doc, String initialFloorId) {
        this.doc = doc;
        this.floorId = initialFloorId;
        tools.put(ToolType.SELECT, new SelectTool());
        tools.put(ToolType.WALL, new WallTool());
        tools.put(ToolType.ROOM, new RoomTool());
        tools.put(ToolType.DOOR, new OpeningTool(true));
        tools.put(ToolType.WINDOW, new OpeningTool(false));
        tools.put(ToolType.COLUMN, new PlaceTool(ToolType.COLUMN));
        tools.put(ToolType.STAIR, new PlaceTool(ToolType.STAIR));
        tools.put(ToolType.FURNITURE, new PlaceTool(ToolType.FURNITURE));
    }

    public void setListener(EditorListener l) { this.listener = l == null ? new NoOpListener() : l; }

    /** Screen density, so touch and snap tolerances stay finger-sized on every phone. */
    public void setDensity(double d) {
        density = Math.max(0.5, d);
        state.snap.radiusPx = 14 * density;
    }

    // EditorContext 

    @Override public PlanDocument doc() { return doc; }
    @Override public String floorId() { return floorId; }
    @Override public FloorPlan plan() { return doc.floorPlan(floorId); }
    @Override public Selection selection() { return selection; }
    @Override public ViewTransform view() { return view; }
    @Override public EditorState state() { return state; }
    @Override public Preview preview() { return preview; }
    @Override public boolean canEdit() { return !readOnly; }
    @Override public double touchPx() { return 14 * density; }

    /** Minor grid step shared by drawing and snapping, in mm. */
    public double gridStepMm() {
        double s = ScaleModel.gridStepMm(view.scale, 9 * density);
        return Math.max(50, Math.min(1000, s));
    }

    @Override public SnapResult snap(Vec2 raw, Vec2 anchor, Set<String> exclude) {
        state.snap.gridStepMm = gridStepMm();
        SnapResult r = SnapEngine.snap(plan(), raw, anchor, exclude, state.snap, view);
        preview.snap = r;
        return r;
    }

    @Override public RoomSync.Result commit(EditTransaction tx, boolean alreadyApplied) {
        RoomSync.Result res = new RoomSync.Result();
        if (tx.isEmpty()) return res;
        if (!canEdit()) {
            message(lockedMessage());
            return res;
        }
        if (!alreadyApplied) tx.apply(doc);
        res = RoomSync.sync(doc, floorId, tx);
        stack.record(tx);
        pruneSelection();
        listener.onDocumentChanged();
        listener.onInvalidate();
        return res;
    }

    @Override public void message(String text) { listener.onMessage(text); }
    @Override public void invalidate() { listener.onInvalidate(); }
    @Override public void selectionChanged() { listener.onSelectionChanged(); listener.onInvalidate(); }
    @Override public void requestRoomEdit(String roomId) { listener.onRoomEditRequested(roomId); }

    @Override public void setTool(ToolType t) {
        if (readOnly && t != ToolType.SELECT) {
            message(lockedMessage());
            return;
        }
        if (t == state.tool) return;
        tool().cancel(this);
        state.tool = t;
        preview.clear();
        listener.onToolChanged();
        listener.onInvalidate();
    }

    private static String lockedMessage() { return "This version is locked. Create a new version to make changes."; }

    private Tool tool() { return tools.get(state.tool); }

    //  raw input (screen pixels)

    public boolean onTap(float sx, float sy) {
        return tool().onTap(this, view.toWorldX(sx), view.toWorldY(sy));
    }

    public boolean onLongPress(float sx, float sy) {
        return tool().onLongPress(this, view.toWorldX(sx), view.toWorldY(sy));
    }

    /** @return true if a tool took the drag; false means the view should pan. */
    public boolean onDragStart(float sx, float sy) {
        Tool t = tool();
        if (t.onDragStart(this, view.toWorldX(sx), view.toWorldY(sy))) {
            dragTool = t;
            return true;
        }
        dragTool = null;
        return false;
    }

    public void onDrag(float sx, float sy) {
        if (dragTool != null) dragTool.onDrag(this, view.toWorldX(sx), view.toWorldY(sy));
    }

    public void onDragEnd() {
        if (dragTool != null) {
            Tool t = dragTool;
            dragTool = null;
            t.onDragEnd(this);
        }
    }

    /** Finger lifted without completing, or a second finger arrived: abandon the gesture. */
    public void onDragCancel() {
        if (dragTool != null) {
            Tool t = dragTool;
            dragTool = null;
            t.cancel(this);
        }
    }

    //  commands 
    public boolean canUndo() { return canEdit() && stack.canUndo(); }
    public boolean canRedo() { return canEdit() && stack.canRedo(); }
    public String undoLabel() { return stack.undoLabel(); }
    public String redoLabel() { return stack.redoLabel(); }

    public boolean undo() {
        if (!canEdit()) { message(lockedMessage()); return false; }
        tool().cancel(this);
        EditTransaction tx = stack.undo(doc);
        if (tx == null) return false;
        afterHistoryStep();
        return true;
    }

    public boolean redo() {
        if (!canEdit()) { message(lockedMessage()); return false; }
        tool().cancel(this);
        EditTransaction tx = stack.redo(doc);
        if (tx == null) return false;
        afterHistoryStep();
        return true;
    }

    private void afterHistoryStep() {
        pruneSelection();
        listener.onDocumentChanged();
        listener.onSelectionChanged();
        listener.onInvalidate();
    }

    /** Ends a multi-step operation (wall chain) keeping what was drawn. */
    public void finishDrawing() { tool().finish(this); }

    public void deleteSelection() {
        if (!requireEdit() || selection.isEmpty()) return;
        FloorPlan fp = plan();
        Set<String> wallIds = new HashSet<>();
        Set<String> others = new HashSet<>();
        for (String id : selection.ids()) {
            PlanElement e = doc.get(id);
            if (e instanceof Wall) wallIds.add(id);
            else if (e instanceof Room) {
                Room r = (Room) e;
                for (String wid : r.wallIds) {
                    boolean shared = false;
                    for (Room other : fp.rooms) if (other != r && other.wallIds.contains(wid)) { shared = true; break; }
                    if (!shared) wallIds.add(wid);
                }
            } else if (e != null) others.add(id);
        }
        EditTransaction tx = new EditTransaction("Delete");
        WallOps.removeWalls(doc, fp, wallIds, tx);
        for (String id : others) {
            boolean alreadyRemoved = false;
            for (EditTransaction.Change c : tx.changes()) if (c.elementId().equals(id)) alreadyRemoved = true;
            if (!alreadyRemoved && doc.get(id) != null) tx.remove(doc.get(id));
        }
        selection.clear();
        commit(tx, false);
        listener.onSelectionChanged();
    }

    /**
     * Copies the selection. A selection containing walls is placed beside the original (so a copied
     * room does not overlap and split the first one); loose items are nudged 500 mm. Walls bring
     * their doors and windows.
     */
    public void duplicateSelection() {
        if (!requireEdit() || selection.isEmpty()) return;
        FloorPlan fp = plan();
        com.buildvisionai.models.RectD bounds = null;
        boolean hasWalls = false;
        for (String id : selection.ids()) {
            PlanElement e = doc.get(id);
            if (e instanceof Wall || e instanceof PlacedElement) {
                bounds = bounds == null ? e.bounds() : bounds.union(e.bounds());
                hasWalls |= e instanceof Wall;
            }
        }
        if (bounds == null) return;
        final double offX = hasWalls ? bounds.width() + 600 : 500;
        final double offY = hasWalls ? 0 : 500;
        EditTransaction tx = new EditTransaction("Duplicate");
        List<String> created = new ArrayList<>();
        Map<String, String> wallMap = new HashMap<>();
        for (String id : selection.ids()) {
            PlanElement e = doc.get(id);
            if (e instanceof Wall) {
                Wall w = ((Wall) e).copy();
                w.id = Ids.newId();
                w.startX += offX; w.startY += offY; w.endX += offX; w.endY += offY;
                wallMap.put(id, w.id);
                tx.add(w);
                created.add(w.id);
            } else if (e instanceof PlacedElement) {
                PlacedElement p = (PlacedElement) e.copy();
                p.id = Ids.newId();
                p.x += offX; p.y += offY;
                tx.add(p);
                created.add(p.id);
            }
        }
        for (Map.Entry<String, String> m : wallMap.entrySet()) {
            for (Door d : fp.doorsOn(m.getKey())) {
                Door nd = d.copy();
                nd.id = Ids.newId();
                nd.wallId = m.getValue();
                tx.add(nd);
            }
            for (Window w : fp.windowsOn(m.getKey())) {
                Window nw = w.copy();
                nw.id = Ids.newId();
                nw.wallId = m.getValue();
                tx.add(nw);
            }
        }
        if (tx.isEmpty()) return;
        commit(tx, false);
        selection.clear();
        for (String id : created) selection.add(id);
        listener.onSelectionChanged();
        listener.onInvalidate();
    }

    /** Rotates the selection about the centre of its bounds. Openings follow their walls. */
    public void rotateSelection(double degrees) {
        if (!requireEdit() || selection.isEmpty()) return;
        com.buildvisionai.models.RectD b = null;
        List<PlanElement> targets = new ArrayList<>();
        for (String id : selection.ids()) {
            PlanElement e = doc.get(id);
            if (e instanceof Wall || e instanceof PlacedElement) {
                targets.add(e);
                b = b == null ? e.bounds() : b.union(e.bounds());
            }
        }
        if (targets.isEmpty()) return;
        double cx = b.centerX(), cy = b.centerY();
        EditTransaction tx = new EditTransaction("Rotate");
        double rad = Math.toRadians(degrees);
        for (PlanElement e : targets) {
            PlanElement before = e.copy();
            if (e instanceof Wall) {
                Wall w = (Wall) e.copy();
                Vec2 s = new Vec2(w.startX - cx, w.startY - cy).rotate(rad);
                Vec2 en = new Vec2(w.endX - cx, w.endY - cy).rotate(rad);
                w.startX = cx + s.x; w.startY = cy + s.y; w.endX = cx + en.x; w.endY = cy + en.y;
                tx.modify(before, w);
            } else {
                PlacedElement p = (PlacedElement) e.copy();
                Vec2 c = new Vec2(p.x - cx, p.y - cy).rotate(rad);
                p.x = cx + c.x; p.y = cy + c.y;
                p.rotationDeg = com.buildvisionai.geometry.GeoUtils.normDeg(p.rotationDeg + degrees);
                tx.modify(before, p);
            }
        }
        commit(tx, false);
    }

    /**
     * Edits one element's properties as one undo step (thickness, wall kind, door width, room
     * name/type ...). The mutator receives a private copy.
     */
    public void updateElement(String id, String label, Consumer<PlanElement> mutator) {
        if (!requireEdit()) return;
        PlanElement live = doc.get(id);
        if (live == null) return;
        PlanElement after = live.copy();
        mutator.accept(after);
        EditTransaction tx = new EditTransaction(label);
        tx.modify(live, after);
        commit(tx, false);
    }

    /** One property change for {@link #updateElements}. */
    public static final class ElementEdit {
        public final String id;
        public final Consumer<PlanElement> mutator;

        public ElementEdit(String id, Consumer<PlanElement> mutator) {
            this.id = id;
            this.mutator = mutator;
        }
    }

    /**
     * Applies several property edits as ONE undo step (used when an accepted suggestion changes many
     * elements). Several edits to the same element are applied in order to one working copy, so none
     * overwrites another.
     *
     * @return number of distinct elements changed
     */
    public int updateElements(String label, List<ElementEdit> edits) {
        if (!requireEdit()) return 0;
        Map<String, PlanElement> afters = new java.util.LinkedHashMap<>();
        for (ElementEdit e : edits) {
            PlanElement live = doc.get(e.id);
            if (live == null) continue;
            PlanElement after = afters.get(e.id);
            if (after == null) {
                after = live.copy();
                afters.put(e.id, after);
            }
            e.mutator.accept(after);
        }
        EditTransaction tx = new EditTransaction(label);
        for (Map.Entry<String, PlanElement> en : afters.entrySet()) tx.modify(doc.get(en.getKey()), en.getValue());
        commit(tx, false);
        return afters.size();
    }

    // floors

    public String activeFloorId() { return floorId; }

    public void setActiveFloor(String id) {
        if (doc.floor(id) == null || id.equals(floorId)) return;
        tool().cancel(this);
        floorId = id;
        selection.clear();
        preview.clear();
        listener.onSelectionChanged();
        listener.onDocumentChanged();
        listener.onInvalidate();
    }

    /** Adds the next level above the highest existing one and switches to it. */
    public Floor addFloor() {
        if (!requireEdit()) return null;
        int level = 0;
        for (Floor f : doc.floors()) level = Math.max(level, f.level + 1);
        Floor nf = new Floor(Ids.newId(), level, Floor.defaultName(level));
        EditTransaction tx = new EditTransaction("Add floor");
        tx.addFloor(nf);
        commit(tx, false);
        setActiveFloor(nf.id);
        return nf;
    }

    // state 

    /** Locked or approved versions open read-only; selection still works so people can inspect. */
    public void setReadOnly(boolean ro) {
        readOnly = ro;
        if (ro) {
            tool().cancel(this);
            state.tool = ToolType.SELECT;
            listener.onToolChanged();
        }
        listener.onInvalidate();
    }

    public boolean isReadOnly() { return readOnly; }

    public void fitToContent() {
        view.fit(plan().contentBounds(), 48 * density);
        listener.onInvalidate();
    }

    private boolean requireEdit() {
        if (!canEdit()) { message(lockedMessage()); return false; }
        return true;
    }

    private void pruneSelection() {
        List<String> gone = new ArrayList<>();
        for (String id : selection.ids()) if (doc.get(id) == null) gone.add(id);
        for (String id : gone) selection.remove(id);
        if (!gone.isEmpty()) listener.onSelectionChanged();
    }

    private static final class NoOpListener implements EditorListener {
        @Override public void onInvalidate() { }
        @Override public void onDocumentChanged() { }
        @Override public void onSelectionChanged() { }
        @Override public void onToolChanged() { }
        @Override public void onMessage(String text) { }
        @Override public void onRoomEditRequested(String roomId) { }
    }
}
