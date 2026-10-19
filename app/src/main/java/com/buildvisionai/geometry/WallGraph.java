package com.buildvisionai.geometry;

import com.buildvisionai.models.RectD;
import com.buildvisionai.models.Vec2;
import com.buildvisionai.models.Wall;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Planar graph built from wall centrelines, used to find closed loops (rooms) and the outer
 * outline (footprint).
 *
 * <p>Pipeline: (1) split every wall wherever another wall crosses it or ends on it (T-junction);
 * (2) merge nearby points into vertices; (3) drop dangling edges (walls that do not take part in
 * any loop); (4) walk faces with the half-edge "next = clockwise neighbour of the twin" rule.
 *
 * <p>With that rule every bounded face comes out with positive coordinate-space area and each
 * connected component has exactly one outer face with negative area. Walls are never modified:
 * the splitting is virtual, so a wall keeps its identity for doors, windows and estimation.
 *
 * <p>Known limitations (documented, not silently wrong): walls that overlap collinearly are not
 * merged, and an island (separate loop fully inside a room) is not subtracted from that room.
 */
public final class WallGraph {
    public static final double DEFAULT_TOL_MM = 5.0;

    public static final class Edge {
        public final int id;
        public final int a, b;
        public final String wallId;
        Edge(int id, int a, int b, String wallId) { this.id = id; this.a = a; this.b = b; this.wallId = wallId; }
    }

    /** One face (closed boundary walk). */
    public static final class Face {
        /** Vertices in traversal order; edge i runs from polygon[i] to polygon[i+1]. */
        public final List<Vec2> polygon;
        /** Edge of each polygon side, same indexing. */
        public final List<Edge> edges;
        public final double signedArea;
        /** True if some edge is walked twice by this face (a bridge); offsetting is not reliable. */
        public final boolean hasBridge;

        Face(List<Vec2> polygon, List<Edge> edges, double signedArea, boolean hasBridge) {
            this.polygon = polygon;
            this.edges = edges;
            this.signedArea = signedArea;
            this.hasBridge = hasBridge;
        }

        public boolean isBounded() { return signedArea > 0; }
    }

    public final List<Vec2> vertices = new ArrayList<>();
    public final List<Edge> edges = new ArrayList<>();
    public final List<Face> faces = new ArrayList<>();

    private WallGraph() {}

    private static final class SplitPoint {
        final double t;
        final Vec2 p;
        SplitPoint(double t, Vec2 p) { this.t = t; this.p = p; }
    }

    public static WallGraph build(Collection<Wall> walls) { return build(walls, DEFAULT_TOL_MM); }

    public static WallGraph build(Collection<Wall> walls, double tol) {
        WallGraph g = new WallGraph();
        List<Wall> ws = new ArrayList<>();
        for (Wall w : walls) if (w.lengthMm() > tol) ws.add(w);
        int n = ws.size();
        if (n == 0) return g;

        // 1. split points
        List<List<SplitPoint>> splits = new ArrayList<>(n);
        for (int i = 0; i < n; i++) splits.add(new ArrayList<>(4));

        SpatialGrid<Integer> grid = new SpatialGrid<>(2000);
        for (int i = 0; i < n; i++) grid.insert(i, ws.get(i).bounds());

        for (int i = 0; i < n; i++) {
            Wall a = ws.get(i);
            for (int j : grid.query(a.bounds())) {
                if (j <= i) continue;
                Wall b = ws.get(j);
                if (!a.bounds().intersects(b.bounds())) continue;
                tryEndpoint(a.start(), b, splits.get(j), tol);
                tryEndpoint(a.end(), b, splits.get(j), tol);
                tryEndpoint(b.start(), a, splits.get(i), tol);
                tryEndpoint(b.end(), a, splits.get(i), tol);
                double[] tu = GeoUtils.segmentIntersection(a.start(), a.end(), b.start(), b.end(), 0);
                if (tu != null) {
                    Vec2 p = a.pointAt(tu[0] * a.lengthMm());
                    addSplit(splits.get(i), tu[0], p, a.lengthMm(), tol);
                    addSplit(splits.get(j), tu[1], p, b.lengthMm(), tol);
                }
            }
        }

        // 2. vertices + edges 
        VertexIndex vi = new VertexIndex(g.vertices, tol);
        Set<Long> seenPairs = new HashSet<>();
        for (int i = 0; i < n; i++) {
            Wall w = ws.get(i);
            List<SplitPoint> sp = splits.get(i);
            Collections.sort(sp, Comparator.comparingDouble(s -> s.t));
            List<Vec2> pts = new ArrayList<>(sp.size() + 2);
            pts.add(w.start());
            for (SplitPoint s : sp) pts.add(s.p);
            pts.add(w.end());
            int prev = vi.idOf(pts.get(0));
            for (int k = 1; k < pts.size(); k++) {
                int cur = vi.idOf(pts.get(k));
                if (cur != prev) {
                    long key = ((long) Math.min(prev, cur) << 32) | Math.max(prev, cur);
                    if (seenPairs.add(key)) g.edges.add(new Edge(g.edges.size(), prev, cur, w.id));
                }
                prev = cur;
            }
        }

        g.pruneDangling();
        g.walkFaces();
        return g;
    }

