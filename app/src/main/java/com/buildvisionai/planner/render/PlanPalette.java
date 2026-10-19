package com.buildvisionai.planner.render;

/**
 * Colours of the drawing canvas, as ARGB ints so this class has no Android dependency and its
 * contrast can be unit-tested. Light and dark variants; the plan view picks one from the system
 * night mode. Wall, text and selection colours are held to WCAG contrast ratios against the canvas
 * (checked in {@code PlanPaletteTest}) because a floor plan is read outdoors, on site, in bright light.
 */
public final class PlanPalette {
    public final int background;
    public final int gridMinor, gridMajor;
    public final int wall;
    public final int roomFillAlpha;          // 0-255, applied to RoomType.fillColor
    public final int roomOutline;
    public final int doorLeaf, windowFrame, windowGlass;
    public final int furnitureFill, furnitureStroke;
    public final int columnFill, stairStroke;
    public final int selection, handleFill;
    public final int previewFill;
    public final int snapMarker, snapGuide;
    public final int dimensionText, labelText, scaleBar;

    private PlanPalette(int background, int gridMinor, int gridMajor, int wall, int roomFillAlpha, int roomOutline,
                        int doorLeaf, int windowFrame, int windowGlass, int furnitureFill, int furnitureStroke,
                        int columnFill, int stairStroke, int selection, int handleFill, int previewFill,
                        int snapMarker, int snapGuide, int dimensionText, int labelText, int scaleBar) {
        this.background = background; this.gridMinor = gridMinor; this.gridMajor = gridMajor; this.wall = wall;
        this.roomFillAlpha = roomFillAlpha; this.roomOutline = roomOutline; this.doorLeaf = doorLeaf;
        this.windowFrame = windowFrame; this.windowGlass = windowGlass; this.furnitureFill = furnitureFill;
        this.furnitureStroke = furnitureStroke; this.columnFill = columnFill; this.stairStroke = stairStroke;
        this.selection = selection; this.handleFill = handleFill; this.previewFill = previewFill;
        this.snapMarker = snapMarker; this.snapGuide = snapGuide; this.dimensionText = dimensionText;
        this.labelText = labelText; this.scaleBar = scaleBar;
    }

    public static PlanPalette light() {
        return new PlanPalette(
                0xFFFAFAF7, 0xFFEAEAE4, 0xFFD2D2CA, 0xFF2B2B2E, 120, 0xFF8A8A90,
                0xFF3A3A3F, 0xFF3A3A3F, 0xFFBFE3F5, 0xFFF1EFE8, 0xFF6B6B72,
                0xFF4A4A50, 0xFF6B6B72, 0xFF0B5FBF, 0xFFFFFFFF, 0x660B5FBF,
                0xFFC2410C, 0xFFC2410C, 0xFF4A4A52, 0xFF1F1F23, 0xFF2B2B2E);
    }

    public static PlanPalette dark() {
        return new PlanPalette(
                0xFF121214, 0xFF1E1E22, 0xFF2E2E34, 0xFFE8E8EA, 80, 0xFF8A8A92,
                0xFFD0D0D6, 0xFFD0D0D6, 0xFF2C4B5C, 0xFF26262B, 0xFF9A9AA2,
                0xFFB8B8C0, 0xFF9A9AA2, 0xFF7DB8FF, 0xFF121214, 0x667DB8FF,
                0xFFFF9466, 0xFFFF9466, 0xFFB8B8C2, 0xFFEDEDF0, 0xFFE8E8EA);
    }

    // ---- contrast (WCAG 2.x relative luminance) ---------------------------------------------------

    private static double channel(int c) {
        double v = c / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    public static double luminance(int argb) {
        return 0.2126 * channel((argb >> 16) & 0xFF) + 0.7152 * channel((argb >> 8) & 0xFF) + 0.0722 * channel(argb & 0xFF);
    }

    /** Contrast ratio of two opaque colours, 1 (identical) to 21 (black on white). */
    public static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        double hi = Math.max(la, lb), lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }
}
