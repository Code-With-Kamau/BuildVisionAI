package com.buildvisionai.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** One immutable-once-frozen snapshot of a project plan. */
@Entity(tableName = "versions",
        foreignKeys = {
        @ForeignKey(entity = ProjectEntity.class, parentColumns = {"projectId"}, childColumns = {"projectId"}, onDelete = ForeignKey.CASCADE)},
        indices = {@Index(value = {"projectId", "number"}, unique = true)})
public class VersionEntity {
    @PrimaryKey
    @NonNull public String id = "";
    @NonNull public String projectId = "";
    public int number;
    @Nullable public String label;
    @Nullable public String parentVersionId;
    public long createdAt;
    @Nullable public String createdBy;
    @NonNull public String workflowState = "";
}
