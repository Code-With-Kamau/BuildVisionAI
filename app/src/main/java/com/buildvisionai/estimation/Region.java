package com.buildvisionai.estimation;

/**
 * Price regions. The factor scales the whole rate pack and mainly reflects haulage from Nairobi
 * and Mombasa. The factors are indicative placeholders, not surveyed data.
 */
public enum Region {
    NAIROBI_METRO("Nairobi metro", 1.00),
    CENTRAL("Central highlands", 0.98),
    COAST("Coast", 1.06),
    RIFT_VALLEY("Rift Valley", 1.00),
    WESTERN_NYANZA("Western & Nyanza", 1.01),
    EASTERN("Lower Eastern", 1.04),
    NORTHERN_ARID("Northern & arid counties", 1.25);

    public final String label;
    public final double factor;

    Region(String label, double factor) {
        this.label = label;
        this.factor = factor;
    }
}
