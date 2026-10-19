package com.buildvisionai.models;

/** A window attached to a wall; see {@link Door} for the attachment model. */
public final class Window extends PlanElement {
    public String wallId;
    public double offsetMm;
    public double widthMm;
    public double heightMm;
    public double sillMm;
    public WindowType windowType;

    public transient double worldX, worldY, angleDeg, wallThicknessMm;
    public transient boolean attached;

    public Window(String id, String floorId, String wallId, double offsetMm, WindowType windowType) {
        super(id, floorId);
        this.wallId = wallId;
        this.offsetMm = offsetMm;
        this.windowType = windowType;
        this.widthMm = windowType.defaultWidthMm;
        this.heightMm = windowType.defaultHeightMm;
        this.sillMm = windowType.defaultSillMm();
    }

    @Override public ElementType type() { return ElementType.WINDOW; }

    @Override public RectD bounds() {
        double r = widthMm / 2 + Math.max(wallThicknessMm, 100);
        return new RectD(worldX - r, worldY - r, worldX + r, worldY + r);
    }

    @Override public Window copy() {
        Window w = new Window(id, floorId, wallId, offsetMm, windowType);
        w.widthMm = widthMm;
        w.heightMm = heightMm;
        w.sillMm = sillMm;
        w.worldX = worldX; w.worldY = worldY; w.angleDeg = angleDeg;
        w.wallThicknessMm = wallThicknessMm; w.attached = attached;
        return w;
    }
}
