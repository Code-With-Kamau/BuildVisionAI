package com.buildvisionai.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;

/** A user quote that replaces the rate pack price for one item in one project. */
@Entity(tableName = "rate_overrides",
        primaryKeys = {"projectId", "itemCode"},
        foreignKeys = {
        @ForeignKey(entity = ProjectEntity.class, parentColumns = {"projectId"}, childColumns = {"projectId"}, onDelete = ForeignKey.CASCADE)})
public class RateOverrideEntity {
    @NonNull public String projectId = "";
    @NonNull public String itemCode = "";
    public double rateMin;
    public double rateAvg;
    public double rateMax;
    public long updatedAt;
}
