package com.buildvisionai.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Plot sizes people actually quote in Kenya (feet), with their metre equivalents. */
public final class PlotPresets {
    private PlotPresets() {}

    public static final class Preset {
        public final String label;
        public final double widthM, lengthM;

        Preset(String label, double widthFt, double lengthFt) {
            this.label = label;
            this.widthM = widthFt * 0.3048;
            this.lengthM = lengthFt * 0.3048;
        }

        public double areaM2() { return widthM * lengthM; }
    }

    private static final List<Preset> ALL = new ArrayList<>();

    static {
        ALL.add(new Preset("40 x 80 ft", 40, 80));
        ALL.add(new Preset("50 x 100 ft (eighth-acre)", 50, 100));
        ALL.add(new Preset("60 x 100 ft", 60, 100));
        ALL.add(new Preset("100 x 100 ft (about a quarter-acre)", 100, 100));
    }

    public static List<Preset> all() { return Collections.unmodifiableList(ALL); }
}
