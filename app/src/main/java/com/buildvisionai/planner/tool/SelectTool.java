package com.buildvisionai.planner.tool;

import com.buildvisionai.geometry.GeoUtils;
import com.buildvisionai.models.Door;
import com.buildvisionai.models.PlacedElement;
import com.buildvisionai.models.PlanElement;
import com.buildvisionai.models.Room;
import com.buildvisionai.models.Vec2;
import com.buildvisionai.models.Wall;
import com.buildvisionai.models.Window;
import com.buildvisionai.planner.EditTransaction;
import com.buildvisionai.planner.FloorPlan;
import com.buildvisionai.planner.Handle;
import com.buildvisionai.planner.HitResult;
import com.buildvisionai.planner.HitTester;
import com.buildvisionai.planner.OpeningPlacer;
import com.buildvisionai.planner.PlanDocument;
import com.buildvisionai.planner.SnapResult;
import com.buildvisionai.planner.WallOps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Selection and direct manipulation. A drag that starts on nothing (or on an unselected room fill)
 * is not consumed, so one finger pans the view. Drags edit the live elements for responsiveness
 * and are recorded as ONE undo step when the finger lifts.
 */
public final class SelectTool extends Tool {
    private enum Mode { NONE, MOVE, ENDPOINT, RESIZE, ROTATE, SLIDE }

    private Mode mode = Mode.NONE;
    private final Map<String, PlanElement> originals = new LinkedHashMap<>();
    private final Set<String> movingWalls = new HashSet<>();
    private final Map<String, boolean[]> follow = new HashMap<>();   // wallId -> {startFollows, endFollows}
    private final Set<String> snapExclude = new HashSet<>();
    private double x0, y0;
    private Vec2 ref0;
    private String activeId;
    private Handle activeHandle = Handle.NONE;
    private boolean changed;

    // ---- taps ------------------------------------------------------------------------------

    @Override public boolean onTap(EditorContext c, double wx, double wy) {
        HitResult h = HitTester.hit(c.plan(), c.selection(), wx, wy, c.view(), c.touchPx());
        if (h == null) {
            if (!c.selection().isEmpty()) { c.selection().clear(); c.selectionChanged(); }
        } else if (h.isHandle()) {
            // tapping a handle does nothing; dragging uses it
            return true;
        } else if (c.state().multiSelect) {
            c.selection().toggle(h.element.id);
            c.selectionChanged();
        } else {
            c.selection().set(h.element.id);
            c.selectionChanged();
        }
        c.invalidate();
        return true;
    }

    @Override public boolean onLongPress(EditorContext c, double wx, double wy) {
        HitResult h = HitTester.hit(c.plan(), c.selection(), wx, wy, c.view(), c.touchPx());
        if (h == null || h.isHandle()) return false;
        c.selection().toggle(h.element.id);
        c.selectionChanged();
        c.invalidate();
        return true;
    }

    // ---- drags -----------------------------------------------------------------------------

    @Override public boolean onDragStart(EditorContext c, double wx, double wy) {
        if (!c.canEdit()) return false;
        HitResult h = HitTester.hit(c.plan(), c.selection(), wx, wy, c.view(), c.touchPx());
        if (h == null) return false;
        reset();
        x0 = wx;
        y0 = wy;
        PlanElement e = h.element;

        if (h.isHandle()) {
            activeId = e.id;
            activeHandle = h.handle;
            keep(e);
            if (e instanceof Wall) {
                mode = Mode.ENDPOINT;
                Wall w = (Wall) e;
                Vec2 p = h.handle == Handle.WALL_START ? w.start() : w.end();
                snapExclude.add(w.id);
                for (WallOps.EndpointRef r : WallOps.connectedAt(c.plan(), p.x, p.y, snapExclude, 5)) {
                    keep(r.wall);
                    follow.computeIfAbsent(r.wall.id, k -> new boolean[2])[r.start ? 0 : 1] = true;
                    snapExclude.add(r.wall.id);
                }
            } else {
                mode = h.handle == Handle.ROTATE ? Mode.ROTATE : Mode.RESIZE;
            }
            return true;
        }

        if (e instanceof Room && !c.selection().contains(e.id)) return false; // pan instead of moving a room by accident

        if (!c.selection().contains(e.id)) {
            c.selection().set(e.id);
            c.selectionChanged();
        }

        if (c.selection().isSingle() && (e instanceof Door || e instanceof Window)) {
            mode = Mode.SLIDE;
            activeId = e.id;
            keep(e);
            return true;
        }

        beginMove(c);
        return mode == Mode.MOVE;
    }

