package com.buildvisionai.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** Room record anchored inside a closed wall loop; metrics are a cache of the derived values. */
@Entity(tableName = "rooms",
        primaryKeys = {"versionId", "id"},
        foreignKeys = {
        @ForeignKey(entity = FloorEntity.class, parentColumns = {"versionId", "id"}, childColumns = {"versionId", "floorId"}, onDelete = ForeignKey.CASCADE)},
        indices = {@Index(value = {"versionId", "floorId"})})
public class RoomEntity {
    @NonNull public String versionId = "";
    @NonNull public String id = "";
    @NonNull public String floorId = "";
    @NonNull public String name = "";
    @NonNull public String roomType = "";
    public double anchorX;
    public double anchorY;
    public double areaM2;
    public double grossAreaM2;
    public double perimeterM;
    public double widthM;
    public double lengthM;
}
