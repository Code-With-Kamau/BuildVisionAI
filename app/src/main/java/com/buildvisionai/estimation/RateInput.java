package com.buildvisionai.estimation;

import com.buildvisionai.utils.MoneyFormatter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validation of a price quote typed for one item: three amounts in KES per unit (lowest, typical,
 * highest). Pure so the rules are tested: every amount must be a positive number, and they must be
 * in order. A quote is the project's own price and replaces the built-in rate for that item only.
 */
public final class RateInput {
    public static final String MIN = "min", AVG = "avg", MAX = "max";
    public static final double MAX_RATE_KES = 100_000_000.0;

    private RateInput() {}

    public static final class Result {
        public final Map<String, String> errors = new LinkedHashMap<>();
        /** Set only when {@link #errors} is empty. */
        public Rate rate;

        public boolean ok() { return errors.isEmpty(); }
    }

    public static Result parse(String minText, String avgText, String maxText) {
        Result r = new Result();
        double min = amount(minText), avg = amount(avgText), max = amount(maxText);
        check(r, MIN, min);
        check(r, AVG, avg);
        check(r, MAX, max);
        if (r.ok() && !(min <= avg && avg <= max)) {
            r.errors.put(AVG, "Lowest must not be above typical, and typical must not be above highest.");
        }
        if (r.ok()) r.rate = new Rate(min, avg, max);
        return r;
    }

    /** The rate as three strings (rounded to whole shillings) for filling the form. */
    public static String[] format(Rate rate) {
        return new String[]{shown(rate.min), shown(rate.avg), shown(rate.max)};
    }

    private static void check(Result r, String key, double v) {
        if (Double.isNaN(v)) r.errors.put(key, "Enter an amount such as 850 or 1,200.");
        else if (v <= 0) r.errors.put(key, "Enter an amount above zero.");
        else if (v > MAX_RATE_KES) r.errors.put(key, "That amount is too large.");
    }

    private static double amount(String text) {
        double v = MoneyFormatter.parse(text);
        return Double.isFinite(v) ? v : Double.NaN;
    }

    private static String shown(double v) { return String.format(java.util.Locale.US, "%.0f", v); }
}
