package com.buildvisionai.estimation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.buildvisionai.models.Project;
import com.buildvisionai.planner.PlanDocument;

import org.junit.Test;

public class EstimateRunnerTest {
    @Test public void matchesRunningTheEnginesByHand() {
        PlanDocument doc = TestPlansAccess.bungalow();
        EstimateSettings s = new EstimateSettings();
        RatePack pack = KenyaRatePack.nairobi();
        EstimateRunner.Result r = EstimateRunner.run(doc, s, pack);
        CostEstimate byHand = CostEstimator.estimate(MaterialEstimator.estimate(doc, s), pack, s);
        assertEquals(byHand.totalAvg, r.cost.totalAvg, 0);
        assertTrue(r.cost.totalAvg > 0);
        assertEquals(r.quantities.grossFloorAreaM2, r.cost.grossFloorAreaM2, 1e-9);
    }

    @Test public void basePackFollowsTheCountyAndDefaultsToNairobi() {
        Project coast = new Project();
        coast.county = "Mombasa";
        assertEquals(Region.COAST, EstimateRunner.basePack(coast).region);
        assertEquals(Region.NAIROBI_METRO, EstimateRunner.basePack(new Project()).region);
        assertEquals(Region.NAIROBI_METRO, EstimateRunner.basePack(null).region);
    }

    @Test public void runningTwiceGivesTheSameNumbers() {
        PlanDocument doc = TestPlansAccess.bungalow();
        EstimateSettings s = new EstimateSettings();
        RatePack pack = KenyaRatePack.nairobi();
        assertEquals(EstimateRunner.run(doc, s, pack).cost.totalAvg, EstimateRunner.run(doc, s, pack).cost.totalAvg, 0);
    }
}
