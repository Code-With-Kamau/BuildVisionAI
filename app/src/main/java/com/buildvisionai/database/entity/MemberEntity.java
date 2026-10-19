package com.buildvisionai.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** Role of a person on a project. */
@Entity(tableName = "members",
        primaryKeys = {"projectId", "userId"},
        foreignKeys = {
        @ForeignKey(entity = ProjectEntity.class, parentColumns = {"projectId"}, childColumns = {"projectId"}, onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = UserEntity.class, parentColumns = {"id"}, childColumns = {"userId"}, onDelete = ForeignKey.CASCADE)},
        indices = {@Index(value = {"userId"})})
public class MemberEntity {
    @NonNull public String projectId = "";
    @NonNull public String userId = "";
    @NonNull public String displayName = "";
    @NonNull public String role = "";
    @Nullable public String registrationNo;
    public long addedAt;
}
