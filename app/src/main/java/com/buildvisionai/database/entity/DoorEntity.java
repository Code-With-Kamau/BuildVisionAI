package com.buildvisionai.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** Door attached to a wall by offset; deleted with its wall. */
@Entity(tableName = "doors",
        primaryKeys = {"versionId", "id"},
        foreignKeys = {
        @ForeignKey(entity = WallEntity.class, parentColumns = {"versionId", "id"}, childColumns = {"versionId", "wallId"}, onDelete = ForeignKey.CASCADE)},
        indices = {@Index(value = {"versionId", "wallId"}), @Index(value = {"versionId", "floorId"})})
public class DoorEntity {
    @NonNull public String versionId = "";
    @NonNull public String id = "";
    @NonNull public String floorId = "";
    @NonNull public String wallId = "";
    public double offsetMm;
    public double widthMm;
    public double heightMm;
    @NonNull public String doorType = "";
    @NonNull public String swing = "";
}
