package com.buildvisionai.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Discussion comment on a version, optionally about one element. */
@Entity(tableName = "comments",
        foreignKeys = {
        @ForeignKey(entity = VersionEntity.class, parentColumns = {"id"}, childColumns = {"versionId"}, onDelete = ForeignKey.CASCADE)},
        indices = {@Index(value = {"versionId", "createdAt"})})
public class CommentEntity {
    @PrimaryKey
    @NonNull public String id = "";
    @NonNull public String versionId = "";
    @Nullable public String elementId;
    @NonNull public String authorId = "";
    @NonNull public String authorName = "";
    @NonNull public String authorRole = "";
    @NonNull public String text = "";
    public long createdAt;
    public boolean resolved;
}
