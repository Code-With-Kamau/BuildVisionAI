package com.buildvisionai.geometry;

import com.buildvisionai.models.RectD;
import com.buildvisionai.models.Vec2;

import java.util.Collections;
import java.util.List;

/**
 * A closed loop of walls found by {@link WallGraph}, with its geometry measured both on the wall
 * centrelines (gross) and on the inside faces (net). Net area is what a person would measure in
 * the finished room and what finishes (tiles, paint, ceiling) are quantified from.
 */
public final class DetectedRoom {
    /** Positively oriented centreline loop (mm). */
    public final List<Vec2> centreline;
    /** Net polygon: centreline offset inwards by half of each bounding wall's thickness. */
    public final List<Vec2> netPolygon;
    /** False when the offset was degenerate and net values are approximated. */
    public final boolean netExact;
    /** Distinct ids of the walls bounding the room, in order of first appearance. */
    public final List<String> wallIds;
    /** Wall id of each centreline side (same indexing as {@link #centreline}). */
    public final List<String> sideWallIds;

    public final double grossAreaM2;
    public final double netAreaM2;
    public final double netPerimeterM;
    /** Short and long side of the minimum oriented bounding rectangle of the net polygon. */
    public final double widthM;
    public final double lengthM;
    public final Vec2 interior;
    public final RectD bounds;

    DetectedRoom(List<Vec2> centreline, List<Vec2> netPolygon, boolean netExact, List<String> wallIds,
                 List<String> sideWallIds, double grossAreaM2, double netAreaM2, double netPerimeterM,
                 double widthM, double lengthM, Vec2 interior, RectD bounds) {
        this.centreline = Collections.unmodifiableList(centreline);
        this.netPolygon = Collections.unmodifiableList(netPolygon);
        this.netExact = netExact;
        this.wallIds = Collections.unmodifiableList(wallIds);
        this.sideWallIds = Collections.unmodifiableList(sideWallIds);
        this.grossAreaM2 = grossAreaM2;
        this.netAreaM2 = netAreaM2;
        this.netPerimeterM = netPerimeterM;
        this.widthM = widthM;
        this.lengthM = lengthM;
        this.interior = interior;
        this.bounds = bounds;
    }

    /** True if the point is inside the room measured on wall centrelines. */
    public boolean contains(double x, double y) {
        return bounds.contains(x, y) && GeoUtils.pointInPolygon(x, y, centreline);
    }
}
