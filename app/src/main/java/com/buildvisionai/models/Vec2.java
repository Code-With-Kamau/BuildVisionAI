package com.buildvisionai.models;

/**
 * Immutable 2D point/vector in plan space.
 *
 * <p>Plan space: millimetres, x to the right, y DOWN (same as screen space), so angles measured
 * with {@code atan2(dy, dx)} increase clockwise on screen. Orientation words used in the geometry
 * package ("CCW", "inward") refer to the coordinate-space sign of the shoelace area, not to how the
 * polygon looks on a y-down screen.
 */
public final class Vec2 {
    public final double x;
    public final double y;

    public Vec2(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public Vec2 plus(Vec2 o) { return new Vec2(x + o.x, y + o.y); }
    public Vec2 minus(Vec2 o) { return new Vec2(x - o.x, y - o.y); }
    public Vec2 times(double k) { return new Vec2(x * k, y * k); }
    public double dot(Vec2 o) { return x * o.x + y * o.y; }
    public double cross(Vec2 o) { return x * o.y - y * o.x; }
    public double length() { return Math.hypot(x, y); }
    public double distanceTo(Vec2 o) { return Math.hypot(o.x - x, o.y - y); }

    public Vec2 normalized() {
        double l = length();
        return l < 1e-12 ? new Vec2(0, 0) : new Vec2(x / l, y / l);
    }

    /** 90 degrees counter-clockwise in coordinate space: (-y, x). */
    public Vec2 perp() { return new Vec2(-y, x); }

    public Vec2 rotate(double radians) {
        double c = Math.cos(radians), s = Math.sin(radians);
        return new Vec2(x * c - y * s, x * s + y * c);
    }

    public static Vec2 lerp(Vec2 a, Vec2 b, double t) {
        return new Vec2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vec2)) return false;
        Vec2 v = (Vec2) o;
        return Double.compare(x, v.x) == 0 && Double.compare(y, v.y) == 0;
    }

    @Override public int hashCode() { return 31 * Double.hashCode(x) + Double.hashCode(y); }

    @Override public String toString() { return "(" + x + ", " + y + ")"; }
}
