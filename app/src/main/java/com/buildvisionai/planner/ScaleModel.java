package com.buildvisionai.planner;

/**
 * Drawing-scale model. Plans are drawn in millimetres, and the view can show them at a true
 * architectural scale (1:50, 1:100 ...) for the device's physical pixel density, the way a paper
 * drawing is read. Also supplies the adaptive grid and scale bar so the picture stays readable at
 * every zoom level.
 */
public final class ScaleModel {
    private ScaleModel() {}

    /** Common architectural drawing scales (1:n). */
    public static final int[] STANDARD_RATIOS = {5, 10, 20, 25, 50, 100, 200, 500};

    /** Minor grid steps in mm. */
    public static final double[] GRID_STEPS = {10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000, 25000};
    private static final double[] MAJORS = {100, 250, 500, 1000, 2500, 5000, 10000, 25000, 50000, 100000, 250000};
    private static final double[] BAR = {1, 2, 5};

    /** Pixels per mm that make 1 mm on screen equal {@code ratio} mm of plan, for a screen dpi. */
    public static double pxPerMm(double dpi, int ratio) { return dpi / 25.4 / ratio; }

    /** Standard ratio closest (on a log scale) to the current zoom. */
    public static int nearestRatio(double pxPerMm, double dpi) {
        double best = Double.MAX_VALUE;
        int bestRatio = STANDARD_RATIOS[0];
        for (int r : STANDARD_RATIOS) {
            double d = Math.abs(Math.log(pxPerMm(dpi, r) / pxPerMm));
            if (d < best) { best = d; bestRatio = r; }
        }
        return bestRatio;
    }

    /** Effective "1:n" at the current zoom (not rounded). */
    public static double effectiveRatio(double pxPerMm, double dpi) { return dpi / 25.4 / pxPerMm; }

    public static String label(int ratio) { return "1:" + ratio; }

    /** Smallest nice grid step whose on-screen spacing is at least {@code minPx}. */
    public static double gridStepMm(double pxPerMm, double minPx) {
        for (double s : GRID_STEPS) if (s * pxPerMm >= minPx) return s;
        return GRID_STEPS[GRID_STEPS.length - 1];
    }

    /** Major-line spacing for a minor step: a round number, a multiple of the step, at least 4 steps. */
    public static double majorStepMm(double stepMm) {
        for (double m : MAJORS) if (m >= 4 * stepMm - 1e-9 && Math.abs(m / stepMm - Math.round(m / stepMm)) < 1e-9) return m;
        return stepMm * 5;
    }

    /** Length in mm (1/2/5 x 10^k) of a scale bar close to {@code targetPx} wide. */
    public static double scaleBarMm(double pxPerMm, double targetPx) {
        double targetMm = targetPx / pxPerMm;
        double pow = Math.pow(10, Math.floor(Math.log10(targetMm)));
        double best = pow;
        for (double mult : new double[]{1, 2, 5, 10}) {
            double c = pow * mult;
            if (c <= targetMm * 1.0001) best = c;
        }
        return best;
    }
}
