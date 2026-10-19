package com.buildvisionai.planner;

import com.buildvisionai.geometry.DetectedRoom;
import com.buildvisionai.geometry.GeometryEngine;
import com.buildvisionai.geometry.PlanMetrics;
import com.buildvisionai.geometry.PlanTopology;
import com.buildvisionai.geometry.SpatialGrid;
import com.buildvisionai.models.Column;
import com.buildvisionai.models.Door;
import com.buildvisionai.models.Furniture;
import com.buildvisionai.models.PlanElement;
import com.buildvisionai.models.RectD;
import com.buildvisionai.models.Room;
import com.buildvisionai.models.Stair;
import com.buildvisionai.models.Wall;
import com.buildvisionai.models.Window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only snapshot of one floor, derived from {@link PlanDocument} and cached per document
 * revision. Holds typed lists, a spatial index and (lazily) the topology and metrics. Rendering,
 * hit-testing, snapping and estimation all read from here, so none of them scan the whole plan.
 */
public final class FloorPlan {
    public final String floorId;
    public final long revision;
    public final List<Wall> walls = new ArrayList<>();
    public final List<Door> doors = new ArrayList<>();
    public final List<Window> windows = new ArrayList<>();
    public final List<Column> columns = new ArrayList<>();
    public final List<Stair> stairs = new ArrayList<>();
    public final List<Furniture> furniture = new ArrayList<>();
    public final List<Room> rooms = new ArrayList<>();
    public final Map<String, Wall> wallsById = new HashMap<>();
    private final SpatialGrid<PlanElement> grid = new SpatialGrid<>(1000);

    private PlanTopology topology;
    private PlanMetrics metrics;
    private Map<Room, DetectedRoom> roomShapes;

    FloorPlan(String floorId, long revision, Iterable<PlanElement> all) {
        this.floorId = floorId;
        this.revision = revision;
        for (PlanElement e : all) {
            if (!floorId.equals(e.floorId)) continue;
            switch (e.type()) {
                case WALL: walls.add((Wall) e); wallsById.put(e.id, (Wall) e); break;
                case DOOR: doors.add((Door) e); break;
                case WINDOW: windows.add((Window) e); break;
                case COLUMN: columns.add((Column) e); break;
                case STAIR: stairs.add((Stair) e); break;
                case FURNITURE: furniture.add((Furniture) e); break;
                case ROOM: rooms.add((Room) e); break;
                default: break;
            }
        }
        GeometryEngine.resolveOpenings(wallsById, doors, windows);
        for (PlanElement e : walls) grid.insert(e, e.bounds());
        for (PlanElement e : doors) grid.insert(e, e.bounds());
        for (PlanElement e : windows) grid.insert(e, e.bounds());
        for (PlanElement e : columns) grid.insert(e, e.bounds());
        for (PlanElement e : stairs) grid.insert(e, e.bounds());
        for (PlanElement e : furniture) grid.insert(e, e.bounds());
    }

    /** Elements whose index cells overlap the circle around (x, y); a superset of true hits. */
    public List<PlanElement> near(double x, double y, double radiusMm) {
        return grid.queryPoint(x, y, radiusMm);
    }

    public List<PlanElement> inRect(RectD r) { return grid.query(r); }

    public PlanTopology topology() {
        if (topology == null) topology = PlanTopology.build(walls);
        return topology;
    }

    public PlanMetrics metrics() {
        if (metrics == null) metrics = GeometryEngine.analyse(topology(), walls, doors, windows);
        return metrics;
    }

    /** Detected loop that belongs to a room record (anchor inside it), or null if its loop is broken. */
    public DetectedRoom shapeOf(Room r) {
        if (roomShapes == null) {
            roomShapes = new IdentityHashMap<>();
            for (Room room : rooms) {
                DetectedRoom d = topology().roomContaining(room.anchorX, room.anchorY);
                if (d != null) roomShapes.put(room, d);
            }
        }
        return roomShapes.get(r);
    }

    public boolean isEmpty() {
        return walls.isEmpty() && columns.isEmpty() && stairs.isEmpty() && furniture.isEmpty();
    }

    /** Bounds of all drawn content, or null for an empty floor. */
    public RectD contentBounds() {
        RectD b = null;
        List<PlanElement> all = new ArrayList<>();
        all.addAll(walls); all.addAll(columns); all.addAll(stairs); all.addAll(furniture);
        for (PlanElement e : all) b = b == null ? e.bounds() : b.union(e.bounds());
        return b;
    }

    public List<Door> doorsOn(String wallId) {
        List<Door> r = new ArrayList<>();
        for (Door d : doors) if (wallId.equals(d.wallId)) r.add(d);
        return r;
    }

    public List<Window> windowsOn(String wallId) {
        List<Window> r = new ArrayList<>();
        for (Window w : windows) if (wallId.equals(w.wallId)) r.add(w);
        return r;
    }

    public List<Wall> wallsView() { return Collections.unmodifiableList(walls); }
}
