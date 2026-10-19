package com.buildvisionai.planner.render;

import com.buildvisionai.planner.ViewTransform;

/**
 * Grid line positions for the current view, in screen pixels, ready for {@code Canvas.drawLines}.
 * Reuses its buffers between frames so drawing the grid allocates nothing. Major lines are left out
 * of the minor buffer, so no line is painted twice (double alpha would make them look heavier than
 * intended), and the total is capped so a bad zoom value can never produce a million lines.
 */
public final class GridLayout {
    public static final int MAX_LINES_PER_AXIS = 400;

    public float[] minor = new float[0];
    public int minorCount;      // floats used, a multiple of 4: x0, y0, x1, y1
    public float[] major = new float[0];
    public int majorCount;

    /**
     * @param stepMm      minor spacing in mm
     * @param majorStepMm major spacing in mm, a multiple of {@code stepMm}; 0 for no major lines
     */
    public void compute(ViewTransform vt, double stepMm, double majorStepMm) {
        minorCount = 0;
        majorCount = 0;
        if (stepMm <= 0 || vt.viewW <= 0 || vt.viewH <= 0) return;
        double x0 = vt.toWorldX(0), x1 = vt.toWorldX(vt.viewW);
        double y0 = vt.toWorldY(0), y1 = vt.toWorldY(vt.viewH);
        long ix0 = (long) Math.ceil(x0 / stepMm), ix1 = (long) Math.floor(x1 / stepMm);
        long iy0 = (long) Math.ceil(y0 / stepMm), iy1 = (long) Math.floor(y1 / stepMm);
        if (ix1 - ix0 + 1 > MAX_LINES_PER_AXIS || iy1 - iy0 + 1 > MAX_LINES_PER_AXIS) return;
        long every = majorStepMm > 0 ? Math.max(1, Math.round(majorStepMm / stepMm)) : 0;

        int nMinor = 0, nMajor = 0;
        for (long i = ix0; i <= ix1; i++) { if (every > 0 && i % every == 0) nMajor++; else nMinor++; }
        for (long i = iy0; i <= iy1; i++) { if (every > 0 && i % every == 0) nMajor++; else nMinor++; }
        if (minor.length < nMinor * 4) minor = new float[nMinor * 4];
        if (major.length < nMajor * 4) major = new float[nMajor * 4];

        float w = vt.viewW, h = vt.viewH;
        for (long i = ix0; i <= ix1; i++) {
            float sx = (float) vt.toScreenX(i * stepMm);
            if (every > 0 && i % every == 0) { put(major, majorCount, sx, 0, sx, h); majorCount += 4; }
            else { put(minor, minorCount, sx, 0, sx, h); minorCount += 4; }
        }
        for (long i = iy0; i <= iy1; i++) {
            float sy = (float) vt.toScreenY(i * stepMm);
            if (every > 0 && i % every == 0) { put(major, majorCount, 0, sy, w, sy); majorCount += 4; }
            else { put(minor, minorCount, 0, sy, w, sy); minorCount += 4; }
        }
    }

    private static void put(float[] a, int at, float x0, float y0, float x1, float y1) {
        a[at] = x0; a[at + 1] = y0; a[at + 2] = x1; a[at + 3] = y1;
    }
}
