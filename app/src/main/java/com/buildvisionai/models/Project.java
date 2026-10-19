package com.buildvisionai.models;

/**
 * Project header. Plot dimensions are metres and budget is Kenya shillings (KES); plan elements
 * themselves use millimetres.
 */
public final class Project {
    public String projectId;
    public String projectName;
    public String ownerName;
    /** Free-text location, e.g. "Ruiru, Kiambu". */
    public String location;
    /** One of the 47 counties; selects the regional rate pack. */
    public String county;
    public double plotWidthM;
    public double plotLengthM;
    public double budgetKes;
    public BuildingType buildingType;
    public long createdAt;
    public long updatedAt;
    public ProjectStatus status = ProjectStatus.DRAFT;
    /** The editable (or latest) version. */
    public String headVersionId;

    public double plotAreaM2() { return plotWidthM * plotLengthM; }
}
