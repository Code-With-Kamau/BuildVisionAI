package com.buildvisionai.planner.render;

/** Where and at what angle plan text goes. */
public final class TextPlacement {
    private TextPlacement() {}

    /**
     * Rotation for text that runs along a line at {@code lineDeg} (screen degrees, clockwise): the
     * same line direction, but flipped by 180 degrees when needed so the text is never upside down.
     * Result is in (-90, 90].
     */
    public static double uprightDeg(double lineDeg) {
        double a = lineDeg % 360.0;
        if (a < 0) a += 360.0;
        if (a > 90 && a <= 270) a -= 180;
        else if (a > 270) a -= 360;
        return a;
    }
}
