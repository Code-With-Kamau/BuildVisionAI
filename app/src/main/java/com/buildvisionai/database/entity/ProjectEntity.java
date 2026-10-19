package com.buildvisionai.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Project header. Status is derived from the head version but cached for list screens. */
@Entity(tableName = "projects",
        indices = {@Index(value = {"updatedAt"})})
public class ProjectEntity {
    @PrimaryKey
    @NonNull public String projectId = "";
    @NonNull public String projectName = "";
    @Nullable public String ownerName;
    @Nullable public String location;
    @Nullable public String county;
    public double plotWidthM;
    public double plotLengthM;
    public double budgetKes;
    @NonNull public String buildingType = "";
    public long createdAt;
    public long updatedAt;
    @NonNull public String status = "";
    @Nullable public String headVersionId;
    public boolean archived;
}
