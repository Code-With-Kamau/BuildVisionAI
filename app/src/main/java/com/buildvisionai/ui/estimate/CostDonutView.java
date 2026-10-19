package com.buildvisionai.ui.estimate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.buildvisionai.R;
import com.buildvisionai.estimation.CostBreakdown;
import com.google.android.material.color.MaterialColors;

import java.util.Collections;
import java.util.List;

import android.view.View;

/**
 * A donut chart of the cost split. Colour is never the only channel: slices are separated by gaps,
 * the legend beside the chart gives every name, amount and percentage, and TalkBack reads the same
 * numbers from the content description. The arc maths comes from {@link CostBreakdown} this class only paints.
 */
public final class CostDonutView extends View {
    private static final int[] CHART_COLORS = {
            R.color.bv_chart_1, R.color.bv_chart_2, R.color.bv_chart_3, R.color.bv_chart_4,
            R.color.bv_chart_5, R.color.bv_chart_6, R.color.bv_chart_7};

    /** The colour resource for a category's slice; also used by the legend swatches. */
    public static int colorRes(int colorIndex) { return CHART_COLORS[colorIndex % CHART_COLORS.length]; }

    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint topText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint bottomText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final float density;

    private List<CostBreakdown.Slice> slices = Collections.emptyList();
    private double[][] arcs = new double[0][];
    private String centerTop = "";
    private String centerBottom = "";

    public CostDonutView(Context context) { this(context, null); }

    public CostDonutView(Context context, @Nullable AttributeSet attrs) { this(context, attrs, 0); }

    public CostDonutView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.BUTT);
        topText.setTextAlign(Paint.Align.CENTER);
        bottomText.setTextAlign(Paint.Align.CENTER);
        topText.setTextSize(sp(13));
        bottomText.setTextSize(sp(20));
        bottomText.setFakeBoldText(true);
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }

    /** Shows the chart for these slices with two lines of text in the hole (a caption and the total). */
    public void setData(List<CostBreakdown.Slice> newSlices, String caption, String total) {
        slices = newSlices;
        arcs = CostBreakdown.arcs(newSlices, 2.0);
        centerTop = caption;
        centerBottom = total;
        setContentDescription(CostBreakdown.describe(newSlices));
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int max = (int) (280 * density);
        int w = View.MeasureSpec.getSize(widthSpec);
        int size = View.MeasureSpec.getMode(widthSpec) == View.MeasureSpec.UNSPECIFIED ? max : Math.min(w, max);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float stroke = 30 * density;
        float half = stroke / 2f;
        float size = Math.min(getWidth(), getHeight());
        float left = (getWidth() - size) / 2f + half;
        float top = (getHeight() - size) / 2f + half;
        oval.set(left, top, left + size - stroke, top + size - stroke);
        arc.setStrokeWidth(stroke);

        for (int i = 0; i < slices.size() && i < arcs.length; i++) {
            arc.setColor(ContextCompat.getColor(getContext(), colorRes(slices.get(i).colorIndex())));
            // 0 degrees is 12 o'clock here; Canvas measures from 3 o'clock
            canvas.drawArc(oval, (float) arcs[i][0] - 90f, (float) arcs[i][1], false, arc);
        }

        int onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface);
        int onSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant);
        topText.setColor(onSurfaceVariant);
        bottomText.setColor(onSurface);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        canvas.drawText(centerTop, cx, cy - 4 * density, topText);
        canvas.drawText(centerBottom, cx, cy + 22 * density, bottomText);
    }
}
