package com.buildvisionai.estimation;

/** How wastewater leaves the building. Costed as an allowance: site conditions dominate the real price. */
public enum SanitationSystem {
    SEPTIC_TANK("Septic tank + soak pit"),
    SEWER_CONNECTION("Connection to public sewer"),
    NONE("Not included");

    public final String label;

    SanitationSystem(String label) { this.label = label; }
}
