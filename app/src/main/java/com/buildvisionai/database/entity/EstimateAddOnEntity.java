package com.buildvisionai.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;

/** Percentage add-ons: preliminaries, overhead and profit, contingency, fees, levy. */
@Entity(tableName = "estimate_addons",
        primaryKeys = {"estimateId", "code"},
        foreignKeys = {
        @ForeignKey(entity = EstimateEntity.class, parentColumns = {"id"}, childColumns = {"estimateId"}, onDelete = ForeignKey.CASCADE)})
public class EstimateAddOnEntity {
    @NonNull public String estimateId = "";
    @NonNull public String code = "";
    @NonNull public String label = "";
    public double ratePct;
    public double amountMin;
    public double amountAvg;
    public double amountMax;
    @Nullable public String note;
}
