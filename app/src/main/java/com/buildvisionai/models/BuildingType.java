package com.buildvisionai.models;

public enum BuildingType {
    BUNGALOW("Bungalow"), MAISONETTE("Maisonette"), APARTMENTS("Apartment block"),
    RENTAL_UNITS("Rental units"), COMMERCIAL("Commercial"), INSTITUTIONAL("Institutional"), OTHER("Other");

    public final String label;
    BuildingType(String label) { this.label = label; }
}
