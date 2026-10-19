package com.buildvisionai.estimation;

/**
 * A cement : sand : aggregate mix by volume. Dry-to-wet volume factors are the usual textbook
 * values (1.54 for concrete, 1.33 for mortar). A 50 kg bag of cement is 0.0347 m3 (1440 kg/m3).
 */
public final class Mix {
    public static final double BAG_M3 = 50.0 / 1440.0;
    /** Bulk densities used to convert volumes to the tonnes that sand and ballast are sold in. */
    public static final double SAND_T_PER_M3 = 1.6;
    public static final double BALLAST_T_PER_M3 = 1.55;

    public final String label;
    public final double cement, sand, aggregate;
    public final double dryFactor;

    private Mix(String label, double cement, double sand, double aggregate, double dryFactor) {
        this.label = label;
        this.cement = cement;
        this.sand = sand;
        this.aggregate = aggregate;
        this.dryFactor = dryFactor;
    }

    public static final Mix C_1_3_6 = new Mix("1:3:6", 1, 3, 6, 1.54);
    public static final Mix C_1_2_4 = new Mix("1:2:4", 1, 2, 4, 1.54);
    public static final Mix C_1_15_3 = new Mix("1:1.5:3", 1, 1.5, 3, 1.54);
    public static final Mix M_1_3 = new Mix("1:3", 1, 3, 0, 1.33);
    public static final Mix M_1_4 = new Mix("1:4", 1, 4, 0, 1.33);

    private double parts() { return cement + sand + aggregate; }

    /** Cement bags per m3 of finished (wet) concrete or mortar. */
    public double bagsPerM3() { return dryFactor * (cement / parts()) / BAG_M3; }

    public double sandTonnesPerM3() { return dryFactor * (sand / parts()) * SAND_T_PER_M3; }

    public double ballastTonnesPerM3() { return dryFactor * (aggregate / parts()) * BALLAST_T_PER_M3; }
}
