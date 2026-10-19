package com.buildvisionai.utils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formats and parses lengths. Kenyan plots and house plans are quoted in both metres and feet
 * (a 50 x 100 ft plot is typical), so both systems are first-class. All maths stays in mm.
 */
public final class LengthFormatter {
    private LengthFormatter() {}

    public static final double MM_PER_INCH = 25.4;
    public static final double MM_PER_FOOT = 304.8;

    public static String format(double mm, UnitSystem u) {
        return u == UnitSystem.FEET_INCHES ? feetInches(mm) : metric(mm);
    }

    public static String metric(double mm) {
        double a = Math.abs(mm);
        if (a >= 1000) return String.format(Locale.US, "%.2f m", mm / 1000.0);
        return String.format(Locale.US, "%d mm", Math.round(mm));
    }

    /** Rounded to the nearest inch, e.g. 3500 mm -> 11'-6". */
    public static String feetInches(double mm) {
        long totalIn = Math.round(Math.abs(mm) / MM_PER_INCH);
        long ft = totalIn / 12, in = totalIn % 12;
        String sign = mm < 0 ? "-" : "";
        return ft == 0 ? sign + in + "\"" : sign + ft + "'-" + in + "\"";
    }

    public static String areaM2(double m2) { return String.format(Locale.US, "%.2f m²", m2); }

    public static String areaSqFt(double m2) { return String.format(Locale.US, "%.0f ft²", m2 * 10.7639104); }

    private static final Pattern FT_IN = Pattern.compile(
            "^\\s*(?:(\\d+(?:\\.\\d+)?)\\s*(?:'|ft|feet))?\\s*-?\\s*(?:(\\d+(?:\\.\\d+)?)\\s*(?:\"|in|inch|inches)?)?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NUM_UNIT = Pattern.compile(
            "^\\s*(-?\\d+(?:\\.\\d+)?)\\s*(mm|cm|m)?\\s*$", Pattern.CASE_INSENSITIVE);

    /**
     * Parses user input into millimetres. Accepts "3.5", "3.5m", "3500mm", "350cm", "11'6\"",
     * "11ft 6in", "12'". A bare number is read in {@code defaultUnit}. Returns NaN if unparsable.
     */
    public static double parseMm(String text, UnitSystem defaultUnit) {
        if (text == null) return Double.NaN;
        String t = text.trim();
        if (t.isEmpty()) return Double.NaN;
        Matcher m = NUM_UNIT.matcher(t);
        if (m.matches()) {
            double v = Double.parseDouble(m.group(1));
            String unit = m.group(2) == null ? null : m.group(2).toLowerCase(Locale.US);
            if (unit == null) return defaultUnit == UnitSystem.FEET_INCHES ? v * MM_PER_FOOT : v * 1000.0;
            switch (unit) {
                case "mm": return v;
                case "cm": return v * 10.0;
                default: return v * 1000.0;
            }
        }
        Matcher f = FT_IN.matcher(t);
        if (f.matches() && (f.group(1) != null || f.group(2) != null)) {
            double ft = f.group(1) == null ? 0 : Double.parseDouble(f.group(1));
            double in = f.group(2) == null ? 0 : Double.parseDouble(f.group(2));
            return ft * MM_PER_FOOT + in * MM_PER_INCH;
        }
        return Double.NaN;
    }
}
