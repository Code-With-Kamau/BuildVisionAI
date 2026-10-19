package com.buildvisionai.geometry;

import com.buildvisionai.models.Vec2;

import java.util.ArrayList;
import java.util.List;

/** Polygon utilities: per-edge offsetting, simplicity test, oriented extents. */
public final class PolygonOps {
    private PolygonOps() {}

    /**
     * Offsets every edge of a polygon by its own distance and re-joins the offset lines with mitre
     * joints. The polygon must be positively oriented (coordinate-space shoelace area &gt; 0); for
     * such polygons a positive distance moves an edge towards the inside, a negative one outward.
     *
     * <p>{@code distances[i]} applies to the edge from vertex i to vertex i+1. Consecutive parallel
     * edges are handled (the shared vertex is simply moved along the average normal). The result may
     * be self-intersecting for large distances; callers must check {@link #isSimple(List)}.
     */
    public static List<Vec2> offsetEdges(List<Vec2> poly, double[] distances) {
        int n = poly.size();
        Vec2[] dir = new Vec2[n];
        Vec2[] nrm = new Vec2[n];
        for (int i = 0; i < n; i++) {
            Vec2 a = poly.get(i), b = poly.get((i + 1) % n);
            Vec2 d = b.minus(a).normalized();
            dir[i] = d;
            nrm[i] = d.perp(); // left normal = inside for positive polygons
        }
        List<Vec2> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int p = (i + n - 1) % n;          // previous edge ends at vertex i
            Vec2 v = poly.get(i);
            Vec2 pp = v.plus(nrm[p].times(distances[p]));
            Vec2 pi = v.plus(nrm[i].times(distances[i]));
            Vec2 hit = GeoUtils.lineIntersection(pp, dir[p], pi, dir[i]);
            if (hit == null) {
                // parallel (collinear) neighbours: same offset -> one point; different offsets
                // (e.g. a thick wall continuing as a thin one) -> a clean step of two points
                if (Math.abs(distances[p] - distances[i]) < 1e-6) {
                    out.add(pi);
                } else {
                    out.add(pp);
                    out.add(pi);
                }
                continue;
            } else {
                // clamp absurd spikes at very sharp corners
                double maxD = Math.max(Math.abs(distances[p]), Math.abs(distances[i]));
                double spike = hit.distanceTo(v);
                if (maxD > 0 && spike > 8 * maxD) {
                    Vec2 m = nrm[p].plus(nrm[i]).normalized();
                    hit = v.plus(m.times(maxD));
                }
            }
            out.add(hit);
        }
        return out;
    }

    public static List<Vec2> offsetUniform(List<Vec2> poly, double distance) {
        double[] d = new double[poly.size()];
        java.util.Arrays.fill(d, distance);
        return offsetEdges(poly, d);
    }

    /** True when no two non-adjacent edges cross. O(n^2); polygons here have tens of vertices. */
    public static boolean isSimple(List<Vec2> poly) {
        int n = poly.size();
        if (n < 3) return false;
        for (int i = 0; i < n; i++) {
            Vec2 a = poly.get(i), b = poly.get((i + 1) % n);
            for (int j = i + 1; j < n; j++) {
                if (j == i + 1 || (i == 0 && j == n - 1)) continue;
                Vec2 c = poly.get(j), d = poly.get((j + 1) % n);
                if (GeoUtils.segmentIntersection(a, b, c, d, 0) != null) return false;
            }
        }
        return true;
    }

    /** Removes vertices that lie on the straight line between their neighbours. */
    public static List<Vec2> removeCollinear(List<Vec2> poly, double tolMm) {
        List<Vec2> cur = new ArrayList<>(poly);
        boolean changed = true;
        while (changed && cur.size() > 3) {
            changed = false;
            for (int i = 0; i < cur.size(); i++) {
                Vec2 a = cur.get((i + cur.size() - 1) % cur.size());
                Vec2 b = cur.get(i);
                Vec2 c = cur.get((i + 1) % cur.size());
                if (GeoUtils.distToSegment(b.x, b.y, a.x, a.y, c.x, c.y) <= tolMm) {
                    cur.remove(i);
                    changed = true;
                    break;
                }
            }
        }
        return cur;
    }

    /**
     * Width and length of the minimum-area oriented bounding rectangle (rotating-calipers style by
     * trying every edge direction). Returns {short, long} in the polygon's units.
     */
    public static double[] orientedExtents(List<Vec2> poly) {
        int n = poly.size();
        if (n < 2) return new double[]{0, 0};
        double bestArea = Double.MAX_VALUE;
        double bw = 0, bh = 0;
        for (int i = 0; i < n; i++) {
            Vec2 a = poly.get(i), b = poly.get((i + 1) % n);
            Vec2 d = b.minus(a).normalized();
            if (d.length() < 1e-12) continue;
            Vec2 nn = d.perp();
            double minU = Double.MAX_VALUE, maxU = -Double.MAX_VALUE, minV = Double.MAX_VALUE, maxV = -Double.MAX_VALUE;
            for (Vec2 p : poly) {
                double u = p.dot(d), v = p.dot(nn);
                minU = Math.min(minU, u); maxU = Math.max(maxU, u);
                minV = Math.min(minV, v); maxV = Math.max(maxV, v);
            }
            double w = maxU - minU, h = maxV - minV;
            if (w * h < bestArea - 1e-9) {
                bestArea = w * h;
                bw = w;
                bh = h;
            }
        }
        return new double[]{Math.min(bw, bh), Math.max(bw, bh)};
    }

    /** Reverses the list in place copy so that the shoelace area becomes positive. */
    public static List<Vec2> ensurePositive(List<Vec2> poly) {
        if (GeoUtils.signedArea(poly) >= 0) return poly;
        List<Vec2> r = new ArrayList<>(poly);
        java.util.Collections.reverse(r);
        return r;
    }
}
