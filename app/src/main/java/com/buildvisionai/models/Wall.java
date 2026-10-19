package com.buildvisionai.models;

/**
 * A straight wall defined by its centreline. {@code length} and {@code rotation} are derived, never
 * stored, so they can never drift out of sync with the endpoints.
 */
public final class Wall extends PlanElement {
    public double startX, startY, endX, endY;
    public double thicknessMm;
    public double heightMm = 2700;
    public WallKind kind;
    public WallMaterial material;

    public Wall(String id, String floorId, double startX, double startY, double endX, double endY, WallKind kind) {
        super(id, floorId);
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.kind = kind;
        this.material = kind.defaultMaterial;
        this.thicknessMm = kind.defaultThicknessMm();
    }

    public double lengthMm() { return Math.hypot(endX - startX, endY - startY); }

    /** Direction of the wall in degrees [0,360), clockwise on screen (plan y points down). */
    public double rotationDeg() {
        double a = Math.toDegrees(Math.atan2(endY - startY, endX - startX));
        return a < 0 ? a + 360 : a;
    }

    public double midX() { return (startX + endX) / 2; }
    public double midY() { return (startY + endY) / 2; }

    /** Unit direction vector start-to-end ((1,0) for a zero-length wall). */
    public Vec2 direction() {
        double l = lengthMm();
        return l < 1e-9 ? new Vec2(1, 0) : new Vec2((endX - startX) / l, (endY - startY) / l);
    }

    /** Point on the centreline {@code offsetMm} from the start. */
    public Vec2 pointAt(double offsetMm) {
        Vec2 d = direction();
        return new Vec2(startX + d.x * offsetMm, startY + d.y * offsetMm);
    }

    public Vec2 start() { return new Vec2(startX, startY); }
    public Vec2 end() { return new Vec2(endX, endY); }

    @Override public ElementType type() { return ElementType.WALL; }

    @Override public RectD bounds() {
        return new RectD(startX, startY, endX, endY).inflate(thicknessMm / 2);
    }

    @Override public Wall copy() {
        Wall w = new Wall(id, floorId, startX, startY, endX, endY, kind);
        w.thicknessMm = thicknessMm;
        w.heightMm = heightMm;
        w.material = material;
        return w;
    }
}
