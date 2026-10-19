package com.buildvisionai.planner.render;

/**
 * Level of detail: what is worth drawing at the current zoom. Zoomed out over a whole plot, text,
 * furniture and door swings are unreadable specks that still cost time on a low-end phone, so they
 * are skipped. All thresholds are in screen pixels (density-scaled), never in plan units, so the
 * plan looks the same on every phone at the same physical size.
 */
public final class Lod {
    private final double pxPerMm;
    private final double density;

    public Lod(double pxPerMm, double density) {
        this.pxPerMm = pxPerMm;
        this.density = Math.max(0.5, density);
    }

    private double dp(double v) { return v * density; }

    /** Screen length of a plan length. */
    public double px(double mm) { return mm * pxPerMm; }

    /** Walls never get thinner than this on screen, so a zoomed-out plan stays visible. */
    public float wallMinPx() { return (float) dp(1.5); }

    public boolean showFurniture() { return px(1000) >= dp(14); }          // a metre is at least 14 dp

    public boolean showOpeningDetail() { return px(900) >= dp(18); }       // door leaf and swing arc

    public boolean showRoomLabel(double roomShortSideMm) { return px(roomShortSideMm) >= dp(64); }

    public boolean showRoomArea(double roomShortSideMm) { return px(roomShortSideMm) >= dp(96); }

    public boolean showDimension(double wallLengthMm) { return px(wallLengthMm) >= dp(64); }

    public boolean showStairSteps() { return px(1000) >= dp(24); }

    /** Text size in px for plan labels, bounded so it stays legible but never swamps the drawing. */
    public float labelTextPx() { return (float) dp(12); }

    /** Upper bound on dimension labels per frame; each is a rotated text draw. */
    public int maxDimensionLabels() { return 120; }
}
