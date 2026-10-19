package com.buildvisionai.models;

import java.util.ArrayList;
import java.util.List;

/**
 * A stored estimate read back from the database: the numbers exactly as they were when it was
 * priced, with the rate pack id and date they came from. It is a record, not a calculation; to
 * price the current plan again, run the estimator.
 */
public final class EstimateSnapshot {
    public static final class Line {
        public int lineNo;
        public String category;
        public String itemCode;
        public String basis;
        public double qty;
        public double rateMin, rateAvg, rateMax;
        public double totalMin, totalAvg, totalMax;
        public boolean allowance;
    }

    public static final class AddOn {
        public String code;
        public String label;
        public double ratePct;
        public double amountMin, amountAvg, amountMax;
        public String note;
    }

    public String id;
    public String versionId;
    public long createdAt;
    public String ratePackId;
    public String ratePackAsOf;
    public String region;
    public String finishLevel;
    public double grossFloorAreaM2;
    public double subtotalMin, subtotalAvg, subtotalMax;
    public double totalMin, totalAvg, totalMax;
    public double perM2Avg;
    public final List<Line> lines = new ArrayList<>();
    public final List<AddOn> addOns = new ArrayList<>();

    /** Sum of the stored line totals of one category (the UI donut uses this). */
    public double categoryAvg(String category) {
        double s = 0;
        for (Line l : lines) if (l.category.equals(category)) s += l.totalAvg;
        return s;
    }
}
