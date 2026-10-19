package com.buildvisionai.geometry;

import com.buildvisionai.models.WallKind;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Result of {@link GeometryEngine#analyse}: everything the estimators need about one floor. */
public final class PlanMetrics {
    public final PlanTopology topology;
    public final List<WallMetrics> walls = new ArrayList<>();
    public final Map<String, WallMetrics> byWallId = new HashMap<>();
    public final List<RoomMetrics> rooms = new ArrayList<>();
    public final EnumMap<WallKind, Double> wallLengthByKindM = new EnumMap<>(WallKind.class);
    public double totalWallLengthM;
    public double netFloorAreaM2;
    public double footprintAreaM2;
    public int doorCount;
    public int windowCount;
    /** Openings whose wall is missing or too short to hold them. */
    public int unattachedOpenings;

    PlanMetrics(PlanTopology topology) { this.topology = topology; }

    public double wallLengthM(WallKind k) {
        Double v = wallLengthByKindM.get(k);
        return v == null ? 0 : v;
    }
}
