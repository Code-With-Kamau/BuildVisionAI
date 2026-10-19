package com.buildvisionai.estimation;

/** Specification level. Only finish-sensitive items (tiles, paint, fixtures, doors, windows, fittings) are scaled. */
public enum FinishLevel {
    BASIC("Basic"), STANDARD("Standard"), PREMIUM("Premium");

    public final String label;

    FinishLevel(String label) { this.label = label; }
}
