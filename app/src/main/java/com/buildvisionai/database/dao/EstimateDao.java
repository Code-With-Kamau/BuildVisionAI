package com.buildvisionai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Upsert;

import com.buildvisionai.database.EstimateRows;
import com.buildvisionai.database.entity.EstimateAddOnEntity;
import com.buildvisionai.database.entity.EstimateEntity;
import com.buildvisionai.database.entity.EstimateLineEntity;
import com.buildvisionai.database.entity.EstimateSettingsEntity;
import com.buildvisionai.database.entity.RateOverrideEntity;

import java.util.List;

/**
 * Estimate assumptions (per version), stored estimates (a priced snapshot that can be shown again
 * exactly as it was) and the project's own rate quotes.
 *
 * Estimate settings belong to the design and are frozen with it; stored estimates are not, so a
 * locked design can still be re-priced.
 */
@Dao
public abstract class EstimateDao {
    // ---- assumptions ---------------------------------------------------------------------

    @Upsert
    public abstract void upsertSettings(EstimateSettingsEntity settings);

    @Query("SELECT * FROM estimate_settings WHERE versionId = :versionId")
    public abstract EstimateSettingsEntity settings(String versionId);

    @Query("SELECT * FROM estimate_settings WHERE versionId = :versionId")
    public abstract LiveData<EstimateSettingsEntity> observeSettings(String versionId);

    /** Copies the assumptions of one version to another (an existing draft). */
    @Query("INSERT INTO estimate_settings (versionId, finishLevel, roofType, roofPitchDeg, roofOverhangMm, foundationDepthMm, footingThicknessMm, footingProjectionMm, plinthHeightMm, groundSlabMm, hardcoreMm, upperSlabMm, screedMm, plasterMm, mortarJointMm, padSizeMm, padThicknessMm, windowGrills, ceilings, sanitation, waterTankLitres, preliminariesPct, overheadProfitPct, contingencyPct, professionalFeesPct, ncaLevy, wasteUnits, wasteConcrete, wasteMortar, wasteSteel, wasteTiles, wastePaint, wasteRoofSheets, wasteBoards, wasteTimber) "
         + "SELECT :toVersion, finishLevel, roofType, roofPitchDeg, roofOverhangMm, foundationDepthMm, footingThicknessMm, footingProjectionMm, plinthHeightMm, groundSlabMm, hardcoreMm, upperSlabMm, screedMm, plasterMm, mortarJointMm, padSizeMm, padThicknessMm, windowGrills, ceilings, sanitation, waterTankLitres, preliminariesPct, overheadProfitPct, contingencyPct, professionalFeesPct, ncaLevy, wasteUnits, wasteConcrete, wasteMortar, wasteSteel, wasteTiles, wastePaint, wasteRoofSheets, wasteBoards, wasteTimber FROM estimate_settings WHERE versionId = :fromVersion")
    public abstract void copySettings(String fromVersion, String toVersion);

    // ---- stored estimates ----------------------------------------------------------------

    @Insert
    public abstract void insertEstimate(EstimateEntity estimate);

    @Insert
    public abstract void insertLines(List<EstimateLineEntity> lines);

    @Insert
    public abstract void insertAddOns(List<EstimateAddOnEntity> addOns);

    @Transaction
    public void saveEstimate(EstimateRows rows) {
        insertEstimate(rows.estimate);
        if (!rows.lines.isEmpty()) insertLines(rows.lines);
        if (!rows.addOns.isEmpty()) insertAddOns(rows.addOns);
    }

    @Query("SELECT * FROM estimates WHERE id = :estimateId")
    public abstract EstimateEntity estimate(String estimateId);

    @Query("SELECT * FROM estimates WHERE versionId = :versionId ORDER BY createdAt DESC LIMIT 1")
    public abstract EstimateEntity latestFor(String versionId);

    @Query("SELECT * FROM estimates WHERE versionId = :versionId ORDER BY createdAt DESC LIMIT 1")
    public abstract LiveData<EstimateEntity> observeLatestFor(String versionId);

    @Query("SELECT * FROM estimates WHERE versionId = :versionId ORDER BY createdAt DESC")
    public abstract List<EstimateEntity> historyFor(String versionId);

    @Query("SELECT * FROM estimate_lines WHERE estimateId = :estimateId ORDER BY lineNo")
    public abstract List<EstimateLineEntity> lines(String estimateId);

    @Query("SELECT * FROM estimate_addons WHERE estimateId = :estimateId ORDER BY code")
    public abstract List<EstimateAddOnEntity> addOns(String estimateId);

    @Transaction
    public EstimateRows load(String estimateId) {
        EstimateEntity e = estimate(estimateId);
        if (e == null) return null;
        EstimateRows r = new EstimateRows(e);
        r.lines.addAll(lines(estimateId));
        r.addOns.addAll(addOns(estimateId));
        return r;
    }

    /** Keeps the newest {@code keep} estimates of a version so the table cannot grow without bound. */
    @Query("DELETE FROM estimates WHERE versionId = :versionId AND id NOT IN "
         + "(SELECT id FROM estimates WHERE versionId = :versionId ORDER BY createdAt DESC LIMIT :keep)")
    public abstract int prune(String versionId, int keep);

    // ---- the project's own rate quotes ----------------------------------------------------

    @Upsert
    public abstract void upsertRateOverride(RateOverrideEntity rate);

    @Query("SELECT * FROM rate_overrides WHERE projectId = :projectId ORDER BY itemCode")
    public abstract List<RateOverrideEntity> rateOverrides(String projectId);

    @Query("SELECT * FROM rate_overrides WHERE projectId = :projectId ORDER BY itemCode")
    public abstract LiveData<List<RateOverrideEntity>> observeRateOverrides(String projectId);

    @Query("DELETE FROM rate_overrides WHERE projectId = :projectId AND itemCode = :itemCode")
    public abstract void deleteRateOverride(String projectId, String itemCode);
}
