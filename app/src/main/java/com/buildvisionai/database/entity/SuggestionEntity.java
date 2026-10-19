package com.buildvisionai.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Proposed change. The payload is the only JSON in the schema. */
@Entity(tableName = "suggestions",
        foreignKeys = {
        @ForeignKey(entity = VersionEntity.class, parentColumns = {"id"}, childColumns = {"versionId"}, onDelete = ForeignKey.CASCADE)},
        indices = {@Index(value = {"versionId", "status"})})
public class SuggestionEntity {
    @PrimaryKey
    @NonNull public String id = "";
    @NonNull public String versionId = "";
    @NonNull public String authorId = "";
    @NonNull public String authorName = "";
    @NonNull public String source = "";
    @NonNull public String status = "";
    @NonNull public String title = "";
    @Nullable public String rationale;
    @Nullable public String payloadJson;
    public long createdAt;
    @Nullable public String decidedBy;
    public long decidedAt;
}
