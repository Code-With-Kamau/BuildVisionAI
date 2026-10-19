package com.buildvisionai.estimation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.buildvisionai.geometry.PlanMetrics;
import com.buildvisionai.models.Floor;
import com.buildvisionai.models.FurnitureType;
import com.buildvisionai.models.Room;
import com.buildvisionai.models.RoomType;
import com.buildvisionai.models.Stair;
import com.buildvisionai.models.StairType;
import com.buildvisionai.models.Wall;
import com.buildvisionai.models.WallKind;
import com.buildvisionai.models.WallMaterial;
import com.buildvisionai.planner.PlanDocument;

import org.junit.Test;

public class EstimationTest {

    static QuantitySchedule schedule(PlanDocument d, EstimateSettings s) { return MaterialEstimator.estimate(d, s); }

    static CostEstimate cost(PlanDocument d, EstimateSettings s) {
        return CostEstimator.estimate(schedule(d, s), KenyaRatePack.nairobi(), s);
    }

    // ---- mixes ---------------------------------------------------------------------------

    @Test public void concreteMixQuantitiesMatchTextbookValues() {
        assertEquals(6.34, Mix.C_1_2_4.bagsPerM3(), 0.02);       // ~6.3 bags of 50 kg per m3 of 1:2:4
        assertEquals(0.44 * Mix.SAND_T_PER_M3, Mix.C_1_2_4.sandTonnesPerM3(), 0.01);
        assertEquals(0.88 * Mix.BALLAST_T_PER_M3, Mix.C_1_2_4.ballastTonnesPerM3(), 0.01);
        assertEquals(8.07, Mix.C_1_15_3.bagsPerM3(), 0.03);
        assertEquals(7.66, Mix.M_1_4.bagsPerM3(), 0.03);
        assertEquals(0, Mix.M_1_4.ballastTonnesPerM3(), 0);
    }

    @Test public void blockCountPerSquareMetreMatchesTheUsualFigure() {
        assertEquals(11.45, WallMaterial.BLOCK_150.unitsPerM2(12), 0.02);   // 400x200 block, 12 mm joints
        assertEquals(48.5, WallMaterial.BRICK_112.unitsPerM2(12), 0.2);     // half-brick wall, 75 mm courses
        assertEquals(97.0, WallMaterial.BRICK_225.unitsPerM2(12), 0.4);     // one-brick wall: two layers
    }

    // ---- reference house -----------------------------------------------------------------

    @Test public void referenceHouseHasSevenRoomsAndAPlausibleArea() {
        PlanDocument d = TestPlans.bungalow();
        PlanMetrics pm = d.floorPlan("g").metrics();
        assertEquals(7, pm.topology.rooms.size());
        assertEquals(7, d.floorPlan("g").rooms.size());
        assertEquals(12.2 * 10.2, pm.footprintAreaM2, 1e-6);
        assertTrue("net floor area " + pm.netFloorAreaM2, pm.netFloorAreaM2 > 100 && pm.netFloorAreaM2 < 120);
        assertEquals(8, pm.doorCount);
        assertEquals(8, pm.windowCount);
        assertEquals(0, pm.unattachedOpenings);
    }

    @Test public void scheduleContainsTheMajorWorkSections() {
        QuantitySchedule q = schedule(TestPlans.bungalow(), new EstimateSettings());
        assertTrue(q.warnings.toString(), q.warnings.isEmpty());
        assertEquals(1, q.floors);
        for (CostCategory c : CostCategory.values()) {
            if (c == CostCategory.LABOUR) continue;
            boolean any = false;
            for (MaterialLine l : q.lines) any |= l.category == c;
            assertTrue("category " + c + " has lines", any);
        }
        assertTrue(q.total(Item.ROOF_SHEET_G28) > 40);
        assertTrue(q.total(Item.UNIT_BLOCK_200) > 0);
        assertTrue(q.total(Item.UNIT_BLOCK_150) > 0);
        assertTrue(q.total(Item.UNIT_BLOCK_100) > 0);               // the partition
        assertTrue(q.total(CostCategory.FOUNDATION, Item.CEMENT_50KG) > 50);
        assertTrue(q.total(Item.TILE_FLOOR_M2) > 90);
        assertEquals(7, q.total(Item.ELEC_HEATER_POINT) + 6, 0);   // exactly one bathroom
        assertEquals(1, q.total(Item.ELEC_COOKER_POINT), 0);
        assertEquals(1, q.total(Item.WC_SET), 0);                  // the toilet's default fixtures
        assertEquals(1, q.total(Item.SHOWER_SET), 0);
        assertEquals(2, q.total(Item.BASIN_SET), 0);               // toilet + bathroom
        assertEquals(1, q.total(Item.SINK_UNIT), 0);               // kitchen
        assertTrue(q.work(WorkItem.EXCAVATION) > 20);
        assertTrue(q.grossFloorAreaM2 > 120 && q.grossFloorAreaM2 < 130);
    }

