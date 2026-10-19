package com.buildvisionai.data.mapper;

import com.buildvisionai.database.EstimateRows;
import com.buildvisionai.database.entity.EstimateAddOnEntity;
import com.buildvisionai.database.entity.EstimateEntity;
import com.buildvisionai.database.entity.EstimateLineEntity;
import com.buildvisionai.database.entity.EstimateSettingsEntity;
import com.buildvisionai.database.entity.RateOverrideEntity;
import com.buildvisionai.estimation.CostEstimate;
import com.buildvisionai.estimation.CostLine;
import com.buildvisionai.estimation.EstimateSettings;
import com.buildvisionai.estimation.FinishLevel;
import com.buildvisionai.estimation.Item;
import com.buildvisionai.estimation.Rate;
import com.buildvisionai.estimation.RatePack;
import com.buildvisionai.estimation.RoofType;
import com.buildvisionai.estimation.SanitationSystem;
import com.buildvisionai.estimation.StatutoryItem;
import com.buildvisionai.models.EstimateSnapshot;

import java.util.List;

/** Estimate assumptions, stored estimates and the project's own rate quotes. */
public final class EstimateMapper {
    private EstimateMapper() {}

    // ---- assumptions -----------------------------------------------------------------------

    public static EstimateSettingsEntity toEntity(String versionId, EstimateSettings s) {
        EstimateSettingsEntity e = new EstimateSettingsEntity();
        e.versionId = versionId;
        e.finishLevel = Codes.of(s.finishLevel);
        e.roofType = Codes.of(s.roofType);
        e.roofPitchDeg = s.roofPitchDeg;
        e.roofOverhangMm = s.roofOverhangMm;
        e.foundationDepthMm = s.foundationDepthMm;
        e.footingThicknessMm = s.footingThicknessMm;
        e.footingProjectionMm = s.footingProjectionMm;
        e.plinthHeightMm = s.plinthHeightMm;
        e.groundSlabMm = s.groundSlabMm;
        e.hardcoreMm = s.hardcoreMm;
        e.upperSlabMm = s.upperSlabMm;
        e.screedMm = s.screedMm;
        e.plasterMm = s.plasterMm;
        e.mortarJointMm = s.mortarJointMm;
        e.padSizeMm = s.padSizeMm;
        e.padThicknessMm = s.padThicknessMm;
        e.windowGrills = s.windowGrills;
        e.ceilings = s.ceilings;
        e.sanitation = Codes.of(s.sanitation);
        e.waterTankLitres = s.waterTankLitres;
        e.preliminariesPct = s.preliminariesPct;
        e.overheadProfitPct = s.overheadProfitPct;
        e.contingencyPct = s.contingencyPct;
        e.professionalFeesPct = s.professionalFeesPct;
        e.ncaLevy = s.ncaLevy;
        e.wasteUnits = s.wastage.masonryUnits;
        e.wasteConcrete = s.wastage.concrete;
        e.wasteMortar = s.wastage.mortar;
        e.wasteSteel = s.wastage.steel;
        e.wasteTiles = s.wastage.tiles;
        e.wastePaint = s.wastage.paint;
        e.wasteRoofSheets = s.wastage.roofSheets;
        e.wasteBoards = s.wastage.boards;
        e.wasteTimber = s.wastage.timber;
        return e;
    }

    public static EstimateSettings toSettings(EstimateSettingsEntity e, List<String> warnings) {
        EstimateSettings s = new EstimateSettings();
        String what = "Estimate settings";
        s.finishLevel = Codes.parse(FinishLevel.class, e.finishLevel, FinishLevel.STANDARD, warnings, what);
        s.roofType = Codes.parse(RoofType.class, e.roofType, RoofType.GABLED_IRON_SHEET, warnings, what);
        s.roofPitchDeg = e.roofPitchDeg;
        s.roofOverhangMm = e.roofOverhangMm;
        s.foundationDepthMm = e.foundationDepthMm;
        s.footingThicknessMm = e.footingThicknessMm;
        s.footingProjectionMm = e.footingProjectionMm;
        s.plinthHeightMm = e.plinthHeightMm;
        s.groundSlabMm = e.groundSlabMm;
        s.hardcoreMm = e.hardcoreMm;
        s.upperSlabMm = e.upperSlabMm;
        s.screedMm = e.screedMm;
        s.plasterMm = e.plasterMm;
        s.mortarJointMm = e.mortarJointMm;
        s.padSizeMm = e.padSizeMm;
        s.padThicknessMm = e.padThicknessMm;
        s.windowGrills = e.windowGrills;
        s.ceilings = e.ceilings;
        s.sanitation = Codes.parse(SanitationSystem.class, e.sanitation, SanitationSystem.SEPTIC_TANK, warnings, what);
        s.waterTankLitres = e.waterTankLitres;
        s.preliminariesPct = e.preliminariesPct;
        s.overheadProfitPct = e.overheadProfitPct;
        s.contingencyPct = e.contingencyPct;
        s.professionalFeesPct = e.professionalFeesPct;
        s.ncaLevy = e.ncaLevy;
        s.wastage.masonryUnits = e.wasteUnits;
        s.wastage.concrete = e.wasteConcrete;
        s.wastage.mortar = e.wasteMortar;
        s.wastage.steel = e.wasteSteel;
        s.wastage.tiles = e.wasteTiles;
        s.wastage.paint = e.wastePaint;
        s.wastage.roofSheets = e.wasteRoofSheets;
        s.wastage.boards = e.wasteBoards;
        s.wastage.timber = e.wasteTimber;
        return s;
    }

    // ---- stored estimates --------------------------------------------------------------------

