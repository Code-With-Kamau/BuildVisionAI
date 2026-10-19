package com.buildvisionai.estimation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Priced result: lines, per-category totals, add-ons and a sanity check against market ranges. */
public final class CostEstimate {
    public String ratePackId;
    public String ratePackAsOf;
    public String disclaimer;
    public Region region;
    public FinishLevel finishLevel;

    public final List<CostLine> lines = new ArrayList<>();
    public final Map<CostCategory, CategoryCost> categories = new EnumMap<>(CostCategory.class);
    public final List<StatutoryItem> addOns = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();

    public double subtotalMin, subtotalAvg, subtotalMax;
    public double totalMin, totalAvg, totalMax;
    /** VAT portion contained in the average total. */
    public double vatIncludedAvg;
    public double grossFloorAreaM2;
    public double perM2Min, perM2Avg, perM2Max;

    public Benchmark benchmark;

    /** Where the per-m2 cost sits against an indicative market range. */
    public static final class Benchmark {
        public enum Position { BELOW, WITHIN, ABOVE, UNKNOWN }

        public final Position position;
        public final double lowKesPerM2, highKesPerM2;
        public final String message;

        Benchmark(Position position, double low, double high, String message) {
            this.position = position;
            this.lowKesPerM2 = low;
            this.highKesPerM2 = high;
            this.message = message;
        }
    }

    public CategoryCost category(CostCategory c) {
        CategoryCost cc = categories.get(c);
        return cc == null ? new CategoryCost(c) : cc;
    }
}
