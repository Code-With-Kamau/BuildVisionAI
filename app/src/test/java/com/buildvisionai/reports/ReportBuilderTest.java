package com.buildvisionai.reports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.buildvisionai.domain.PlanChecker;
import com.buildvisionai.estimation.CostEstimate;
import com.buildvisionai.estimation.CostEstimator;
import com.buildvisionai.estimation.EstimateSettings;
import com.buildvisionai.estimation.KenyaRatePack;
import com.buildvisionai.estimation.MaterialEstimator;
import com.buildvisionai.estimation.QuantitySchedule;
import com.buildvisionai.estimation.TestPlansAccess;
import com.buildvisionai.models.Approval;
import com.buildvisionai.models.ApprovalDecision;
import com.buildvisionai.models.BuildingType;
import com.buildvisionai.models.Project;
import com.buildvisionai.models.ProjectVersion;
import com.buildvisionai.models.UserRole;
import com.buildvisionai.models.WorkflowState;
import com.buildvisionai.planner.PlanDocument;
import com.buildvisionai.reports.data.ReportBuilder;
import com.buildvisionai.reports.data.ReportModel;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ReportBuilderTest {
    private ReportModel build(WorkflowState state, List<Approval> approvals) {
        PlanDocument d = TestPlansAccess.bungalow();
        EstimateSettings s = new EstimateSettings();
        QuantitySchedule q = MaterialEstimator.estimate(d, s);
        CostEstimate c = CostEstimator.estimate(q, KenyaRatePack.nairobi(), s);
        Project p = new Project();
        p.projectName = "Test House";
        p.ownerName = "A. Owner";
        p.county = "Nairobi";
        p.plotWidthM = 15.24; p.plotLengthM = 30.48;
        p.budgetKes = 6_000_000;
        p.buildingType = BuildingType.BUNGALOW;
        ProjectVersion v = new ProjectVersion();
        v.number = 3; v.label = "After engineer review"; v.workflowState = state;
        return ReportBuilder.build(p, v, d, q, c, approvals, PlanChecker.check(d, p), "2026-10-10 09:00");
    }

    @Test public void reportCarriesPlanQuantitiesCostsAndTheDisclaimer() {
        ReportModel m = build(WorkflowState.DRAFT, new ArrayList<Approval>());
        assertEquals("Test House", m.projectName);
        assertEquals("Version 3 - After engineer review", m.versionText);
        assertEquals(1, m.floors.size());
        assertEquals(7, m.floors.get(0).rooms.size());
        assertTrue(m.floors.get(0).rooms.get(0).area.endsWith("m²"));
        assertFalse(m.quantities.isEmpty());
        assertEquals(7, m.costRows.size());
        assertNotNull(m.total);
        assertTrue(m.total.avg.startsWith("KES "));
        assertTrue(m.perM2Text.contains("per m"));
        assertTrue(m.ratePackText.contains("Not surveyed market data"));
        assertTrue(m.disclaimer.contains("never change a design"));
        assertEquals(3, m.addOnRows.size());      // preliminaries, overhead & profit, contingency
    }

    @Test public void onlyApprovedOrLockedReportsAreWithoutAWatermark() {
        for (WorkflowState s : WorkflowState.values()) {
            ReportModel m = build(s, new ArrayList<Approval>());
            if (s == WorkflowState.APPROVED || s == WorkflowState.LOCKED) assertNull(s.name(), m.watermark);
            else assertEquals(s.name(), "DRAFT - NOT APPROVED", m.watermark);
        }
    }

    @Test public void signOffsAreListed() {
        Approval a = new Approval();
        a.userName = "Eng. Otieno";
        a.role = UserRole.ENGINEER;
        a.stage = WorkflowState.PROFESSIONAL_REVIEW;
        a.decision = ApprovalDecision.APPROVED;
        a.comment = "Footings OK";
        List<Approval> l = new ArrayList<>();
        l.add(a);
        ReportModel m = build(WorkflowState.OWNER_REVIEW, l);
        assertEquals(1, m.signOffs.size());
        assertEquals("Engineer", m.signOffs.get(0).role);
        assertEquals("Approved", m.signOffs.get(0).decision);
        assertEquals("Professional review", m.signOffs.get(0).stage);
    }

    @Test public void signOffDatesAreReadableEastAfricaTime() {
        // 2026-10-11 10:05:00 UTC is 13:05 in Nairobi (UTC+3, no daylight saving)
        long utc = java.time.ZonedDateTime.of(2026, 10, 11, 10, 5, 0, 0, java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
        org.junit.Assert.assertEquals("11 Oct 2026 13:05", ReportBuilder.formatDate(utc));
        org.junit.Assert.assertEquals("", ReportBuilder.formatDate(0));
    }
}
