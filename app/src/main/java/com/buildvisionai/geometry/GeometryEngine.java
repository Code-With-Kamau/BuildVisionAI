package com.buildvisionai.geometry;

import com.buildvisionai.models.Door;
import com.buildvisionai.models.Vec2;
import com.buildvisionai.models.Wall;
import com.buildvisionai.models.Window;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic geometry: attaches openings to walls, builds the topology and measures walls and
 * rooms. No AI and no randomness: the same plan always gives the same numbers, which is what makes
 * estimates reproducible and auditable.
 *
 * <p>Wall quantities use the centreline method (length of the wall axis x height), the usual
 * quick-takeoff convention; it slightly over-counts at corners and is accepted for a pre-design
 * estimate.
 */
public final class GeometryEngine {
    private GeometryEngine() {}

    /**
     * Resolves world positions of doors and windows from their wall attachment. An opening whose
     * wall is gone, or too short to hold it, is marked {@code attached = false}.
     */
    public static void resolveOpenings(Map<String, Wall> walls, Collection<Door> doors, Collection<Window> windows) {
        for (Door d : doors) {
            Wall w = walls.get(d.wallId);
            d.attached = place(w, d.offsetMm, d.widthMm, p -> {
                d.worldX = p[0]; d.worldY = p[1]; d.angleDeg = p[2]; d.wallThicknessMm = p[3];
            });
        }
        for (Window win : windows) {
            Wall w = walls.get(win.wallId);
            win.attached = place(w, win.offsetMm, win.widthMm, p -> {
                win.worldX = p[0]; win.worldY = p[1]; win.angleDeg = p[2]; win.wallThicknessMm = p[3];
            });
        }
    }

    private interface Sink { void set(double[] xyAngleThickness); }

    private static boolean place(Wall w, double offsetMm, double widthMm, Sink sink) {
        if (w == null) return false;
        double len = w.lengthMm();
        if (len + 1e-6 < widthMm) {
            Vec2 m = w.pointAt(len / 2);
            sink.set(new double[]{m.x, m.y, w.rotationDeg(), w.thicknessMm});
            return false;
        }
        double off = Math.max(widthMm / 2, Math.min(len - widthMm / 2, offsetMm));
        Vec2 p = w.pointAt(off);
        sink.set(new double[]{p.x, p.y, w.rotationDeg(), w.thicknessMm});
        return true;
    }

    /** Full analysis of one floor. Openings must already be resolved. */
    public static PlanMetrics analyse(Collection<Wall> walls, Collection<Door> doors, Collection<Window> windows) {
        return analyse(PlanTopology.build(walls), walls, doors, windows);
    }

    public static PlanMetrics analyse(PlanTopology topo, Collection<Wall> walls, Collection<Door> doors,
                                      Collection<Window> windows) {
        PlanMetrics pm = new PlanMetrics(topo);
        Map<String, Wall> byId = new HashMap<>();
        for (Wall w : walls) byId.put(w.id, w);

        // wall metrics
        for (Wall w : walls) {
            WallMetrics m = new WallMetrics();
            m.wallId = w.id;
            m.kind = w.kind;
            m.material = w.material;
            m.lengthM = w.lengthMm() / 1000.0;
            m.heightM = w.heightMm / 1000.0;
            m.thicknessMm = w.thicknessMm;
            m.grossFaceAreaM2 = m.lengthM * m.heightM;
            pm.walls.add(m);
            pm.byWallId.put(w.id, m);
        }
        for (Door d : doors) {
            WallMetrics m = pm.byWallId.get(d.wallId);
            if (m == null || !d.attached) { pm.unattachedOpenings++; continue; }
            m.openingAreaM2 += d.widthMm * d.heightMm / 1e6;
            m.doorCount++;
            pm.doorCount++;
        }
        for (Window win : windows) {
            WallMetrics m = pm.byWallId.get(win.wallId);
            if (m == null || !win.attached) { pm.unattachedOpenings++; continue; }
            m.openingAreaM2 += win.widthMm * win.heightMm / 1e6;
            m.windowCount++;
            pm.windowCount++;
        }
        for (WallMetrics m : pm.walls) {
            m.netFaceAreaM2 = Math.max(0, m.grossFaceAreaM2 - m.openingAreaM2);
            m.volumeM3 = m.netFaceAreaM2 * m.thicknessMm / 1000.0;
            pm.totalWallLengthM += m.lengthM;
            pm.wallLengthByKindM.merge(m.kind, m.lengthM, Double::sum);
        }

        // room metrics
        for (DetectedRoom dr : topo.rooms) {
            RoomMetrics rm = new RoomMetrics(dr);
            double hSum = 0, lSum = 0;
            int n = dr.centreline.size();
            for (int i = 0; i < n; i++) {
                Wall w = byId.get(dr.sideWallIds.get(i));
                double len = dr.centreline.get(i).distanceTo(dr.centreline.get((i + 1) % n));
                hSum += (w == null ? 2700 : w.heightMm) * len;
                lSum += len;
            }
            rm.wallHeightM = lSum > 0 ? hSum / lSum / 1000.0 : 2.7;
            rm.grossWallSurfaceM2 = dr.netPerimeterM * rm.wallHeightM;
            for (Door d : doors) {
                if (d.attached && onBoundary(dr, d.worldX, d.worldY)) {
                    rm.openingAreaM2 += d.widthMm * d.heightMm / 1e6;
                    rm.doorCount++;
                    rm.doorWidthTotalM += d.widthMm / 1000.0;
                }
            }
            for (Window win : windows) {
                if (win.attached && onBoundary(dr, win.worldX, win.worldY)) {
                    rm.openingAreaM2 += win.widthMm * win.heightMm / 1e6;
                    rm.windowCount++;
                }
            }
            rm.netWallSurfaceM2 = Math.max(0, rm.grossWallSurfaceM2 - rm.openingAreaM2);
            pm.rooms.add(rm);
            pm.netFloorAreaM2 += dr.netAreaM2;
        }
        pm.footprintAreaM2 = topo.footprintAreaM2;
        return pm;
    }

    private static boolean onBoundary(DetectedRoom r, double x, double y) {
        List<Vec2> c = r.centreline;
        int n = c.size();
        for (int i = 0; i < n; i++) {
            Vec2 a = c.get(i), b = c.get((i + 1) % n);
            if (GeoUtils.distToSegment(x, y, a.x, a.y, b.x, b.y) <= WallGraph.DEFAULT_TOL_MM) return true;
        }
        return false;
    }
}
