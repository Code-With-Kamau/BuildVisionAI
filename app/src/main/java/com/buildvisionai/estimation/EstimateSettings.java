package com.buildvisionai.estimation;

/**
 * Everything the estimator needs that is not in the drawing. Defaults describe a typical Kenyan
 * masonry house; each value is a normal engineering/market assumption the user can change.
 */
public final class EstimateSettings {
    public FinishLevel finishLevel = FinishLevel.STANDARD;
    public RoofType roofType = RoofType.GABLED_IRON_SHEET;
    public double roofPitchDeg = 20;
    public double roofOverhangMm = 600;

    // sub-structure
    public double foundationDepthMm = 900;
    public double footingThicknessMm = 200;
    /** Footing projects this far beyond each face of the wall. */
    public double footingProjectionMm = 200;
    public double plinthHeightMm = 200;
    public double groundSlabMm = 100;
    public double hardcoreMm = 150;
    public double upperSlabMm = 150;
    public double screedMm = 40;
    public double plasterMm = 12;
    public double mortarJointMm = 12;
    /** Pads under ground-floor columns. */
    public double padSizeMm = 1200;
    public double padThicknessMm = 300;

    // choices
    public boolean windowGrills = true;
    public boolean ceilings = true;
    public SanitationSystem sanitation = SanitationSystem.SEPTIC_TANK;
    public int waterTankLitres = 5000;

    // money add-ons, each a percentage of the construction subtotal (not compounded)
    /** Site set-up, supervision, insurance, water and power during construction. 0 for owner-managed builds. */
    public double preliminariesPct = 8.0;
    /** Contractor overheads and profit. 0 for owner-managed builds with directly hired fundis. */
    public double overheadProfitPct = 12.0;
    public double contingencyPct = 5.0;
    /** Architect/engineer/QS fees as a percentage of the construction subtotal; 0 = not included. */
    public double professionalFeesPct = 0.0;
    public boolean ncaLevy = false;

    public Wastage wastage = new Wastage();

    public EstimateSettings copy() {
        EstimateSettings s = new EstimateSettings();
        s.finishLevel = finishLevel; s.roofType = roofType; s.roofPitchDeg = roofPitchDeg; s.roofOverhangMm = roofOverhangMm;
        s.foundationDepthMm = foundationDepthMm; s.footingThicknessMm = footingThicknessMm;
        s.footingProjectionMm = footingProjectionMm; s.plinthHeightMm = plinthHeightMm;
        s.groundSlabMm = groundSlabMm; s.hardcoreMm = hardcoreMm; s.upperSlabMm = upperSlabMm;
        s.screedMm = screedMm; s.plasterMm = plasterMm; s.mortarJointMm = mortarJointMm;
        s.padSizeMm = padSizeMm; s.padThicknessMm = padThicknessMm;
        s.windowGrills = windowGrills; s.ceilings = ceilings; s.sanitation = sanitation; s.waterTankLitres = waterTankLitres;
        s.preliminariesPct = preliminariesPct; s.overheadProfitPct = overheadProfitPct; s.contingencyPct = contingencyPct; s.professionalFeesPct = professionalFeesPct; s.ncaLevy = ncaLevy;
        s.wastage = wastage.copy();
        return s;
    }

    /** Depth of masonry below the damp-proof course, above the footing. */
    public double foundationWallHeightMm() {
        return Math.max(300, foundationDepthMm - footingThicknessMm + plinthHeightMm);
    }
}
