package com.buildvisionai.ui.editor;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.buildvisionai.planner.EditorController;
import com.buildvisionai.planner.EditorListener;
import com.buildvisionai.planner.ScaleModel;
import com.buildvisionai.planner.ViewTransform;
import com.buildvisionai.planner.render.PlanPalette;

/**
 * The floor-plan canvas. A custom View (no Compose): it forwards touch input to the
 * {@link EditorController} in screen pixels and draws the controller's current floor with
 * {@link FloorPlanRenderer}. It holds no plan data of its own.
 *
 * <p>Gestures: a one-finger drag goes to the active tool when the tool takes it (moving a wall,
 * dragging a handle, drawing) and otherwise pans the view, with a fling; two fingers pinch-zoom about
 * the midpoint and pan together; a mouse wheel zooms about the pointer. Starting a pinch cancels a tool
 * drag in progress, and the finger left on the screen afterwards does nothing until it lifts, so a
 * pinch never leaves a stray edit behind.
 *
 */
public class FloorPlanView extends View {
    private enum Mode { IDLE, UNDECIDED, TOOL, PAN, PINCH, IGNORE }

    private static final double WHEEL_ZOOM_PER_NOTCH = 1.15;

    private final FloorPlanRenderer renderer = new FloorPlanRenderer();
    private final GestureDetector gestures;
    private final ScaleGestureDetector scaler;
    private final OverScroller scroller;

    private PlanPalette palette = PlanPalette.light();
    private float density = 1f;
    private float dpi = 160f;

    private EditorController controller;
    private EditorListener delegate;
    private boolean fitPending;

    private Mode mode = Mode.IDLE;
    private float lastFocusX, lastFocusY;
    private int flingX, flingY;

    public FloorPlanView(Context context) { this(context, null); }

    public FloorPlanView(Context context, @Nullable AttributeSet attrs) { this(context, attrs, 0); }

