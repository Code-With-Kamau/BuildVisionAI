package com.buildvisionai.planner;

import com.buildvisionai.models.RectD;

/**
 * Mapping between plan space (mm) and screen space (px): {@code screen = world * scale + t}.
 * Pure arithmetic with no Android types, so zoom/pan maths is unit-testable. The view turns this
 * into a {@code Matrix} for drawing and uses it directly for touch conversion.
 */
public final class ViewTransform {
    /** Zoom limits in px per mm: 1 px = 250 mm .. 1 px = 0.33 mm. */
    public static final double MIN_SCALE = 0.004;
    public static final double MAX_SCALE = 3.0;

    public double scale = 0.1;
    public double tx, ty;
    public int viewW, viewH;
    /** True once {@link #fit} has framed the plan; the screen then keeps the person's zoom and pan when it is rebuilt. */
    public boolean fitted;

    public double toScreenX(double wx) { return wx * scale + tx; }
    public double toScreenY(double wy) { return wy * scale + ty; }
    public double toWorldX(double sx) { return (sx - tx) / scale; }
    public double toWorldY(double sy) { return (sy - ty) / scale; }

    /** Length of {@code px} screen pixels in plan millimetres. */
    public double pxToMm(double px) { return px / scale; }

    public void setViewSize(int w, int h) { viewW = w; viewH = h; }

    /**
     * The view changed size (a rotation, or a new view for the same editor). Once the plan has been framed, the point at
     * the middle of the old view stays at the middle of the new one, so the person keeps their place instead of being thrown
     * back to "fit all".
     */
    public void resize(int w, int h) {
        if (fitted && viewW > 0 && viewH > 0 && w > 0 && h > 0) {
            tx += (w - viewW) / 2.0;
            ty += (h - viewH) / 2.0;
        }
        viewW = w;
        viewH = h;
    }

    public void panBy(double dxPx, double dyPx) { tx += dxPx; ty += dyPx; }

    /** Zooms by {@code factor} keeping the plan point under (fx, fy) fixed; clamps to limits. */
    public void zoomAbout(double fx, double fy, double factor) {
        double ns = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * factor));
        double real = ns / scale;
        tx = fx - (fx - tx) * real;
        ty = fy - (fy - ty) * real;
        scale = ns;
    }

    public void setScaleAbout(double fx, double fy, double newScale) { zoomAbout(fx, fy, newScale / scale); }

    /** Centres and scales so that {@code r} fills the view with the given padding. */
    public void fit(RectD r, double paddingPx) {
        if (r == null || viewW <= 0 || viewH <= 0) return;
        double w = Math.max(r.width(), 1000), h = Math.max(r.height(), 1000);
        double sx = (viewW - 2 * paddingPx) / w, sy = (viewH - 2 * paddingPx) / h;
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, Math.min(sx, sy)));
        tx = viewW / 2.0 - r.centerX() * scale;
        ty = viewH / 2.0 - r.centerY() * scale;
        fitted = true;
    }

    public RectD visibleWorldRect() {
        return new RectD(toWorldX(0), toWorldY(0), toWorldX(viewW), toWorldY(viewH));
    }

    public ViewTransform copy() {
        ViewTransform v = new ViewTransform();
        v.scale = scale; v.tx = tx; v.ty = ty; v.viewW = viewW; v.viewH = viewH; v.fitted = fitted;
        return v;
    }
}
