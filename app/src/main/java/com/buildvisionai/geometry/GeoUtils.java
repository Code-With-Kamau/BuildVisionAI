package com.buildvisionai.geometry;

import com.buildvisionai.models.Vec2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Small, allocation-light 2D helpers shared by the geometry engine and the editor (hit testing,
 * snapping). Everything is in plan space (mm, y down). Pure Java: no Android imports.
 */
public final class GeoUtils {
    public static final double EPS = 1e-9;

    private GeoUtils() {}

    /** Shoelace signed area in coordinate space (positive = counter-clockwise when y is up). */
    public static double signedArea(List<Vec2> poly) {
        double s = 0;
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            Vec2 a = poly.get(i), b = poly.get((i + 1) % n);
            s += a.x * b.y - b.x * a.y;
        }
        return s / 2.0;
    }

    public static double area(List<Vec2> poly) { return Math.abs(signedArea(poly)); }

    public static double perimeter(List<Vec2> poly) {
        double p = 0;
        int n = poly.size();
        for (int i = 0; i < n; i++) p += poly.get(i).distanceTo(poly.get((i + 1) % n));
        return p;
    }

    public static Vec2 centroid(List<Vec2> poly) {
        double a = signedArea(poly);
        int n = poly.size();
        if (Math.abs(a) < EPS || n == 0) {
            double sx = 0, sy = 0;
            for (Vec2 v : poly) { sx += v.x; sy += v.y; }
            return n == 0 ? new Vec2(0, 0) : new Vec2(sx / n, sy / n);
        }
        double cx = 0, cy = 0;
        for (int i = 0; i < n; i++) {
            Vec2 p = poly.get(i), q = poly.get((i + 1) % n);
            double k = p.x * q.y - q.x * p.y;
            cx += (p.x + q.x) * k;
            cy += (p.y + q.y) * k;
        }
        return new Vec2(cx / (6 * a), cy / (6 * a));
    }

    /** Even-odd ray cast. Points exactly on the boundary may go either way. */
    public static boolean pointInPolygon(double px, double py, List<Vec2> poly) {
        boolean inside = false;
        int n = poly.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Vec2 a = poly.get(i), b = poly.get(j);
            if ((a.y > py) != (b.y > py)) {
                double x = (b.x - a.x) * (py - a.y) / (b.y - a.y) + a.x;
                if (px < x) inside = !inside;
            }
        }
        return inside;
    }

    /** Parameter of the projection of p on segment a-b, unclamped (0 at a, 1 at b). */
    public static double projectT(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double l2 = dx * dx + dy * dy;
        if (l2 < EPS) return 0;
        return ((px - ax) * dx + (py - ay) * dy) / l2;
    }

    public static double distToSegment(double px, double py, double ax, double ay, double bx, double by) {
        double t = Math.max(0, Math.min(1, projectT(px, py, ax, ay, bx, by)));
        double cx = ax + (bx - ax) * t, cy = ay + (by - ay) * t;
        return Math.hypot(px - cx, py - cy);
    }

    /** Closest point on segment a-b to p. */
    public static Vec2 closestOnSegment(double px, double py, double ax, double ay, double bx, double by) {
        double t = Math.max(0, Math.min(1, projectT(px, py, ax, ay, bx, by)));
        return new Vec2(ax + (bx - ax) * t, ay + (by - ay) * t);
    }

    /**
     * Intersection of segments a-b and c-d. Returns {t, u} (parameters along ab and cd, both in
     * [0,1] within {@code tol} of the ends) or null when parallel / not touching.
     */
    public static double[] segmentIntersection(Vec2 a, Vec2 b, Vec2 c, Vec2 d, double tolParam) {
        Vec2 r = b.minus(a), s = d.minus(c);
        double denom = r.cross(s);
        if (Math.abs(denom) < EPS) return null;
        Vec2 ca = c.minus(a);
        double t = ca.cross(s) / denom;
        double u = ca.cross(r) / denom;
        if (t < -tolParam || t > 1 + tolParam || u < -tolParam || u > 1 + tolParam) return null;
        return new double[]{t, u};
    }

    /** Infinite-line intersection of p1+t*d1 and p2+u*d2, or null when parallel. */
    public static Vec2 lineIntersection(Vec2 p1, Vec2 d1, Vec2 p2, Vec2 d2) {
        double denom = d1.cross(d2);
        if (Math.abs(denom) < 1e-12) return null;
        double t = p2.minus(p1).cross(d2) / denom;
        return new Vec2(p1.x + d1.x * t, p1.y + d1.y * t);
    }

    /**
     * A point guaranteed to lie inside a simple polygon (centroid when it is inside, otherwise the
     * middle of the widest horizontal scanline span). Used as the room anchor.
     */
    public static Vec2 interiorPoint(List<Vec2> poly) {
        if (poly.size() < 3) return poly.isEmpty() ? new Vec2(0, 0) : poly.get(0);
        Vec2 c = centroid(poly);
        if (pointInPolygon(c.x, c.y, poly)) return c;

        List<Double> ys = new ArrayList<>();
        for (Vec2 v : poly) ys.add(v.y);
        Collections.sort(ys);
        double bestW = -1;
        Vec2 best = poly.get(0);
        for (int k = 0; k + 1 < ys.size(); k++) {
            double y0 = ys.get(k), y1 = ys.get(k + 1);
            if (y1 - y0 < 1e-6) continue;
            double y = (y0 + y1) / 2;
            List<Double> xs = new ArrayList<>();
            int n = poly.size();
            for (int i = 0, j = n - 1; i < n; j = i++) {
                Vec2 a = poly.get(i), b = poly.get(j);
                if ((a.y > y) != (b.y > y)) xs.add((b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x);
            }
            Collections.sort(xs);
            for (int i = 0; i + 1 < xs.size(); i += 2) {
                double w = xs.get(i + 1) - xs.get(i);
                if (w > bestW) {
                    bestW = w;
                    best = new Vec2((xs.get(i) + xs.get(i + 1)) / 2, y);
                }
            }
        }
        return best;
    }

    /** Snap a value to the nearest multiple of step. */
    public static double snap(double v, double step) {
        return step <= 0 ? v : Math.round(v / step) * step;
    }

    /** Normalises an angle in degrees to [0, 360). */
    public static double normDeg(double a) {
        a = a % 360.0;
        return a < 0 ? a + 360.0 : a;
    }

    /** Smallest signed difference b - a in degrees, in (-180, 180]. */
    public static double angleDiffDeg(double a, double b) {
        double d = normDeg(b - a);
        return d > 180 ? d - 360 : d;
    }
}
