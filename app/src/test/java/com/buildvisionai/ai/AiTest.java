package com.buildvisionai.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.buildvisionai.collaboration.ChangeProposal;
import com.buildvisionai.collaboration.ProposalOp;
import com.buildvisionai.estimation.CostEstimate;
import com.buildvisionai.estimation.CostEstimator;
import com.buildvisionai.estimation.EstimateSettings;
import com.buildvisionai.estimation.FinishLevel;
import com.buildvisionai.estimation.KenyaRatePack;
import com.buildvisionai.estimation.MaterialEstimator;
import com.buildvisionai.estimation.RatePack;
import com.buildvisionai.estimation.RoofType;
import com.buildvisionai.models.BuildingType;
import com.buildvisionai.models.Project;
import com.buildvisionai.planner.PlanDocument;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class AiTest {
    static PlanDocument house() { return com.buildvisionai.estimation.TestPlansAccess.bungalow(); }

    static double total(PlanDocument d, EstimateSettings s) {
        return CostEstimator.estimate(MaterialEstimator.estimate(d, s), KenyaRatePack.nairobi(), s).totalAvg;
    }

    @Test public void savingsAreExactReEstimatesNotGuesses() {
        PlanDocument d = house();
        EstimateSettings s = new EstimateSettings();
        RatePack pack = KenyaRatePack.nairobi();
        double base = total(d, s);
        BudgetAdvisor.Report r = BudgetAdvisor.advise(d, s, pack, base * 0.8);
        assertEquals(BudgetAdvisor.Status.OVER, r.status);
        assertEquals(base, r.totalKes, 1e-6);
        assertEquals(base * 0.2, r.gapKes, 1e-6);
        assertFalse(r.options.isEmpty());
        // first option is the biggest, and each equals the real difference when applied alone
        for (int i = 1; i < r.options.size(); i++) assertTrue(r.options.get(i - 1).savingKes >= r.options.get(i).savingKes);
        BudgetAdvisor.Option basic = null;
        for (BudgetAdvisor.Option o : r.options) if (o.title.contains("basic finishes")) basic = o;
        assertNotNull(basic);
        EstimateSettings b = s.copy();
        b.finishLevel = FinishLevel.BASIC;
        assertEquals(base - total(d, b), basic.savingKes, 1e-6);
        assertEquals(-basic.savingKes, basic.proposal.estimatedDeltaKes, 1e-6);
        assertEquals(ProposalOp.Type.SET_FINISH_LEVEL, basic.proposal.ops.get(0).type);
    }

    @Test public void combinedOptionsAreReEstimatedBecauseSavingsAreNotAdditive() {
        PlanDocument d = house();
        EstimateSettings s = new EstimateSettings();
        double base = total(d, s);
        BudgetAdvisor.Report r = BudgetAdvisor.advise(d, s, KenyaRatePack.nairobi(), base * 0.85);
        assertTrue("some combination closes a 15% gap", r.optionsNeeded > 0);
        assertTrue(r.totalAfterNeeded <= base * 0.85);
        double naiveSum = 0;
        for (int i = 0; i < r.optionsNeeded; i++) naiveSum += r.options.get(i).savingKes;
        assertEquals("exact combined total differs from naive subtraction only by interaction effects",
                base - naiveSum, r.totalAfterNeeded, base * 0.03);
        assertTrue(r.summary.contains("over your"));
    }

    @Test public void underBudgetAndNoBudgetStatuses() {
        PlanDocument d = house();
        EstimateSettings s = new EstimateSettings();
        double base = total(d, s);
        assertEquals(BudgetAdvisor.Status.UNDER, BudgetAdvisor.advise(d, s, KenyaRatePack.nairobi(), base * 1.5).status);
        assertEquals(BudgetAdvisor.Status.TIGHT, BudgetAdvisor.advise(d, s, KenyaRatePack.nairobi(), base * 1.02).status);
        BudgetAdvisor.Report none = BudgetAdvisor.advise(d, s, KenyaRatePack.nairobi(), 0);
        assertEquals(BudgetAdvisor.Status.NO_BUDGET, none.status);
        assertTrue(none.options.isEmpty());
    }

    @Test public void tileRoofGetsAnIronSheetOption() {
        PlanDocument d = house();
        EstimateSettings s = new EstimateSettings();
        s.roofType = RoofType.GABLED_TILE;
        BudgetAdvisor.Report r = BudgetAdvisor.advise(d, s, KenyaRatePack.nairobi(), 1);
        boolean found = false;
        for (BudgetAdvisor.Option o : r.options) found |= o.title.contains("iron sheets");
        assertTrue(found);
    }

    @Test public void offlineAssistantAnswersBudgetAndChecksButOnlyReturnsProposals() {
        PlanDocument d = house();
        Project p = new Project();
        p.budgetKes = total(d, new EstimateSettings()) * 0.8;
        p.buildingType = BuildingType.BUNGALOW;
        OfflineAssistant a = new OfflineAssistant(d, p, new EstimateSettings(), KenyaRatePack.nairobi());
        final List<AssistantReply> got = new ArrayList<>();
        a.ask(new AssistantRequest("How can I save money?", ""), new AiAssistant.Callback() {
            public void onReply(AssistantReply r) { got.add(r); }
            public void onError(String m) { throw new AssertionError(m); }
        });
        assertEquals(1, got.size());
        assertEquals("offline", got.get(0).origin);
        assertTrue(got.get(0).text.contains("over your"));
        assertFalse(got.get(0).proposals.isEmpty());
        for (ChangeProposal cp : got.get(0).proposals) assertEquals(com.buildvisionai.models.SuggestionSource.AI, cp.source);

        a.ask(new AssistantRequest("check my plan", ""), new AiAssistant.Callback() {
            public void onReply(AssistantReply r) { got.add(r); }
            public void onError(String m) { throw new AssertionError(m); }
        });
        assertEquals(2, got.size());
        a.ask(new AssistantRequest("what colour should the sofa be", ""), new AiAssistant.Callback() {
            public void onReply(AssistantReply r) { got.add(r); }
            public void onError(String m) { throw new AssertionError(m); }
        });
        assertTrue(got.get(2).text.contains("Offline"));
    }

    @Test public void theAssistantContractCannotReachADocumentOrEditor() throws Exception {
        // AI-assists-only is enforced by types: AiAssistant.ask takes only a request of two strings.
        Method ask = AiAssistant.class.getMethods()[0];
        for (Class<?> t : ask.getParameterTypes()) {
            assertFalse(t.getName(), t.getName().contains("PlanDocument") || t.getName().contains("EditorController"));
        }
        for (java.lang.reflect.Field f : AssistantRequest.class.getFields()) assertEquals(String.class, f.getType());
        for (java.lang.reflect.Field f : AssistantReply.class.getFields()) {
            assertFalse(f.getType().getName().contains("PlanDocument") || f.getType().getName().contains("EditorController"));
        }
    }

    @Test public void summaryContainsNoPersonalDetails() {
        PlanDocument d = house();
        Project p = new Project();
        p.projectName = "Wanjiku Home";
        p.ownerName = "Wanjiku Kamau";
        p.location = "Plot 12, Ruiru Kimbo Road";
        p.county = "Kiambu";
        p.plotWidthM = 15.24; p.plotLengthM = 30.48;
        p.budgetKes = 6_000_000;
        p.buildingType = BuildingType.BUNGALOW;
        EstimateSettings s = new EstimateSettings();
        CostEstimate e = CostEstimator.estimate(MaterialEstimator.estimate(d, s), KenyaRatePack.nairobi(), s);
        String text = PlanSummarizer.summarize(p, d, s, e);
        assertFalse(text.contains("Wanjiku"));
        assertFalse(text.contains("Ruiru"));
        assertFalse(text.contains("Plot 12"));
        assertTrue(text.contains("Kiambu"));
        assertTrue(text.contains("Master Bedroom"));
        assertTrue(text.contains("Budget: KES 6,000,000"));
    }
}