    @Test public void wholeUnitItemsAreRoundedUp() {
        QuantitySchedule q = schedule(TestPlans.bungalow(), new EstimateSettings());
        for (MaterialLine l : q.lines) {
            if (l.item.wholeUnits()) assertEquals(l.item + " whole", Math.rint(l.qty), l.qty, 1e-9);
            assertTrue(l.item + " qty >= net", l.qty + 1e-9 >= l.netQty);
        }
    }

    @Test public void everyItemHasARateAndEverySchedulerItemIsPriced() {
        RatePack p = KenyaRatePack.nairobi();
        for (Item it : Item.values()) assertTrue("rate for " + it, p.has(it));
        for (Region r : Region.values()) {
            RatePack rp = KenyaRatePack.forRegion(r);
            for (Item it : Item.values()) assertTrue(r + " has " + it, rp.has(it));
        }
        CostEstimate e = cost(TestPlans.bungalow(), new EstimateSettings());
        assertTrue(e.warnings.toString(), e.warnings.isEmpty());
    }

    @Test public void costPerSquareMetreIsInsideTheSanityRange() {
        EstimateSettings s = new EstimateSettings();
        CostEstimate e = cost(TestPlans.bungalow(), s);
        System.out.printf("reference house: gross %.1f m2, total avg KES %,.0f (min %,.0f, max %,.0f), %,.0f per m2%n",
                e.grossFloorAreaM2, e.totalAvg, e.totalMin, e.totalMax, e.perM2Avg);
        for (CostCategory c : CostCategory.values()) {
            System.out.printf("   %-26s %,12.0f  (%.0f%%)%n", c.label, e.category(c).avg, 100 * e.category(c).avg / e.subtotalAvg);
        }
        assertEquals(CostEstimate.Benchmark.Position.WITHIN, e.benchmark.position);
        assertTrue(e.perM2Min < e.perM2Avg && e.perM2Avg < e.perM2Max);
        assertTrue("labour is a sensible share", e.category(CostCategory.LABOUR).avg / e.subtotalAvg > 0.12
                && e.category(CostCategory.LABOUR).avg / e.subtotalAvg < 0.40);
    }

    @Test public void finishLevelOnlyChangesFinishSensitiveItems() {
        PlanDocument d = TestPlans.bungalow();
        EstimateSettings basic = new EstimateSettings(); basic.finishLevel = FinishLevel.BASIC;
        EstimateSettings std = new EstimateSettings();
        EstimateSettings prem = new EstimateSettings(); prem.finishLevel = FinishLevel.PREMIUM;
        CostEstimate b = cost(d, basic), s = cost(d, std), p = cost(d, prem);
        assertTrue(b.totalAvg < s.totalAvg && s.totalAvg < p.totalAvg);
        assertEquals(s.category(CostCategory.FOUNDATION).avg, p.category(CostCategory.FOUNDATION).avg, 1e-6);
        assertEquals(s.category(CostCategory.ROOFING).avg, p.category(CostCategory.ROOFING).avg, 1e-6);
        assertEquals(s.category(CostCategory.LABOUR).avg, p.category(CostCategory.LABOUR).avg, 1e-6);
        assertTrue(p.category(CostCategory.FINISHES).avg > s.category(CostCategory.FINISHES).avg);
    }

    @Test public void estimatesAreDeterministic() {
        PlanDocument d = TestPlans.bungalow();
        CostEstimate a = cost(d, new EstimateSettings());
        CostEstimate b = cost(d, new EstimateSettings());
        assertEquals(a.totalAvg, b.totalAvg, 0);
        assertEquals(a.lines.size(), b.lines.size());
    }

    @Test public void regionFactorScalesTheTotal() {
        PlanDocument d = TestPlans.bungalow();
        EstimateSettings s = new EstimateSettings();
        QuantitySchedule q = schedule(d, s);
        double nairobi = CostEstimator.estimate(q, KenyaRatePack.forRegion(Region.NAIROBI_METRO), s).totalAvg;
        double north = CostEstimator.estimate(q, KenyaRatePack.forRegion(Region.NORTHERN_ARID), s).totalAvg;
        assertEquals(1.25, north / nairobi, 0.001);
    }