    public static EstimateRows toRows(String estimateId, String versionId, long createdAt, CostEstimate est) {
        EstimateEntity e = new EstimateEntity();
        e.id = estimateId;
        e.versionId = versionId;
        e.createdAt = createdAt;
        e.ratePackId = Codes.orEmpty(est.ratePackId);
        e.ratePackAsOf = Codes.orEmpty(est.ratePackAsOf);
        e.region = Codes.of(est.region);
        e.finishLevel = Codes.of(est.finishLevel);
        e.grossFloorAreaM2 = est.grossFloorAreaM2;
        e.subtotalMin = est.subtotalMin;
        e.subtotalAvg = est.subtotalAvg;
        e.subtotalMax = est.subtotalMax;
        e.totalMin = est.totalMin;
        e.totalAvg = est.totalAvg;
        e.totalMax = est.totalMax;
        e.perM2Avg = est.perM2Avg;
        EstimateRows rows = new EstimateRows(e);
        int n = 0;
        for (CostLine l : est.lines) {
            EstimateLineEntity le = new EstimateLineEntity();
            le.estimateId = estimateId;
            le.lineNo = n++;
            le.category = Codes.of(l.category);
            le.itemCode = l.item.code();
            le.basis = l.basis;
            le.qty = l.qty;
            le.rateMin = l.rate.min;
            le.rateAvg = l.rate.avg;
            le.rateMax = l.rate.max;
            le.totalMin = l.totalMin;
            le.totalAvg = l.totalAvg;
            le.totalMax = l.totalMax;
            le.allowance = l.allowance;
            rows.lines.add(le);
        }
        for (StatutoryItem a : est.addOns) {
            EstimateAddOnEntity ae = new EstimateAddOnEntity();
            ae.estimateId = estimateId;
            ae.code = a.code;
            ae.label = a.label;
            ae.ratePct = a.ratePct;
            ae.amountMin = a.min;
            ae.amountAvg = a.avg;
            ae.amountMax = a.max;
            ae.note = a.note;
            rows.addOns.add(ae);
        }
        return rows;
    }

    public static EstimateSnapshot toSnapshot(EstimateRows rows) {
        EstimateEntity e = rows.estimate;
        EstimateSnapshot s = new EstimateSnapshot();
        s.id = e.id;
        s.versionId = e.versionId;
        s.createdAt = e.createdAt;
        s.ratePackId = e.ratePackId;
        s.ratePackAsOf = e.ratePackAsOf;
        s.region = e.region;
        s.finishLevel = e.finishLevel;
        s.grossFloorAreaM2 = e.grossFloorAreaM2;
        s.subtotalMin = e.subtotalMin;
        s.subtotalAvg = e.subtotalAvg;
        s.subtotalMax = e.subtotalMax;
        s.totalMin = e.totalMin;
        s.totalAvg = e.totalAvg;
        s.totalMax = e.totalMax;
        s.perM2Avg = e.perM2Avg;
        for (EstimateLineEntity le : rows.lines) {
            EstimateSnapshot.Line l = new EstimateSnapshot.Line();
            l.lineNo = le.lineNo;
            l.category = le.category;
            l.itemCode = le.itemCode;
            l.basis = le.basis;
            l.qty = le.qty;
            l.rateMin = le.rateMin;
            l.rateAvg = le.rateAvg;
            l.rateMax = le.rateMax;
            l.totalMin = le.totalMin;
            l.totalAvg = le.totalAvg;
            l.totalMax = le.totalMax;
            l.allowance = le.allowance;
            s.lines.add(l);
        }
        for (EstimateAddOnEntity ae : rows.addOns) {
            EstimateSnapshot.AddOn a = new EstimateSnapshot.AddOn();
            a.code = ae.code;
            a.label = ae.label;
            a.ratePct = ae.ratePct;
            a.amountMin = ae.amountMin;
            a.amountAvg = ae.amountAvg;
            a.amountMax = ae.amountMax;
            a.note = ae.note;
            s.addOns.add(a);
        }
        return s;
    }

    // ---- the project's own rate quotes -----------------------------------------------------------

    public static RateOverrideEntity toEntity(String projectId, Item item, Rate rate, long now) {
        RateOverrideEntity e = new RateOverrideEntity();
        e.projectId = projectId;
        e.itemCode = item.code();
        e.rateMin = rate.min;
        e.rateAvg = rate.avg;
        e.rateMax = rate.max;
        e.updatedAt = now;
        return e;
    }

    /**
     * The base rate pack with the project's own quotes laid over it. The result has its own id, so a
     * stored estimate says it used customised rates. An override for an unknown item (written by a
     * newer app version, say) is ignored with a warning; an override whose rates are not positive
     * and ordered min &le; avg &le; max is ignored too, so a typo cannot zero out a cost.
     */
    public static RatePack applyOverrides(RatePack base, List<RateOverrideEntity> overrides, List<String> warnings) {
        if (overrides == null || overrides.isEmpty()) return base;
        RatePack p = base.copyAs(base.id + "+project", base.name + " (with your rates)");
        int applied = 0;
        for (RateOverrideEntity o : overrides) {
            Item item;
            try {
                item = Item.valueOf(o.itemCode);
            } catch (IllegalArgumentException ex) {
                if (warnings != null) warnings.add("Your rate for " + o.itemCode + " was ignored: unknown item.");
                continue;
            }
            if (!(o.rateMin > 0) || o.rateAvg < o.rateMin || o.rateMax < o.rateAvg) {
                if (warnings != null) warnings.add("Your rate for " + item.description + " was ignored: it must be positive and min ≤ typical ≤ max.");
                continue;
            }
            p.set(item, new Rate(o.rateMin, o.rateAvg, o.rateMax));
            applied++;
        }
        return applied == 0 ? base : p;
    }
}
