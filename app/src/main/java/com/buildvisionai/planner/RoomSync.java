package com.buildvisionai.planner;

import com.buildvisionai.geometry.DetectedRoom;
import com.buildvisionai.geometry.GeoUtils;
import com.buildvisionai.geometry.PlanTopology;
import com.buildvisionai.models.Room;
import com.buildvisionai.models.RoomType;
import com.buildvisionai.models.Vec2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps room records consistent with the walls after every edit.
 *
 * <ul>
 *   <li>A room record follows its loop through its anchor point, so it keeps its name and type when
 *       surrounding walls move, or when a wall splits it (the half holding the anchor keeps it).</li>
 *   <li>A new closed loop automatically becomes a room named "Room n".</li>
 *   <li>A loop that is broken removes its room (undo brings it back, name and all).</li>
 * </ul>
 *
 * Changes are written both to the document and to the caller's transaction, so one undo reverts
 * the wall edit and the room changes together.
 */
public final class RoomSync {
    private RoomSync() {}

    public static final class Result {
        public int added, removed, updated;
        public boolean any() { return added + removed + updated > 0; }
    }

    private static final double EPS = 1e-6;

    /** The document must already contain the primary changes of {@code tx}. */
    public static Result sync(PlanDocument doc, String floorId, EditTransaction tx) {
        Result res = new Result();
        FloorPlan fp = doc.floorPlan(floorId);
        PlanTopology topo = fp.topology();
        EditTransaction derived = new EditTransaction(tx.label);

        // group existing rooms by the loop that contains their anchor
        Map<DetectedRoom, List<Room>> claims = new IdentityHashMap<>();
        List<Room> orphans = new ArrayList<>();
        for (Room r : fp.rooms) {
            DetectedRoom d = topo.roomContaining(r.anchorX, r.anchorY);
            if (d == null) orphans.add(r);
            else claims.computeIfAbsent(d, k -> new ArrayList<>()).add(r);
        }

        Set<String> usedNames = new HashSet<>();
        for (Room r : fp.rooms) usedNames.add(r.name);

        for (DetectedRoom d : topo.rooms) {
            List<Room> list = claims.get(d);
            if (list == null || list.isEmpty()) continue;
            Room winner = list.get(0);
            for (Room r : list) {
                if (r.areaM2 > winner.areaM2 + EPS || (Math.abs(r.areaM2 - winner.areaM2) <= EPS && r.id.compareTo(winner.id) < 0)) winner = r;
            }
            for (Room r : list) if (r != winner) orphans.add(r);

            Room before = winner.copy();
            Room after = winner.copy();
            apply(after, d);
            if (!GeoUtils.pointInPolygon(after.anchorX, after.anchorY, d.netPolygon)) {
                after.anchorX = d.interior.x;
                after.anchorY = d.interior.y;
            }
            if (changed(before, after)) {
                derived.modify(before, after);
                res.updated++;
            }
        }

        for (Room r : orphans) {
            derived.remove(r);
            res.removed++;
        }

        for (DetectedRoom d : topo.rooms) {
            if (claims.containsKey(d) && !claims.get(d).isEmpty()) continue;
            Room r = ElementFactory.room(floorId, nextName(usedNames), RoomType.CUSTOM, d.interior.x, d.interior.y);
            usedNames.add(r.name);
            apply(r, d);
            derived.add(r);
            res.added++;
        }

        if (!derived.isEmpty()) {
            derived.apply(doc);
            tx.append(derived);
        }
        return res;
    }

    static void apply(Room r, DetectedRoom d) {
        r.areaM2 = d.netAreaM2;
        r.grossAreaM2 = d.grossAreaM2;
        r.perimeterM = d.netPerimeterM;
        r.widthM = d.widthM;
        r.lengthM = d.lengthM;
        r.wallIds.clear();
        r.wallIds.addAll(d.wallIds);
        r.polygon = d.netPolygon;
    }

    private static boolean changed(Room a, Room b) {
        return Math.abs(a.areaM2 - b.areaM2) > EPS || Math.abs(a.grossAreaM2 - b.grossAreaM2) > EPS
                || Math.abs(a.perimeterM - b.perimeterM) > EPS || Math.abs(a.widthM - b.widthM) > EPS
                || Math.abs(a.lengthM - b.lengthM) > EPS || a.anchorX != b.anchorX || a.anchorY != b.anchorY
                || !a.wallIds.equals(b.wallIds);
    }

    private static String nextName(Set<String> used) {
        int n = 1;
        while (used.contains("Room " + n)) n++;
        return "Room " + n;
    }

    /** Anchor helper for tools that need a room's inside point. */
    public static Vec2 anchorOf(Room r) { return new Vec2(r.anchorX, r.anchorY); }
}
