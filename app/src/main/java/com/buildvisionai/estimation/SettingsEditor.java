package com.buildvisionai.estimation;

import java.util.Locale;

/**
 * The numeric estimate assumptions a person can type: limits, parsing and applying. Choice fields
 * (finish level, roof type, sanitation) and switches are set directly on {@link EstimateSettings};
 * only free-typed numbers need validating, and doing it here keeps the rules in one tested place.
 */
public final class SettingsEditor {
    private SettingsEditor() {}

    public enum Field {
        ROOF_PITCH("degrees", 5, 45),
        CONTINGENCY("%", 0, 30),
        PRELIMINARIES("%", 0, 25),
        OVERHEAD_PROFIT("%", 0, 30),
        PROFESSIONAL_FEES("%", 0, 20),
        WATER_TANK("litres", 0, 50_000);

        public final String unit;
        public final double min, max;

        Field(String unit, double min, double max) {
            this.unit = unit;
            this.min = min;
            this.max = max;
        }
    }

    /** Null when {@code text} is acceptable, otherwise a message to show next to the field. */
    public static String validate(Field f, String text) {
        double v = parse(text);
        if (Double.isNaN(v)) return "Enter a number.";
        if (v < f.min || v > f.max) return String.format(Locale.US, "Enter between %s and %s %s.", plain(f.min), plain(f.max), f.unit);
        if (f == Field.WATER_TANK && v != Math.rint(v)) return "Enter a whole number of litres.";
        return null;
    }

    /** Applies a validated value; an invalid one changes nothing. */
    public static void apply(EstimateSettings s, Field f, String text) {
        if (validate(f, text) != null) return;
        double v = parse(text);
        switch (f) {
            case ROOF_PITCH: s.roofPitchDeg = v; break;
            case CONTINGENCY: s.contingencyPct = v; break;
            case PRELIMINARIES: s.preliminariesPct = v; break;
            case OVERHEAD_PROFIT: s.overheadProfitPct = v; break;
            case PROFESSIONAL_FEES: s.professionalFeesPct = v; break;
            default: s.waterTankLitres = (int) v; break;
        }
    }

    /** The current value as it should appear in the field. */
    public static String format(EstimateSettings s, Field f) {
        switch (f) {
            case ROOF_PITCH: return plain(s.roofPitchDeg);
            case CONTINGENCY: return plain(s.contingencyPct);
            case PRELIMINARIES: return plain(s.preliminariesPct);
            case OVERHEAD_PROFIT: return plain(s.overheadProfitPct);
            case PROFESSIONAL_FEES: return plain(s.professionalFeesPct);
            default: return Integer.toString(s.waterTankLitres);
        }
    }

    /** "5", "5.5", "5 %" and "5%" all read as 5, 5.5, 5 and 5. NaN for anything else. */
    static double parse(String text) {
        if (text == null) return Double.NaN;
        String t = text.trim();
        if (t.endsWith("%")) t = t.substring(0, t.length() - 1).trim();
        if (t.isEmpty()) return Double.NaN;
        try {
            double v = Double.parseDouble(t);
            return Double.isFinite(v) ? v : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static String plain(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.US, "%.1f", v);
    }
}
