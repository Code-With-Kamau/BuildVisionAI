package com.buildvisionai.estimation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The numbers behind the cost donut chart, free of Android so they are unit-tested: which
 * categories get a slice, their shares, the arc angles (with a small gap between slices so the
 * chart does not rely on colour alone) and the spoken description for TalkBack.
 *
 * <p>Shares are of the construction subtotal (the sum of the category averages); the percentage
 * add-ons (preliminaries, overhead and profit, contingency, fees) are listed beside the chart, not in it.
 */
public final class CostBreakdown {
    private CostBreakdown() {}

    public static final class Slice {
        public final CostCategory category;
        public final double min, avg, max;
        /** 0..1 of the subtotal. */
        public final double share;

        Slice(CostCategory category, double min, double avg, double max, double share) {
            this.category = category;
            this.min = min;
            this.avg = avg;
            this.max = max;
            this.share = share;
        }

        /** Index of this category's chart colour (bv_chart_1 is index 0), stable even when other slices are absent. */
        public int colorIndex() { return category.ordinal(); }
    }

    /** One slice per category with a positive average cost, in {@link CostCategory} order. */
    public static List<Slice> slices(CostEstimate e) {
        double sum = 0;
        for (CostCategory c : CostCategory.values()) sum += Math.max(0, e.category(c).avg);
        List<Slice> out = new ArrayList<>();
        if (sum <= 0) return out;
        for (CostCategory c : CostCategory.values()) {
            CategoryCost cc = e.category(c);
            if (cc.avg > 0) out.add(new Slice(c, cc.min, cc.avg, cc.max, cc.avg / sum));
        }
        return out;
    }

    /**
     * Start angle and sweep (degrees, 0 = 12 o'clock, clockwise) for each slice, leaving
     * {@code gapDeg} between neighbours. A single slice is a full circle with no gap. A gap that
     * would swallow a slice is reduced for the whole chart so every slice stays visible.
     *
     * @return an array of {start, sweep} pairs, one per slice
     */
    public static double[][] arcs(List<Slice> slices, double gapDeg) {
        int n = slices.size();
        double[][] arcs = new double[n][2];
        if (n == 0) return arcs;
        if (n == 1) {
            arcs[0][0] = 0;
            arcs[0][1] = 360;
            return arcs;
        }
        double minShare = 1;
        for (Slice s : slices) minShare = Math.min(minShare, s.share);
        // each slice keeps at least a third of its own proportional arc
        double gap = Math.max(0, Math.min(gapDeg, minShare * (360.0 - 0) / 3.0));
        double usable = 360.0 - gap * n;
        double angle = gap / 2.0;
        for (int i = 0; i < n; i++) {
            double sweep = slices.get(i).share * usable;
            arcs[i][0] = angle;
            arcs[i][1] = sweep;
            angle += sweep + gap;
        }
        return arcs;
    }

    /** "Cost split by category: Foundation 18%, Walling 35% ..." */
    public static String describe(List<Slice> slices) {
        if (slices.isEmpty()) return "No cost to show yet.";
        StringBuilder b = new StringBuilder("Cost split by category: ");
        for (int i = 0; i < slices.size(); i++) {
            if (i > 0) b.append(", ");
            b.append(slices.get(i).category.label).append(' ')
                    .append(String.format(Locale.US, "%.0f", slices.get(i).share * 100)).append(" percent");
        }
        return b.toString();
    }
}
