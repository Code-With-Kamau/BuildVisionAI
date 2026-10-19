package com.buildvisionai.utils;

import java.util.Locale;

/** Kenya shilling formatting. Money is held as double KES and rounded only when displayed. */
public final class MoneyFormatter {
    private MoneyFormatter() {}

    /** "KES 1,234,567". */
    public static String kes(double v) { return String.format(Locale.US, "KES %,.0f", v); }

    /** "KES 5.06M" / "KES 450K" / "KES 980" for tight spaces such as chart labels. */
    public static String compact(double v) {
        double a = Math.abs(v);
        if (a >= 1_000_000) return String.format(Locale.US, "KES %.2fM", v / 1_000_000);
        if (a >= 10_000) return String.format(Locale.US, "KES %.0fK", v / 1_000);
        return String.format(Locale.US, "KES %,.0f", v);
    }

    /** Parses "5,000,000", "5000000", "5m", "5.5M", "750k". Returns NaN if unparsable. */
    public static double parse(String text) {
        if (text == null) return Double.NaN;
        String t = text.trim().toLowerCase(Locale.US).replace(",", "").replace("kes", "").replace("ksh", "").trim();
        if (t.isEmpty()) return Double.NaN;
        double mult = 1;
        if (t.endsWith("m")) { mult = 1_000_000; t = t.substring(0, t.length() - 1).trim(); }
        else if (t.endsWith("k")) { mult = 1_000; t = t.substring(0, t.length() - 1).trim(); }
        try {
            return Double.parseDouble(t) * mult;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
