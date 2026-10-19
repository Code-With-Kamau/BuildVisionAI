package com.buildvisionai.estimation;

/**
 * Where an estimate sits against the owner's budget, in the three words people use: fits, fits only
 * at typical prices, over. Uses the low, typical and high totals so the answer shows the price
 * risk instead of one falsely precise number. Pure, so the wording logic is tested.
 */
public final class BudgetPosition {
    public enum Kind {
        /** No budget was entered. */
        NOT_SET,
        /** Even the highest prices fit. */
        WITHIN_EVEN_AT_HIGH,
        /** Typical prices fit; the highest prices would go over. */
        WITHIN_AT_TYPICAL,
        /** Typical prices go over the budget. */
        OVER
    }

    public final Kind kind;
    public final double budgetKes;
    /** Spare money (the WITHIN kinds, against the total that fits) or the overspend (OVER, against the typical total). */
    public final double amountKes;
    /** For WITHIN_AT_TYPICAL: how far the highest prices would overshoot. For OVER: whether the lowest prices would fit. */
    public final double overshootAtHighKes;
    public final boolean fitsAtLowestPrices;

    private BudgetPosition(Kind kind, double budget, double amount, double overshootAtHigh, boolean fitsAtLowest) {
        this.kind = kind;
        this.budgetKes = budget;
        this.amountKes = amount;
        this.overshootAtHighKes = overshootAtHigh;
        this.fitsAtLowestPrices = fitsAtLowest;
    }

    public static BudgetPosition of(double budgetKes, double totalMin, double totalAvg, double totalMax) {
        if (!(budgetKes > 0) || !(totalAvg > 0)) return new BudgetPosition(Kind.NOT_SET, 0, 0, 0, false);
        if (totalMax <= budgetKes) return new BudgetPosition(Kind.WITHIN_EVEN_AT_HIGH, budgetKes, budgetKes - totalMax, 0, true);
        if (totalAvg <= budgetKes) return new BudgetPosition(Kind.WITHIN_AT_TYPICAL, budgetKes, budgetKes - totalAvg, totalMax - budgetKes, true);
        return new BudgetPosition(Kind.OVER, budgetKes, totalAvg - budgetKes, totalMax - budgetKes, totalMin <= budgetKes);
    }
}
