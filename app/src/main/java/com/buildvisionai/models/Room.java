package com.buildvisionai.models;

import java.util.ArrayList;
import java.util.List;

/**
 * A room is the user's record (name, type) attached to a closed loop of walls. The loop itself is
 * derived from the walls; the {@code anchor} point inside the loop is the identity key that lets a
 * room keep its name and type while the surrounding walls are moved or split.
 *
 * <p>The metric fields are a persisted cache of values recomputed on every edit.
 */
public final class Room extends PlanElement {
    public String name;
    public RoomType roomType;
    public double anchorX, anchorY;
    /** Boundary walls (derived). */
    public final List<String> wallIds = new ArrayList<>();

    // Derived metrics, written by RoomSync. Areas in m2, lengths in m.
    public double areaM2;
    public double grossAreaM2;
    public double perimeterM;
    public double widthM;
    public double lengthM;

    /** Net (internal) polygon in mm. Derived, never persisted. */
    public transient java.util.List<Vec2> polygon;

    public Room(String id, String floorId, String name, RoomType roomType, double anchorX, double anchorY) {
        super(id, floorId);
        this.name = name;
        this.roomType = roomType;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
    }

    @Override public ElementType type() { return ElementType.ROOM; }

    @Override public RectD bounds() {
        if (polygon != null && !polygon.isEmpty()) {
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (Vec2 p : polygon) {
                minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
                minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y);
            }
            return new RectD(minX, minY, maxX, maxY);
        }
        return new RectD(anchorX - 1, anchorY - 1, anchorX + 1, anchorY + 1);
    }

    @Override public Room copy() {
        Room r = new Room(id, floorId, name, roomType, anchorX, anchorY);
        r.wallIds.addAll(wallIds);
        r.areaM2 = areaM2;
        r.grossAreaM2 = grossAreaM2;
        r.perimeterM = perimeterM;
        r.widthM = widthM;
        r.lengthM = lengthM;
        r.polygon = polygon;
        return r;
    }
}
