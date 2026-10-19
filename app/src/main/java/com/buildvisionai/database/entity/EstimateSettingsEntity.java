package com.buildvisionai.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

/** Estimate assumptions for one version (one row). */
@Entity(tableName = "estimate_settings",
        foreignKeys = {
        @ForeignKey(entity = VersionEntity.class, parentColumns = {"id"}, childColumns = {"versionId"}, onDelete = ForeignKey.CASCADE)})
public class EstimateSettingsEntity {
    @PrimaryKey
    @NonNull public String versionId = "";
    @NonNull public String finishLevel = "";
    @NonNull public String roofType = "";
    public double roofPitchDeg;
    public double roofOverhangMm;
    public double foundationDepthMm;
    public double footingThicknessMm;
    public double footingProjectionMm;
    public double plinthHeightMm;
    public double groundSlabMm;
    public double hardcoreMm;
    public double upperSlabMm;
    public double screedMm;
    public double plasterMm;
    public double mortarJointMm;
    public double padSizeMm;
    public double padThicknessMm;
    public boolean windowGrills;
    public boolean ceilings;
    @NonNull public String sanitation = "";
    public int waterTankLitres;
    public double preliminariesPct;
    public double overheadProfitPct;
    public double contingencyPct;
    public double professionalFeesPct;
    public boolean ncaLevy;
    public double wasteUnits;
    public double wasteConcrete;
    public double wasteMortar;
    public double wasteSteel;
    public double wasteTiles;
    public double wastePaint;
    public double wasteRoofSheets;
    public double wasteBoards;
    public double wasteTimber;
}
