package com.buildvisionai.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** Furniture and plumbing fixtures. */
@Entity(tableName = "furniture",
        primaryKeys = {"versionId", "id"},
        foreignKeys = {
        @ForeignKey(entity = FloorEntity.class, parentColumns = {"versionId", "id"}, childColumns = {"versionId", "floorId"}, onDelete = ForeignKey.CASCADE)},
        indices = {@Index(value = {"versionId", "floorId"})})
public class FurnitureEntity {
    @NonNull public String versionId = "";
    @NonNull public String id = "";
    @NonNull public String floorId = "";
    public double x;
    public double y;
    public double widthMm;
    public double depthMm;
    public double rotationDeg;
    @NonNull public String furnitureType = "";
}