    @Test public void rateOverrideChangesOnlyThatItem() {
        PlanDocument d = TestPlans.bungalow();
        EstimateSettings s = new EstimateSettings();
        QuantitySchedule q = schedule(d, s);
        RatePack base = KenyaRatePack.nairobi();
        RatePack mine = base.copyAs("mine", "My quotes");
        mine.set(Item.CEMENT_50KG, new Rate(700, 800, 900));
        double bags = q.total(Item.CEMENT_50KG);
        double diff = CostEstimator.estimate(q, mine, s).subtotalAvg - CostEstimator.estimate(q, base, s).subtotalAvg;
        assertEquals(bags * 50, diff, 1e-6);
    }

    @Test public void addOnsAndNcaLevy() {
        PlanDocument d = TestPlans.bungalow();
        EstimateSettings s = new EstimateSettings();
        s.preliminariesPct = 0;
        s.overheadProfitPct = 0;
        s.contingencyPct = 5;
        s.professionalFeesPct = 0;
        CostEstimate e = cost(d, s);
        assertEquals(e.subtotalAvg * 1.05, e.totalAvg, 1e-6);
        assertEquals(e.totalAvg * 0.16 / 1.16, e.vatIncludedAvg, 1e-6);

        s.ncaLevy = true;
        s.professionalFeesPct = 6;
        s.preliminariesPct = 0;
        s.overheadProfitPct = 0;
        CostEstimate withLevy = cost(d, s);
        boolean hasLevy = false;
        for (StatutoryItem a : withLevy.addOns) hasLevy |= a.code.equals("NCA_LEVY");
        assertEquals("subtotal " + withLevy.subtotalAvg, withLevy.subtotalAvg >= 5_000_000, hasLevy);
        double expected = withLevy.subtotalAvg * (1 + (5 + 6 + (hasLevy ? 0.5 : 0)) / 100.0);
        assertEquals(expected, withLevy.totalAvg, 1e-6);
    }

    @Test public void defaultAddOnsAreExplicitLinesNotHiddenInTheRates() {
        CostEstimate e = cost(TestPlans.bungalow(), new EstimateSettings());
        double pct = 0;
        for (StatutoryItem a : e.addOns) pct += a.ratePct;
        assertEquals(25.0, pct, 1e-9);                       // 8 prelims + 12 OH&P + 5 contingency
        assertEquals(e.subtotalAvg * 1.25, e.totalAvg, 1e-6);
        EstimateSettings owner = new EstimateSettings();
        owner.preliminariesPct = 0; owner.overheadProfitPct = 0;
        assertTrue(cost(TestPlans.bungalow(), owner).totalAvg < e.totalAvg);
    }

    @Test public void emptyPlanGivesAWarningNotACrash() {
        PlanDocument d = new PlanDocument();
        d.putFloor(new Floor("g", 0, "Ground"));
        CostEstimate e = cost(d, new EstimateSettings());
        assertEquals(0, e.totalAvg, 0);
        assertFalse(e.warnings.isEmpty());
        assertEquals(CostEstimate.Benchmark.Position.UNKNOWN, e.benchmark.position);
    }

    @Test public void openLoopIsReportedNotSilentlyIgnored() {
        PlanDocument d = new PlanDocument();
        d.putFloor(new Floor("g", 0, "Ground"));
        TestPlans.wall(d, "g", 0, 0, 5000, 0, WallKind.EXTERNAL);
        TestPlans.wall(d, "g", 5000, 0, 5000, 4000, WallKind.EXTERNAL);
        QuantitySchedule q = schedule(d, new EstimateSettings());
        boolean warned = false;
        for (String w : q.warnings) warned |= w.contains("no closed rooms");
        assertTrue(q.warnings.toString(), warned);
    }

    @Test public void placedFixturesReplaceTheDefaultsForThatRoom() {
        PlanDocument d = TestPlans.bungalow();
        // draw two WCs and a basin in the toilet room: defaults (1 WC + 1 basin) must not be added on top
        TestPlans.furniture(d, "g", 10000, 8750, FurnitureType.TOILET);
        TestPlans.furniture(d, "g", 11000, 8750, FurnitureType.TOILET);
        TestPlans.furniture(d, "g", 10500, 9500, FurnitureType.SINK);
        QuantitySchedule q = schedule(d, new EstimateSettings());
        assertEquals(2, q.total(Item.WC_SET), 0);
        assertEquals(2, q.total(Item.BASIN_SET), 0);   // 1 placed (toilet room) + 1 default (bathroom)
    }

