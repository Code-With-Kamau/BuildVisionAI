package com.buildvisionai.planner;

import com.buildvisionai.models.Door;
import com.buildvisionai.models.PlanElement;
import com.buildvisionai.models.Wall;
import com.buildvisionai.models.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Wall-level editing helpers: connected endpoints and cascading deletes. */
public final class WallOps {
    private WallOps() {}

    /** One end of a wall. */
    public static final class EndpointRef {
        public final Wall wall;
        public final boolean start;

        EndpointRef(Wall wall, boolean start) { this.wall = wall; this.start = start; }
    }

    /** Wall ends (of walls not in {@code exclude}) that sit within tolMm of the point. */
    public static List<EndpointRef> connectedAt(FloorPlan fp, double x, double y, Set<String> exclude, double tolMm) {
        List<EndpointRef> out = new ArrayList<>();
        for (Wall w : fp.walls) {
            if (exclude != null && exclude.contains(w.id)) continue;
            if (Math.hypot(w.startX - x, w.startY - y) <= tolMm) out.add(new EndpointRef(w, true));
            if (Math.hypot(w.endX - x, w.endY - y) <= tolMm) out.add(new EndpointRef(w, false));
        }
        return out;
    }

    /** Adds REMOVE changes for the walls and every door/window attached to them. */
    public static void removeWalls(PlanDocument doc, FloorPlan fp, Set<String> wallIds, EditTransaction tx) {
        for (String id : wallIds) {
            PlanElement w = doc.get(id);
            if (w == null) continue;
            for (Door d : fp.doorsOn(id)) tx.remove(d);
            for (Window win : fp.windowsOn(id)) tx.remove(win);
            tx.remove(w);
        }
    }

    public static void setEndpoint(Wall w, boolean start, double x, double y) {
        if (start) { w.startX = x; w.startY = y; } else { w.endX = x; w.endY = y; }
    }
}
