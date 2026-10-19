package com.buildvisionai.estimation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.buildvisionai.planner.PlanDocument;

import java.util.List;
import org.junit.Test;

public class CostBreakdownTest {
    private static CostEstimate referenceEstimate() {
        PlanDocument doc = TestPlansAccess.bungalow();
        EstimateSettings s = new EstimateSettings();
        return CostEstimator.estimate(MaterialEstimator.estimate(doc, s), KenyaRatePack.nairobi(), s);
    }

    @Test public void sharesSumToOneAndFollowCategoryOrder() {
        List<CostBreakdown.Slice> slices = CostBreakdown.slices(referenceEstimate());
        double sum = 0;
        int last = -1;
        for (CostBreakdown.Slice s : slices) {
            sum += s.share;
            assertTrue(s.colorIndex() > last);
            last = s.colorIndex();
            assertTrue(s.avg > 0);
        }
        assertEquals(1.0, sum, 1e-9);
        assertEquals(CostCategory.values().length, slices.size());
    }

    @Test public void arcsCoverTheCircleExactlyOnceWithGaps() {
        List<CostBreakdown.Slice> slices = CostBreakdown.slices(referenceEstimate());
        double[][] arcs = CostBreakdown.arcs(slices, 2.0);
        double covered = 0;
        for (int i = 0; i < arcs.length; i++) {
            assertTrue("slice " + i + " is visible", arcs[i][1] > 0.5);
            covered += arcs[i][1];
            if (i > 0) assertEquals("gap before slice " + i, 2.0, arcs[i][0] - (arcs[i - 1][0] + arcs[i - 1][1]), 1e-9);
        }
        assertEquals(360.0 - 2.0 * arcs.length, covered, 1e-9);
        assertEquals("last slice ends one gap before the full turn", 360.0, arcs[arcs.length - 1][0] + arcs[arcs.length - 1][1] + 1.0, 1e-9);
    }

    @Test public void aTinySliceShrinksTheGapInsteadOfVanishing() {
        CostEstimate e = new CostEstimate();
        for (CostCategory c : CostCategory.values()) e.categories.put(c, new CategoryCost(c));
        e.categories.get(CostCategory.WALLING).avg = 1_000_000;
        e.categories.get(CostCategory.ROOFING).avg = 4;           // 0.0004 % of the total
        List<CostBreakdown.Slice> slices = CostBreakdown.slices(e);
        double[][] arcs = CostBreakdown.arcs(slices, 6.0);
        assertEquals(2, arcs.length);
        assertTrue(arcs[1][1] > 0);
        assertTrue(arcs[0][1] + arcs[1][1] <= 360.0);
    }

    @Test public void singleSliceIsAFullCircleAndEmptyEstimateHasNoSlices() {
        CostEstimate e = new CostEstimate();
        for (CostCategory c : CostCategory.values()) e.categories.put(c, new CategoryCost(c));
        assertTrue(CostBreakdown.slices(e).isEmpty());
        assertEquals("No cost to show yet.", CostBreakdown.describe(CostBreakdown.slices(e)));
        e.categories.get(CostCategory.LABOUR).avg = 500;
        double[][] arcs = CostBreakdown.arcs(CostBreakdown.slices(e), 3.0);
        assertEquals(360.0, arcs[0][1], 0);
        assertEquals(0.0, arcs[0][0], 0);
        assertEquals(CostCategory.LABOUR.ordinal(), CostBreakdown.slices(e).get(0).colorIndex());
    }

    @Test public void spokenDescriptionNamesEveryCategoryWithPercent() {
        String d = CostBreakdown.describe(CostBreakdown.slices(referenceEstimate()));
        for (CostCategory c : CostCategory.values()) assertTrue(d, d.contains(c.label));
        assertTrue(d, d.contains("percent"));
    }
}