    @SuppressWarnings("this-escape")
    public FloorPlanView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        density = dm.density;
        dpi = dm.xdpi > 40f ? dm.xdpi : dm.densityDpi;
        gestures = new GestureDetector(context, new GestureHandler());
        scaler = new ScaleGestureDetector(context, new PinchHandler());
        scaler.setQuickScaleEnabled(false);       // double-tap-and-drag would fight the drawing tools
        scaler.setStylusScaleEnabled(false);
        scroller = new OverScroller(context);
        setFocusable(true);
        refreshPalette();
    }

    // wiring 

    /**
     * Connects the view to an editor. The view redraws on every editor invalidation and forwards all
     * editor events to {@code listener}, which the screen uses for autosave, toolbars and messages.
     */
    public void attach(@NonNull EditorController c, @Nullable EditorListener listener) {
        controller = c;
        delegate = listener;
        c.setDensity(density);
        c.setListener(new EditorListener() {
            @Override public void onInvalidate() {
                postInvalidateOnAnimation();
                if (delegate != null) delegate.onInvalidate();
            }
            @Override public void onDocumentChanged() { if (delegate != null) delegate.onDocumentChanged(); }
            @Override public void onSelectionChanged() { if (delegate != null) delegate.onSelectionChanged(); }
            @Override public void onToolChanged() { if (delegate != null) delegate.onToolChanged(); }
            @Override public void onMessage(String text) { if (delegate != null) delegate.onMessage(text); }
            @Override public void onRoomEditRequested(String roomId) { if (delegate != null) delegate.onRoomEditRequested(roomId); }
        });
        c.view().resize(getWidth(), getHeight());
        // Frame the plan the first time only. The controller (and its zoom and pan) outlives this view, so coming back from
        // another screen or rotating keeps where the person was working.
        fitPending = !c.view().fitted;
        if (fitPending && getWidth() > 0 && getHeight() > 0) {
            fitPending = false;
            c.fitToContent();
        }
        invalidate();
    }

    /**
     * Disconnects from the editor. The controller lives in a ViewModel and outlives this view, so call
     * this from the fragment's onDestroyView; otherwise the controller keeps the old view alive.
     */
    public void detach() {
        scroller.abortAnimation();
        mode = Mode.IDLE;
        if (controller != null) {
            controller.onDragCancel();
            controller.setListener(null);
        }
        controller = null;
        delegate = null;
    }

    public void invalidatePlan() { postInvalidateOnAnimation(); }

    /** A spoken summary for TalkBack, such as "Ground floor, 7 rooms, 124 square metres". */
    public void setPlanDescription(@Nullable CharSequence description) { setContentDescription(description); }

    //  view lifecycle
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (controller == null) return;
        controller.view().resize(w, h);
        if (fitPending && w > 0 && h > 0) {
            fitPending = false;
            controller.fitToContent();
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        refreshPalette();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        scroller.abortAnimation();
        if (controller != null) controller.onDragCancel();
        super.onDetachedFromWindow();
    }

    private void refreshPalette() {
        int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        palette = night == Configuration.UI_MODE_NIGHT_YES ? PlanPalette.dark() : PlanPalette.light();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (controller == null) {
            canvas.drawColor(palette.background);
            return;
        }
        renderer.draw(canvas, controller, palette, density, dpi);
    }

    // zoom helpers (toolbar buttons, drawing-scale picker)
    public void zoomIn() { zoomBy(1.25); }

    public void zoomOut() { zoomBy(0.8); }

    private void zoomBy(double factor) {
        if (controller == null) return;
        controller.view().zoomAbout(getWidth() / 2.0, getHeight() / 2.0, factor);
        invalidate();
    }

    public void fitToContent() {
        if (controller != null) controller.fitToContent();
    }

    /**
     * Sets the view to a standard drawing scale such as 1:50, using the screen's physical pixel
     * density, so a length measured on the screen with a ruler is the plan length divided by the
     * ratio. Phone-reported density is only approximate, so the on-screen label says "about" unless
     * the scale is within 4% of a standard ratio.
     */
    public void setDrawingScale(int ratio) {
        if (controller == null || ratio <= 0) return;
        controller.view().setScaleAbout(getWidth() / 2.0, getHeight() / 2.0, ScaleModel.pxPerMm(dpi, ratio));
        invalidate();
    }

    /** The standard ratio nearest to the current zoom, for the toolbar's scale label. */
    public int drawingScaleRatio() {
        return controller == null ? ScaleModel.STANDARD_RATIOS[0] : ScaleModel.nearestRatio(controller.view().scale, dpi);
    }

    // touch 
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (controller == null) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                scroller.forceFinished(true);
                mode = Mode.UNDECIDED;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (ev.getPointerCount() == 2 && mode == Mode.TOOL) controller.onDragCancel();
                break;
            default:
                break;
        }
        scaler.onTouchEvent(ev);
        if (!scaler.isInProgress()) gestures.onTouchEvent(ev);

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_UP:
                if (mode == Mode.TOOL) controller.onDragEnd();
                mode = Mode.IDLE;
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mode == Mode.TOOL) controller.onDragCancel();
                mode = Mode.IDLE;
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent ev) {
        if (controller != null && ev.getAction() == MotionEvent.ACTION_SCROLL
                && ev.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) {
            float notches = ev.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (notches != 0f) {
                controller.view().zoomAbout(ev.getX(), ev.getY(), Math.pow(WHEEL_ZOOM_PER_NOTCH, notches));
                invalidate();
                return true;
            }
        }
        return super.onGenericMotionEvent(ev);
    }

    @Override
    public void computeScroll() {
        if (controller != null && scroller.computeScrollOffset()) {
            int x = scroller.getCurrX(), y = scroller.getCurrY();
            controller.view().panBy(x - flingX, y - flingY);
            flingX = x;
            flingY = y;
            postInvalidateOnAnimation();
        }
    }

    private final class GestureHandler extends GestureDetector.SimpleOnGestureListener {
        @Override public boolean onDown(MotionEvent e) { return true; }

        @Override public boolean onSingleTapUp(MotionEvent e) {
            if (mode == Mode.IGNORE || mode == Mode.PINCH) return false;
            performClick();
            return controller.onTap(e.getX(), e.getY());
        }

        @Override public void onLongPress(MotionEvent e) {
            if (mode == Mode.UNDECIDED) controller.onLongPress(e.getX(), e.getY());
        }

        @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
            if (e1 == null || mode == Mode.IGNORE || mode == Mode.PINCH) return false;
            if (mode == Mode.UNDECIDED) {
                // grab where the finger first touched, not where it was when the touch slop was crossed
                mode = controller.onDragStart(e1.getX(), e1.getY()) ? Mode.TOOL : Mode.PAN;
            }
            if (mode == Mode.TOOL) {
                controller.onDrag(e2.getX(), e2.getY());
            } else if (mode == Mode.PAN) {
                controller.view().panBy(-dx, -dy);
                invalidate();
            }
            return true;
        }

        @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
            if (mode != Mode.PAN) return false;
            flingX = 0;
            flingY = 0;
            scroller.fling(0, 0, (int) vx, (int) vy, Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2,
                    Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2);
            postInvalidateOnAnimation();
            return true;
        }
    }

    private final class PinchHandler extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override public boolean onScaleBegin(ScaleGestureDetector d) {
            if (mode == Mode.TOOL) controller.onDragCancel();
            scroller.forceFinished(true);
            mode = Mode.PINCH;
            lastFocusX = d.getFocusX();
            lastFocusY = d.getFocusY();
            return true;
        }

        @Override public boolean onScale(ScaleGestureDetector d) {
            ViewTransform vt = controller.view();
            vt.zoomAbout(d.getFocusX(), d.getFocusY(), d.getScaleFactor());
            vt.panBy(d.getFocusX() - lastFocusX, d.getFocusY() - lastFocusY);
            lastFocusX = d.getFocusX();
            lastFocusY = d.getFocusY();
            invalidate();
            return true;
        }

        @Override public void onScaleEnd(ScaleGestureDetector d) {
            mode = Mode.IGNORE;      // the remaining finger must not start a pan or a tool drag
        }
    }
}