    @Test public void secondFloorAddsASlabStairsAndRaisesTheCost() {
        PlanDocument d = TestPlans.bungalow();
        EstimateSettings s = new EstimateSettings();
        CostEstimate single = cost(d, s);

        Floor up = new Floor("u", 1, "First Floor");
        d.putFloor(up);
        Wall a = TestPlans.wall(d, "u", 0, 0, 12000, 0, WallKind.EXTERNAL);
        TestPlans.wall(d, "u", 12000, 0, 12000, 10000, WallKind.EXTERNAL);
        TestPlans.wall(d, "u", 12000, 10000, 0, 10000, WallKind.EXTERNAL);
        TestPlans.wall(d, "u", 0, 10000, 0, 0, WallKind.EXTERNAL);
        assertNotNull(a);
        d.put(new Stair("s1", "g", 6000, 5500, StairType.STRAIGHT));
        com.buildvisionai.planner.RoomSync.sync(d, "u", new com.buildvisionai.planner.EditTransaction("r"));
        CostEstimate two = cost(d, s);
        QuantitySchedule q = schedule(d, s);
        assertEquals(2, q.floors);
        assertTrue(q.lines.toString(), q.total(CostCategory.WALLING, Item.STEEL_KG) > 1000);
        assertTrue(q.work(WorkItem.FORMWORK) > 100);
        assertTrue(q.total(Item.HANDRAIL_M) > 3);
        assertTrue(two.totalAvg > single.totalAvg * 1.3);
        // roof is only measured once, on the top floor, so roofing barely changes
        assertEquals(single.category(CostCategory.ROOFING).avg, two.category(CostCategory.ROOFING).avg,
                single.category(CostCategory.ROOFING).avg * 0.10);
        assertTrue(q.grossFloorAreaM2 > 240);
    }

    @Test public void flatRoofSwapsSheetsForASlabAndWaterproofing() {
        EstimateSettings s = new EstimateSettings();
        s.roofType = RoofType.FLAT_SLAB;
        QuantitySchedule q = schedule(TestPlans.bungalow(), s);
        assertEquals(0, q.total(Item.ROOF_SHEET_G28), 0);
        assertTrue(q.total(Item.WATERPROOFING_M2) > 120);
        assertTrue(q.total(CostCategory.ROOFING, Item.CEMENT_50KG) > 100);
    }

    @Test public void hippedRoofHasNoGableMasonryAndLongerRidge() {
        EstimateSettings gabled = new EstimateSettings();
        EstimateSettings hipped = new EstimateSettings();
        hipped.roofType = RoofType.HIPPED_IRON_SHEET;
        QuantitySchedule g = schedule(TestPlans.bungalow(), gabled);
        QuantitySchedule h = schedule(TestPlans.bungalow(), hipped);
        assertTrue(h.total(Item.RIDGE_M) > g.total(Item.RIDGE_M));
        assertTrue(g.total(CostCategory.WALLING, Item.UNIT_BLOCK_200) > h.total(CostCategory.WALLING, Item.UNIT_BLOCK_200));
    }

    @Test public void countiesMapToRegions() {
        assertEquals(47, Counties.all().size());
        assertEquals(Region.NAIROBI_METRO, Counties.regionOf("Kiambu"));
        assertEquals(Region.COAST, Counties.regionOf("mombasa"));
        assertEquals(Region.NORTHERN_ARID, Counties.regionOf("Turkana"));
        assertEquals(Region.NAIROBI_METRO, Counties.regionOf("Atlantis"));
        assertTrue(Counties.isCounty("Nakuru"));
        assertFalse(Counties.isCounty("Atlantis"));
        assertNull(null);
    }

    @Test public void roomTypesDriveFinishes() {
        PlanDocument d = TestPlans.bungalow();
        QuantitySchedule base = schedule(d, new EstimateSettings());
        double tilesBefore = base.total(Item.TILE_FLOOR_M2);
        // turn the living room into a garage: no floor tiles, no ceiling there
        Room living = null;
        for (Room r : d.floorPlan("g").rooms) if (r.name.equals("Living Room")) living = r;
        Room c = living.copy();
        c.roomType = RoomType.GARAGE;
        d.put(c);
        QuantitySchedule after = schedule(d, new EstimateSettings());
        assertTrue(after.total(Item.TILE_FLOOR_M2) < tilesBefore - 20);
        assertTrue(after.total(Item.GYPSUM_BOARD) < base.total(Item.GYPSUM_BOARD));
    }
}
