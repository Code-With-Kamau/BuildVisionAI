package com.buildvisionai.utils;

/** Display units for lengths. Storage is always millimetres. */
public enum UnitSystem {
    METRIC("Metric (m, mm)"),
    FEET_INCHES("Feet & inches");

    public final String label;

    UnitSystem(String label) { this.label = label; }
}
