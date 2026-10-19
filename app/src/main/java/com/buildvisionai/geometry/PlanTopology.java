package com.buildvisionai.geometry;

import com.buildvisionai.models.RectD;
import com.buildvisionai.models.Vec2;
import com.buildvisionai.models.Wall;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything derivable from the walls of one floor: closed rooms and the building footprint.
 * Immutable once built; the editor caches one per plan revision so repeated reads (rendering,
 * hit-testing, estimation) never recompute it.
 */
public final class PlanTopology {
    /** Loops smaller than this are ignored (0.1 m2): they are drawing slivers, not rooms. */
    public static final double MIN_ROOM_AREA_MM2 = 100_000.0;

    public final List<DetectedRoom> rooms;
    public final List<Footprint> footprints;
    public final double footprintAreaM2;
    public final double externalPerimeterM;

    private PlanTopology(List<DetectedRoom> rooms, List<Footprint> footprints) {
        this.rooms = Collections.unmodifiableList(rooms);
        this.footprints = Collections.unmodifiableList(footprints);
        double a = 0, p = 0;
        for (Footprint f : footprints) { a += f.areaM2; p += f.perimeterM; }
        this.footprintAreaM2 = a;
        this.externalPerimeterM = p;
    }

    public static PlanTopology empty() {
        return new PlanTopology(new ArrayList<>(), new ArrayList<>());
    }

    public static PlanTopology build(Collection<Wall> walls) {
        Map<String, Wall> byId = new HashMap<>();
        for (Wall w : walls) byId.put(w.id, w);
        WallGraph g = WallGraph.build(walls);

        List<DetectedRoom> rooms = new ArrayList<>();
        List<Footprint> prints = new ArrayList<>();
        for (WallGraph.Face f : g.faces) {
            if (f.signedArea >= MIN_ROOM_AREA_MM2) {
                rooms.add(toRoom(f, byId));
            } else if (f.signedArea < -MIN_ROOM_AREA_MM2) {
                prints.add(toFootprint(f, byId));
            }
        }
        // stable, readable order: top-to-bottom, left-to-right by interior point
        Collections.sort(rooms, (a, b) -> {
            int c = Double.compare(Math.round(a.interior.y / 500.0), Math.round(b.interior.y / 500.0));
            return c != 0 ? c : Double.compare(a.interior.x, b.interior.x);
        });
        return new PlanTopology(rooms, prints);
    }

    /** Smallest detected room whose centreline loop contains the point, or null. */
    public DetectedRoom roomContaining(double x, double y) {
        DetectedRoom best = null;
        for (DetectedRoom r : rooms) {
            if (r.contains(x, y) && (best == null || r.grossAreaM2 < best.grossAreaM2)) best = r;
        }
        return best;
    }

    

    private static double[] halfThickness(WallGraph.Face f, Map<String, Wall> byId) {
        double[] d = new double[f.edges.size()];
        for (int i = 0; i < d.length; i++) {
            Wall w = byId.get(f.edges.get(i).wallId);
            d[i] = (w == null ? 150 : w.thicknessMm) / 2.0;
        }
        return d;
    }

    private static DetectedRoom toRoom(WallGraph.Face f, Map<String, Wall> byId) {
        List<Vec2> cl = new ArrayList<>(f.polygon);
        double[] half = halfThickness(f, byId);
        double grossMm2 = f.signedArea;

        List<Vec2> net = null;
        boolean exact = false;
        if (!f.hasBridge) {
            List<Vec2> cand = PolygonOps.offsetEdges(cl, half);
            double a = GeoUtils.signedArea(cand);
            if (a > 0 && a < grossMm2 && PolygonOps.isSimple(cand)) {
                net = cand;
                exact = true;
            }
        }
        double netMm2, netPerimMm;
        if (exact) {
            netMm2 = GeoUtils.area(net);
            netPerimMm = GeoUtils.perimeter(net);
        } else {
            net = cl;
            double avgHalf = 0, per = 0;
            for (int i = 0; i < half.length; i++) {
                double len = cl.get(i).distanceTo(cl.get((i + 1) % cl.size()));
                avgHalf += half[i] * len;
                per += len;
            }
            avgHalf = per > 0 ? avgHalf / per : 0;
            netMm2 = Math.max(0, grossMm2 - per * avgHalf);
            netPerimMm = Math.max(0, per - 8 * avgHalf);
        }

        Set<String> ids = new LinkedHashSet<>();
        List<String> side = new ArrayList<>(f.edges.size());
        for (WallGraph.Edge e : f.edges) { ids.add(e.wallId); side.add(e.wallId); }

        double[] ext = PolygonOps.orientedExtents(net);
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Vec2 p : cl) {
            minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y);
        }
        Vec2 interior = GeoUtils.interiorPoint(net);
        return new DetectedRoom(cl, net, exact, new ArrayList<>(ids), side,
                grossMm2 / 1e6, netMm2 / 1e6, netPerimMm / 1000.0,
                ext[0] / 1000.0, ext[1] / 1000.0, interior, new RectD(minX, minY, maxX, maxY));
    }

    private static Footprint toFootprint(WallGraph.Face f, Map<String, Wall> byId) {
        // Reverse so the polygon is positive, keeping each side's wall thickness aligned.
        int k = f.polygon.size();
        List<Vec2> rev = new ArrayList<>(k);
        double[] halfFwd = halfThickness(f, byId);
        double[] half = new double[k];
        for (int j = 0; j < k; j++) {
            rev.add(f.polygon.get(k - 1 - j));
            half[j] = halfFwd[(k - 2 - j + k) % k];
        }
        double[] out = new double[k];
        for (int j = 0; j < k; j++) out[j] = -half[j];

        double clAreaMm2 = -f.signedArea;
        List<Vec2> cand = PolygonOps.offsetEdges(rev, out);
        double a = GeoUtils.signedArea(cand);
        if (!f.hasBridge && a > clAreaMm2 && PolygonOps.isSimple(cand)) {
            return new Footprint(cand, a / 1e6, GeoUtils.perimeter(cand) / 1000.0, true);
        }
        double per = GeoUtils.perimeter(rev), avgHalf = 0;
        for (double h : half) avgHalf += h;
        avgHalf = k > 0 ? avgHalf / k : 0;
        return new Footprint(rev, (clAreaMm2 + per * avgHalf) / 1e6, (per + 8 * avgHalf) / 1000.0, false);
    }
}
