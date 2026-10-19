package com.buildvisionai.models;

/**
 * Base for free-standing items that have a centre, a size and a rotation: columns, stairs and
 * furniture. Sharing this lets the editor move/resize/rotate/duplicate them with one code path.
 */
public abstract class PlacedElement extends PlanElement {
    public double x, y;
    public double widthMm, depthMm;
    /** Degrees, clockwise on screen. */
    public double rotationDeg;

    protected PlacedElement(String id, String floorId, double x, double y, double widthMm, double depthMm) {
        super(id, floorId);
        this.x = x;
        this.y = y;
        this.widthMm = widthMm;
        this.depthMm = depthMm;
    }

    /** True for shapes drawn as ellipses (circular columns). */
    public boolean isElliptical() { return false; }

    public Vec2 toLocal(double wx, double wy) {
        double a = Math.toRadians(-rotationDeg);
        double dx = wx - x, dy = wy - y;
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec2(dx * c - dy * s, dx * s + dy * c);
    }

    public Vec2 toWorld(double lx, double ly) {
        double a = Math.toRadians(rotationDeg);
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec2(x + lx * c - ly * s, y + lx * s + ly * c);
    }

    /** NW, NE, SE, SW corners of the rotated rectangle (in the element's own frame: NW = (-w/2,-d/2)). */
    public Vec2[] corners() {
        double hw = widthMm / 2, hd = depthMm / 2;
        return new Vec2[]{toWorld(-hw, -hd), toWorld(hw, -hd), toWorld(hw, hd), toWorld(-hw, hd)};
    }

    public boolean containsPoint(double wx, double wy, double slackMm) {
        Vec2 l = toLocal(wx, wy);
        double hw = widthMm / 2 + slackMm, hd = depthMm / 2 + slackMm;
        if (isElliptical()) {
            double nx = l.x / hw, ny = l.y / hd;
            return nx * nx + ny * ny <= 1.0;
        }
        return Math.abs(l.x) <= hw && Math.abs(l.y) <= hd;
    }

    @Override public RectD bounds() {
        Vec2[] c = corners();
        double minX = c[0].x, maxX = c[0].x, minY = c[0].y, maxY = c[0].y;
        for (int i = 1; i < 4; i++) {
            minX = Math.min(minX, c[i].x); maxX = Math.max(maxX, c[i].x);
            minY = Math.min(minY, c[i].y); maxY = Math.max(maxY, c[i].y);
        }
        return new RectD(minX, minY, maxX, maxY);
    }

    protected void copyPlacedInto(PlacedElement t) {
        t.rotationDeg = rotationDeg;
    }
}
