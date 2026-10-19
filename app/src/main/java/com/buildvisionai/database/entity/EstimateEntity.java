package com.buildvisionai.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A computed estimate, kept so it can be shown again exactly as it was (rate pack id and date included). */
@Entity(tableName = "estimates",
        foreignKeys = {
        @ForeignKey(entity = VersionEntity.class, parentColumns = {"id"}, childColumns = {"versionId"}, onDelete = ForeignKey.CASCADE)},
        indices = {@Index(value = {"versionId", "createdAt"})})
public class EstimateEntity {
    @PrimaryKey
    @NonNull public String id = "";
    @NonNull public String versionId = "";
    public long createdAt;
    @NonNull public String ratePackId = "";
    @NonNull public String ratePackAsOf = "";
    @NonNull public String region = "";
    @NonNull public String finishLevel = "";
    public double grossFloorAreaM2;
    public double subtotalMin;
    public double subtotalAvg;
    public double subtotalMax;
    public double totalMin;
    public double totalAvg;
    public double totalMax;
    public double perM2Avg;
}
