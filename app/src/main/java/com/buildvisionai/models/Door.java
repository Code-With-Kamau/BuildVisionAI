package com.buildvisionai.models;

/**
 * A door attached to a wall. Position is stored relative to the wall ({@code offsetMm} from the
 * wall start to the door centre), so the door follows its wall automatically when the wall moves.
 * The {@code world*} fields are resolved by {@code FloorPlan.resolveOpenings()} for drawing and
 * hit-testing.
 */
public final class Door extends PlanElement {
    public String wallId;
    public double offsetMm;
    public double widthMm;
    public double heightMm;
    public DoorType doorType;
    public SwingDirection swing = SwingDirection.LEFT_IN;

    public transient double worldX, worldY, angleDeg, wallThicknessMm;
    public transient boolean attached;

    public Door(String id, String floorId, String wallId, double offsetMm, DoorType doorType) {
        super(id, floorId);
        this.wallId = wallId;
        this.offsetMm = offsetMm;
        this.doorType = doorType;
        this.widthMm = doorType.defaultWidthMm;
        this.heightMm = doorType.defaultHeightMm();
    }

    @Override public ElementType type() { return ElementType.DOOR; }

    @Override public RectD bounds() {
        double r = widthMm / 2 + Math.max(wallThicknessMm, 100);
        return new RectD(worldX - r, worldY - r, worldX + r, worldY + r);
    }

    @Override public Door copy() {
        Door d = new Door(id, floorId, wallId, offsetMm, doorType);
        d.widthMm = widthMm;
        d.heightMm = heightMm;
        d.swing = swing;
        d.worldX = worldX; d.worldY = worldY; d.angleDeg = angleDeg;
        d.wallThicknessMm = wallThicknessMm; d.attached = attached;
        return d;
    }
}
