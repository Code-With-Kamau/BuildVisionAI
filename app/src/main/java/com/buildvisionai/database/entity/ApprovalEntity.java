package com.buildvisionai.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Append-only sign-off history. */
@Entity(tableName = "approvals",
        foreignKeys = {
        @ForeignKey(entity = VersionEntity.class, parentColumns = {"id"}, childColumns = {"versionId"}, onDelete = ForeignKey.CASCADE)},
        indices = {@Index(value = {"versionId", "createdAt"})})
public class ApprovalEntity {
    @PrimaryKey
    @NonNull public String id = "";
    @NonNull public String versionId = "";
    @NonNull public String userId = "";
    @NonNull public String userName = "";
    @NonNull public String role = "";
    @NonNull public String stage = "";
    @NonNull public String decision = "";
    @Nullable public String comment;
    public long createdAt;
}
