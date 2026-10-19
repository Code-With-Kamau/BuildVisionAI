package com.buildvisionai.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** Wall centreline. Length and rotation are derived, never stored. */
@Entity(tableName = "walls",
        primaryKeys = {"versionId", "id"},
        foreignKeys = {
        @ForeignKey(entity = FloorEntity.class, parentColumns = {"versionId", "id"}, childColumns = {"versionId", "floorId"}, onDelete = ForeignKey.CASCADE)},
        indices = {@Index(value = {"versionId", "floorId"})})
public class WallEntity {
    @NonNull public String versionId = "";
    @NonNull public String id = "";
    @NonNull public String floorId = "";
    public double startX;
    public double startY;
    public double endX;
    public double endY;
    public double thicknessMm;
    public double heightMm;
    @NonNull public String kind = "";
    @NonNull public String material = "";
}
