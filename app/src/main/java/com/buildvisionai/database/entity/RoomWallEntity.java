package com.buildvisionai.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** Which walls bound which room (many-to-many). */
@Entity(tableName = "room_walls",
        primaryKeys = {"versionId", "roomId", "wallId"},
        foreignKeys = {
        @ForeignKey(entity = RoomEntity.class, parentColumns = {"versionId", "id"}, childColumns = {"versionId", "roomId"}, onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = WallEntity.class, parentColumns = {"versionId", "id"}, childColumns = {"versionId", "wallId"}, onDelete = ForeignKey.CASCADE)},
        indices = {@Index(value = {"versionId", "wallId"})})
public class RoomWallEntity {
    @NonNull public String versionId = "";
    @NonNull public String roomId = "";
    @NonNull public String wallId = "";
    public int position;
}