    private static void tryEndpoint(Vec2 p, Wall w, List<SplitPoint> into, double tol) {
        double len = w.lengthMm();
        double t = GeoUtils.projectT(p.x, p.y, w.startX, w.startY, w.endX, w.endY);
        double along = t * len;
        if (along <= tol || along >= len - tol) return;
        if (GeoUtils.distToSegment(p.x, p.y, w.startX, w.startY, w.endX, w.endY) > tol) return;
        addSplit(into, t, p, len, tol);
    }

    private static void addSplit(List<SplitPoint> into, double t, Vec2 p, double len, double tol) {
        double along = t * len;
        if (along <= tol || along >= len - tol) return;
        for (SplitPoint s : into) if (Math.abs(s.t - t) * len <= tol) return;
        into.add(new SplitPoint(t, p));
    }

    /** Hash-grid vertex merge: points closer than tol share one vertex. */
    private static final class VertexIndex {
        private final List<Vec2> vertices;
        private final double tol;
        private final Map<Long, List<Integer>> cells = new HashMap<>();

        VertexIndex(List<Vec2> vertices, double tol) { this.vertices = vertices; this.tol = tol; }

        private static long key(int cx, int cy) { return ((long) cx << 32) ^ (cy & 0xffffffffL); }

        int idOf(Vec2 p) {
            int cx = (int) Math.floor(p.x / tol), cy = (int) Math.floor(p.y / tol);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    List<Integer> l = cells.get(key(cx + dx, cy + dy));
                    if (l == null) continue;
                    for (int id : l) if (vertices.get(id).distanceTo(p) <= tol) return id;
                }
            }
            int id = vertices.size();
            vertices.add(p);
            cells.computeIfAbsent(key(cx, cy), k -> new ArrayList<>(2)).add(id);
            return id;
        }
    }

    //  3. prune dangling 

    private boolean[] alive;

    private void pruneDangling() {
        int ne = edges.size();
        alive = new boolean[ne];
        Arrays.fill(alive, true);
        int[] deg = new int[vertices.size()];
        for (Edge e : edges) { deg[e.a]++; deg[e.b]++; }
        List<List<Integer>> inc = new ArrayList<>();
        for (int i = 0; i < vertices.size(); i++) inc.add(new ArrayList<>(3));
        for (Edge e : edges) { inc.get(e.a).add(e.id); inc.get(e.b).add(e.id); }

        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
        for (int v = 0; v < deg.length; v++) if (deg[v] == 1) q.add(v);
        while (!q.isEmpty()) {
            int v = q.poll();
            if (deg[v] != 1) continue;
            for (int eid : inc.get(v)) {
                if (!alive[eid]) continue;
                alive[eid] = false;
                Edge e = edges.get(eid);
                int other = e.a == v ? e.b : e.a;
                deg[v]--;
                deg[other]--;
                if (deg[other] == 1) q.add(other);
            }
        }
    }

    //  4. face walk 

    private void walkFaces() {
        int nv = vertices.size();
        int ne = edges.size();
        List<List<Integer>> out = new ArrayList<>(nv);
        for (int i = 0; i < nv; i++) out.add(new ArrayList<>(3));
        for (Edge e : edges) {
            if (!alive[e.id]) continue;
            out.get(e.a).add(2 * e.id);       // a -> b
            out.get(e.b).add(2 * e.id + 1);   // b -> a
        }
        final double[] ang = new double[2 * ne];
        for (Edge e : edges) {
            if (!alive[e.id]) continue;
            Vec2 a = vertices.get(e.a), b = vertices.get(e.b);
            ang[2 * e.id] = Math.atan2(b.y - a.y, b.x - a.x);
            ang[2 * e.id + 1] = Math.atan2(a.y - b.y, a.x - b.x);
        }
        int[] pos = new int[2 * ne];
        for (int v = 0; v < nv; v++) {
            List<Integer> l = out.get(v);
            Collections.sort(l, Comparator.comparingDouble(h -> ang[h]));
            for (int k = 0; k < l.size(); k++) pos[l.get(k)] = k;
        }

        boolean[] visited = new boolean[2 * ne];
        for (Edge e0 : edges) {
            if (!alive[e0.id]) continue;
            for (int dir = 0; dir < 2; dir++) {
                int h0 = 2 * e0.id + dir;
                if (visited[h0]) continue;
                List<Vec2> poly = new ArrayList<>();
                List<Edge> es = new ArrayList<>();
                int h = h0;
                int guard = 0;
                while (!visited[h] && guard++ <= 2 * ne + 2) {
                    visited[h] = true;
                    Edge e = edges.get(h >> 1);
                    int origin = (h & 1) == 0 ? e.a : e.b;
                    int dest = (h & 1) == 0 ? e.b : e.a;
                    poly.add(vertices.get(origin));
                    es.add(e);
                    int twin = h ^ 1;
                    List<Integer> l = out.get(dest);
                    h = l.get((pos[twin] - 1 + l.size()) % l.size());
                }
                if (poly.size() < 3) continue;
                Set<Integer> ids = new HashSet<>();
                boolean bridge = false;
                for (Edge e : es) if (!ids.add(e.id)) { bridge = true; break; }
                faces.add(new Face(poly, es, GeoUtils.signedArea(poly), bridge));
            }
        }
    }
}
