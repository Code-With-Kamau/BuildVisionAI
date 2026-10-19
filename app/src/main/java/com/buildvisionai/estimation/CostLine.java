package com.buildvisionai.estimation;

/** One priced line: quantity x low/typical/high rate. */
public final class CostLine {
    public final CostCategory category;
    public final Item item;
    public final String basis;
    public final double qty;
    public final Rate rate;
    public final double totalMin, totalAvg, totalMax;
    public final boolean allowance;

    CostLine(CostCategory category, Item item, String basis, double qty, Rate rate, boolean allowance) {
        this.category = category;
        this.item = item;
        this.basis = basis;
        this.qty = qty;
        this.rate = rate;
        this.allowance = allowance;
        this.totalMin = qty * rate.min;
        this.totalAvg = qty * rate.avg;
        this.totalMax = qty * rate.max;
    }
}