    private void beginMove(EditorContext c) {
        FloorPlan fp = c.plan();
        PlanDocument doc = c.doc();
        for (String id : c.selection().ids()) {
            PlanElement e = doc.get(id);
            if (e == null) continue;
            if (e instanceof Wall) {
                movingWalls.add(id);
                keep(e);
            } else if (e instanceof Room) {
                keep(e);
                for (String wid : ((Room) e).wallIds) {
                    PlanElement w = doc.get(wid);
                    if (w instanceof Wall) { movingWalls.add(wid); keep(w); }
                }
            } else if (e instanceof PlacedElement) {
                keep(e);
            }
        }
        if (originals.isEmpty()) return;
        // walls joined to a moving wall's end stretch with it
        snapExclude.addAll(movingWalls);
        List<Wall> moving = new ArrayList<>();
        for (String id : movingWalls) moving.add((Wall) doc.get(id));
        for (Wall w : moving) {
            for (Vec2 p : new Vec2[]{w.start(), w.end()}) {
                for (WallOps.EndpointRef r : WallOps.connectedAt(fp, p.x, p.y, movingWalls, 5)) {
                    keep(r.wall);
                    follow.computeIfAbsent(r.wall.id, k -> new boolean[2])[r.start ? 0 : 1] = true;
                    snapExclude.add(r.wall.id);
                }
            }
        }
        for (String id : originals.keySet()) if (!(originals.get(id) instanceof Wall)) snapExclude.add(id);
        ref0 = reference(originals.values().iterator().next());
        mode = Mode.MOVE;
    }

    private static Vec2 reference(PlanElement o) {
        if (o instanceof Wall) return ((Wall) o).start();
        if (o instanceof PlacedElement) return new Vec2(((PlacedElement) o).x, ((PlacedElement) o).y);
        if (o instanceof Room) return new Vec2(((Room) o).anchorX, ((Room) o).anchorY);
        return new Vec2(0, 0);
    }

    private void keep(PlanElement e) {
        if (!originals.containsKey(e.id)) originals.put(e.id, e.copy());
    }

    @Override public void onDrag(EditorContext c, double wx, double wy) {
        if (mode == Mode.NONE) return;
        PlanDocument doc = c.doc();
        switch (mode) {
            case MOVE: {
                SnapResult s = c.snap(new Vec2(ref0.x + (wx - x0), ref0.y + (wy - y0)), null, snapExclude);
                c.preview().snap = s;
                double dx = s.point.x - ref0.x, dy = s.point.y - ref0.y;
                applyMove(doc, dx, dy);
                break;
            }
            case ENDPOINT: dragEndpoint(c, wx, wy); break;
            case RESIZE: dragCorner(c, wx, wy); break;
            case ROTATE: dragRotate(c, wx, wy); break;
            case SLIDE: slide(c, wx, wy); break;
            default: break;
        }
        doc.touch();
        changed = true;
        c.invalidate();
    }

    private void applyMove(PlanDocument doc, double dx, double dy) {
        for (PlanElement o : originals.values()) {
            PlanElement live = doc.get(o.id);
            if (live == null) continue;
            if (live instanceof Wall) {
                Wall lw = (Wall) live, ow = (Wall) o;
                if (movingWalls.contains(o.id)) {
                    lw.startX = ow.startX + dx; lw.startY = ow.startY + dy;
                    lw.endX = ow.endX + dx; lw.endY = ow.endY + dy;
                } else {
                    boolean[] f = follow.get(o.id);
                    if (f != null && f[0]) { lw.startX = ow.startX + dx; lw.startY = ow.startY + dy; }
                    if (f != null && f[1]) { lw.endX = ow.endX + dx; lw.endY = ow.endY + dy; }
                }
            } else if (live instanceof PlacedElement) {
                PlacedElement lp = (PlacedElement) live, op = (PlacedElement) o;
                lp.x = op.x + dx;
                lp.y = op.y + dy;
            } else if (live instanceof Room) {
                Room lr = (Room) live, or = (Room) o;
                lr.anchorX = or.anchorX + dx;
                lr.anchorY = or.anchorY + dy;
            }
        }
    }

    private void dragEndpoint(EditorContext c, double wx, double wy) {
        Wall live = (Wall) c.doc().get(activeId);
        Wall orig = (Wall) originals.get(activeId);
        boolean start = activeHandle == Handle.WALL_START;
        Vec2 anchor = start ? orig.end() : orig.start();
        SnapResult s = c.snap(new Vec2(wx, wy), anchor, snapExclude);
        c.preview().snap = s;
        if (s.point.distanceTo(anchor) < WallTool.MIN_LENGTH_MM) return; // never collapse a wall
        WallOps.setEndpoint(live, start, s.point.x, s.point.y);
        for (Map.Entry<String, boolean[]> en : follow.entrySet()) {
            Wall lw = (Wall) c.doc().get(en.getKey());
            if (lw == null) continue;
            if (en.getValue()[0]) WallOps.setEndpoint(lw, true, s.point.x, s.point.y);
            if (en.getValue()[1]) WallOps.setEndpoint(lw, false, s.point.x, s.point.y);
        }
    }

