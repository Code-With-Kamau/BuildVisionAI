package com.buildvisionai.estimation;

import com.buildvisionai.utils.Strings;
import java.util.Locale;

/**
 * Prices a {@link QuantitySchedule}. Pure multiplication and addition: every number on the result
 * traces back to a quantity, a rate and a setting.
 */
public final class CostEstimator {
    private CostEstimator() {}

    public static final double VAT_RATE = 0.16;
    public static final double NCA_LEVY_PCT = 0.5;
    public static final double NCA_LEVY_THRESHOLD_KES = 5_000_000;
    /** Indicative range of finished-house cost per m2 used only to flag implausible estimates. */
    public static final double BENCHMARK_LOW = 35_000, BENCHMARK_HIGH = 122_000;

    public static CostEstimate estimate(QuantitySchedule q, RatePack pack, EstimateSettings s) {
        CostEstimate e = new CostEstimate();
        e.ratePackId = pack.id;
        e.ratePackAsOf = pack.asOf;
        e.disclaimer = pack.disclaimer;
        e.region = pack.region;
        e.finishLevel = s.finishLevel;
        e.grossFloorAreaM2 = q.grossFloorAreaM2;
        for (CostCategory c : CostCategory.values()) e.categories.put(c, new CategoryCost(c));
        e.warnings.addAll(q.warnings);

        for (MaterialLine l : q.lines) {
            Rate r = pack.effective(l.item, s.finishLevel);
            if (r == null) {
                e.warnings.add("No rate for " + l.item.code() + ": not priced.");
                continue;
            }
            add(e, new CostLine(l.category, l.item, Strings.join("; ", l.basis()), l.qty, r, l.allowance));
        }
        for (WorkItem w : WorkItem.values()) {
            double amount = q.work(w);
            if (amount <= 0) continue;
            Rate r = pack.effective(w.labourItem, s.finishLevel);
            if (r == null) {
                e.warnings.add("No rate for " + w.labourItem.code() + ": not priced.");
                continue;
            }
            add(e, new CostLine(CostCategory.LABOUR, w.labourItem, "", amount, r, false));
        }

        for (CategoryCost c : e.categories.values()) {
            e.subtotalMin += c.min;
            e.subtotalAvg += c.avg;
            e.subtotalMax += c.max;
        }

        if (s.preliminariesPct > 0) {
            e.addOns.add(new StatutoryItem("PRELIMINARIES", "Preliminaries", s.preliminariesPct,
                    e.subtotalMin, e.subtotalAvg, e.subtotalMax, "Site set-up, supervision, insurance, temporary water and power"));
        }
        if (s.overheadProfitPct > 0) {
            e.addOns.add(new StatutoryItem("OVERHEAD_PROFIT", "Contractor overhead and profit", s.overheadProfitPct,
                    e.subtotalMin, e.subtotalAvg, e.subtotalMax, "Set to 0 if you hire and manage the fundis yourself"));
        }
        if (s.contingencyPct > 0) {
            e.addOns.add(new StatutoryItem("CONTINGENCY", "Contingency", s.contingencyPct,
                    e.subtotalMin, e.subtotalAvg, e.subtotalMax, "Allowance for unforeseen work and price movement"));
        }
        if (s.professionalFeesPct > 0) {
            e.addOns.add(new StatutoryItem("PROFESSIONAL_FEES", "Professional fees", s.professionalFeesPct,
                    e.subtotalMin, e.subtotalAvg, e.subtotalMax, "Architect, engineer and QS, as a share of construction cost"));
        }
        if (s.ncaLevy) {
            if (e.subtotalAvg >= NCA_LEVY_THRESHOLD_KES) {
                e.addOns.add(new StatutoryItem("NCA_LEVY", "NCA levy", NCA_LEVY_PCT,
                        e.subtotalMin, e.subtotalAvg, e.subtotalMax,
                        "Verify the current rate and threshold with the National Construction Authority"));
            } else {
                e.warnings.add("NCA levy is switched on but the estimate is below the KES 5 million threshold used here; not charged. Verify with NCA.");
            }
        }
        e.totalMin = e.subtotalMin;
        e.totalAvg = e.subtotalAvg;
        e.totalMax = e.subtotalMax;
        for (StatutoryItem a : e.addOns) {
            e.totalMin += a.min;
            e.totalAvg += a.avg;
            e.totalMax += a.max;
        }
        e.vatIncludedAvg = e.totalAvg * VAT_RATE / (1 + VAT_RATE);

        if (e.grossFloorAreaM2 > 0) {
            e.perM2Min = e.totalMin / e.grossFloorAreaM2;
            e.perM2Avg = e.totalAvg / e.grossFloorAreaM2;
            e.perM2Max = e.totalMax / e.grossFloorAreaM2;
            e.benchmark = benchmark(e.perM2Avg);
        } else {
            e.benchmark = new CostEstimate.Benchmark(CostEstimate.Benchmark.Position.UNKNOWN, BENCHMARK_LOW, BENCHMARK_HIGH,
                    "No enclosed floor area yet, so cost per m2 cannot be checked.");
        }
        return e;
    }

    private static void add(CostEstimate e, CostLine l) {
        e.lines.add(l);
        CategoryCost c = e.categories.get(l.category);
        c.min += l.totalMin;
        c.avg += l.totalAvg;
        c.max += l.totalMax;
    }

    static CostEstimate.Benchmark benchmark(double perM2) {
        String range = String.format(Locale.US, "KES %,.0f - %,.0f per m2", BENCHMARK_LOW, BENCHMARK_HIGH);
        if (perM2 < BENCHMARK_LOW) {
            return new CostEstimate.Benchmark(CostEstimate.Benchmark.Position.BELOW, BENCHMARK_LOW, BENCHMARK_HIGH,
                    String.format(Locale.US, "KES %,.0f per m2 is below the indicative range (%s). Check for missing items or rates that are too low.", perM2, range));
        }
        if (perM2 > BENCHMARK_HIGH) {
            return new CostEstimate.Benchmark(CostEstimate.Benchmark.Position.ABOVE, BENCHMARK_LOW, BENCHMARK_HIGH,
                    String.format(Locale.US, "KES %,.0f per m2 is above the indicative range (%s). Check premium finishes, allowances and rates.", perM2, range));
        }
        return new CostEstimate.Benchmark(CostEstimate.Benchmark.Position.WITHIN, BENCHMARK_LOW, BENCHMARK_HIGH,
                String.format(Locale.US, "KES %,.0f per m2 is inside the indicative range (%s). This is a sanity check, not a validation.", perM2, range));
    }
}
