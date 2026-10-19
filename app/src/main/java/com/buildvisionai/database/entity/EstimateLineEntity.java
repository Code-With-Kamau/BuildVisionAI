package com.buildvisionai.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;

/** One priced line: material or labour, with quantity and low/typical/high rate. */
@Entity(tableName = "estimate_lines",
        primaryKeys = {"estimateId", "lineNo"},
        foreignKeys = {
        @ForeignKey(entity = EstimateEntity.class, parentColumns = {"id"}, childColumns = {"estimateId"}, onDelete = ForeignKey.CASCADE)})
public class EstimateLineEntity {
    @NonNull public String estimateId = "";
    public int lineNo;
    @NonNull public String category = "";
    @NonNull public String itemCode = "";
    @Nullable public String basis;
    public double qty;
    public double rateMin;
    public double rateAvg;
    public double rateMax;
    public double totalMin;
    public double totalAvg;
    public double totalMax;
    public boolean allowance;
}