    private void dragCorner(EditorContext c, double wx, double wy) {
        PlacedElement live = (PlacedElement) c.doc().get(activeId);
        PlacedElement o = (PlacedElement) originals.get(activeId);
        Vec2 p = c.snap(new Vec2(wx, wy), null, null).point;
        Vec2 pl = o.toLocal(p.x, p.y);
        double sx = (activeHandle == Handle.CORNER_NE || activeHandle == Handle.CORNER_SE) ? 1 : -1;
        double sy = (activeHandle == Handle.CORNER_SE || activeHandle == Handle.CORNER_SW) ? 1 : -1;
        double fx = -sx * o.widthMm / 2, fy = -sy * o.depthMm / 2;   // fixed opposite corner, local
        double w = Math.max(100, Math.abs(pl.x - fx));
        double d = Math.max(100, Math.abs(pl.y - fy));
        if (o.isElliptical()) { w = d = Math.max(w, d); }
        double cx = fx + sx * w / 2, cy = fy + sy * d / 2;
        Vec2 centre = o.toWorld(cx, cy);
        live.x = centre.x;
        live.y = centre.y;
        live.widthMm = w;
        live.depthMm = d;
    }

    private void dragRotate(EditorContext c, double wx, double wy) {
        PlacedElement live = (PlacedElement) c.doc().get(activeId);
        PlacedElement o = (PlacedElement) originals.get(activeId);
        double ang = Math.toDegrees(Math.atan2(wy - o.y, wx - o.x)) + 90;
        double snapped = Math.round(ang / 15.0) * 15.0;
        if (c.state().snap.enabled && Math.abs(GeoUtils.angleDiffDeg(ang, snapped)) < 4) ang = snapped;
        live.rotationDeg = GeoUtils.normDeg(ang);
    }

    private void slide(EditorContext c, double wx, double wy) {
        PlanElement live = c.doc().get(activeId);
        FloorPlan fp = c.plan();
        double width = live instanceof Door ? ((Door) live).widthMm : ((Window) live).widthMm;
        Wall target = OpeningPlacer.nearestWall(fp, wx, wy, Math.max(400, c.view().pxToMm(c.touchPx() * 2)));
        if (target == null) return;
        double off = OpeningPlacer.place(fp, target, OpeningPlacer.offsetOf(target, wx, wy), width, activeId);
        if (Double.isNaN(off)) return;
        if (live instanceof Door) { ((Door) live).wallId = target.id; ((Door) live).offsetMm = off; }
        else { ((Window) live).wallId = target.id; ((Window) live).offsetMm = off; }
    }

    @Override public void onDragEnd(EditorContext c) {
        if (mode == Mode.NONE) return;
        EditTransaction tx = null;
        if (changed) {
            tx = new EditTransaction(label());
            for (PlanElement o : originals.values()) {
                PlanElement live = c.doc().get(o.id);
                if (live != null && differs(o, live)) tx.modify(o, live);
            }
        }
        reset();
        c.preview().clear();
        if (tx != null && !tx.isEmpty()) c.commit(tx, true);
        c.invalidate();
    }

    private String label() {
        switch (mode) {
            case ENDPOINT: return "Edit wall";
            case RESIZE: return "Resize";
            case ROTATE: return "Rotate";
            case SLIDE: return "Move opening";
            default: return "Move";
        }
    }

    private static boolean differs(PlanElement a, PlanElement b) {
        if (a instanceof Wall) {
            Wall x = (Wall) a, y = (Wall) b;
            return x.startX != y.startX || x.startY != y.startY || x.endX != y.endX || x.endY != y.endY;
        }
        if (a instanceof PlacedElement) {
            PlacedElement x = (PlacedElement) a, y = (PlacedElement) b;
            return x.x != y.x || x.y != y.y || x.widthMm != y.widthMm || x.depthMm != y.depthMm || x.rotationDeg != y.rotationDeg;
        }
        if (a instanceof Room) {
            Room x = (Room) a, y = (Room) b;
            return x.anchorX != y.anchorX || x.anchorY != y.anchorY;
        }
        if (a instanceof Door) return ((Door) a).offsetMm != ((Door) b).offsetMm || !((Door) a).wallId.equals(((Door) b).wallId);
        if (a instanceof Window) return ((Window) a).offsetMm != ((Window) b).offsetMm || !((Window) a).wallId.equals(((Window) b).wallId);
        return true;
    }

    private void reset() {
        mode = Mode.NONE;
        originals.clear();
        movingWalls.clear();
        follow.clear();
        snapExclude.clear();
        activeId = null;
        activeHandle = Handle.NONE;
        changed = false;
        ref0 = null;
    }

    /** Aborts the drag and restores every element to where it started. */
    @Override public void cancel(EditorContext c) {
        if (mode != Mode.NONE) {
            for (PlanElement o : originals.values()) c.doc().put(o.copy());
        }
        reset();
        c.preview().clear();
        c.invalidate();
    }
}
