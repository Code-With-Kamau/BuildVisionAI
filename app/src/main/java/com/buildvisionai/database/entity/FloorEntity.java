package com.buildvisionai.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;

/** A level of the building within one version. */
@Entity(tableName = "floors",
        primaryKeys = {"versionId", "id"},
        foreignKeys = {
        @ForeignKey(entity = VersionEntity.class, parentColumns = {"id"}, childColumns = {"versionId"}, onDelete = ForeignKey.CASCADE)})
public class FloorEntity {
    @NonNull public String versionId = "";
    @NonNull public String id = "";
    public int level;
    @NonNull public String name = "";
    public double floorToFloorMm;
}
