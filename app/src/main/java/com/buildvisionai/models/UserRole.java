package com.buildvisionai.models;

/** Project roles. The regulator is the Kenyan body that registers that profession (informational). */
public enum UserRole {
    OWNER("Owner", null),
    ARCHITECT("Architect", "BORAQS"),
    ENGINEER("Engineer", "EBK"),
    QUANTITY_SURVEYOR("Quantity Surveyor", "BORAQS"),
    CONTRACTOR("Contractor", "NCA");

    public final String label;
    public final String regulator;

    UserRole(String label, String regulator) {
        this.label = label;
        this.regulator = regulator;
    }

    public boolean isProfessional() { return this == ARCHITECT || this == ENGINEER || this == QUANTITY_SURVEYOR; }
}
