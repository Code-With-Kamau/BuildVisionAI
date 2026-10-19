package com.buildvisionai.data.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.buildvisionai.database.EstimateRows;
import com.buildvisionai.database.PlanRows;
import com.buildvisionai.database.VersionGuard;
import com.buildvisionai.database.entity.ApprovalEntity;
import com.buildvisionai.database.entity.EstimateSettingsEntity;
import com.buildvisionai.database.entity.MemberEntity;
import com.buildvisionai.database.entity.RateOverrideEntity;
import com.buildvisionai.database.entity.SuggestionEntity;
import com.buildvisionai.database.entity.VersionEntity;
import com.buildvisionai.database.entity.WallEntity;
import com.buildvisionai.estimation.CostEstimate;
import com.buildvisionai.estimation.CostEstimator;
import com.buildvisionai.estimation.EstimateSettings;
import com.buildvisionai.estimation.FinishLevel;
import com.buildvisionai.estimation.Item;
import com.buildvisionai.estimation.KenyaRatePack;
import com.buildvisionai.estimation.MaterialEstimator;
import com.buildvisionai.estimation.Rate;
import com.buildvisionai.estimation.RatePack;
import com.buildvisionai.estimation.RoofType;
import com.buildvisionai.estimation.TestPlansAccess;
import com.buildvisionai.models.AppUser;
import com.buildvisionai.models.Approval;
import com.buildvisionai.models.ApprovalDecision;
import com.buildvisionai.models.BuildingType;
import com.buildvisionai.models.Comment;
import com.buildvisionai.models.Door;
import com.buildvisionai.models.EstimateSnapshot;
import com.buildvisionai.models.Member;
import com.buildvisionai.models.PlanElement;
import com.buildvisionai.models.Project;
import com.buildvisionai.models.ProjectVersion;
import com.buildvisionai.models.Room;
import com.buildvisionai.models.Suggestion;
import com.buildvisionai.models.SuggestionSource;
import com.buildvisionai.models.SuggestionStatus;
import com.buildvisionai.models.UserRole;
import com.buildvisionai.models.Wall;
import com.buildvisionai.models.WorkflowState;
import com.buildvisionai.planner.PlanDocument;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Round trips through the table rows. No database is needed: the mappers only touch the row classes. */
public class MapperTest {

    // ---- helpers -------------------------------------------------------------------------

    /** Every public, non-static, non-transient field must match (derived caches are transient on purpose). */
    static void assertSameFields(Object a, Object b) {
        assertSame(a.getClass(), b.getClass());
        for (Field f : a.getClass().getFields()) {
            int m = f.getModifiers();
            if (Modifier.isStatic(m) || Modifier.isTransient(m)) continue;
            try {
                Object x = f.get(a), y = f.get(b);
                if (x != null && !(x instanceof Enum) && x.getClass().getName().startsWith("com.buildvisionai.")) {
                    assertSameFields(x, y);                              // nested value object (e.g. Wastage)
                    continue;
                }
                assertTrue(a.getClass().getSimpleName() + "." + f.getName() + ": " + x + " vs " + y, Objects.equals(x, y));
            } catch (IllegalAccessException ex) {
                throw new AssertionError(ex);
            }
        }
    }

    static void assertSamePlan(PlanDocument a, PlanDocument b) {
        assertEquals(a.floors().size(), b.floors().size());
        for (int i = 0; i < a.floors().size(); i++) assertSameFields(a.floors().get(i), b.floors().get(i));
        assertEquals(a.elementCount(), b.elementCount());
        for (PlanElement e : a.all()) {
            PlanElement o = b.get(e.id);
            assertTrue("missing " + e.id, o != null);
            assertSameFields(e, o);
        }
    }

    static int count(PlanDocument d, Class<?> c) {
        int n = 0;
        for (PlanElement e : d.all()) if (c.isInstance(e)) n++;
        return n;
    }

    // ---- plan --------------------------------------------------------------------------------

    @Test public void planRoundTripKeepsEveryElementAndEveryField() {
        PlanDocument d = TestPlansAccess.bungalow();
        List<String> warnings = new ArrayList<>();
        PlanRows rows = PlanMapper.toRows("v1", d, warnings);
        assertTrue(warnings.toString(), warnings.isEmpty());
        assertEquals(1, rows.floors.size());
        assertEquals(9, rows.walls.size());
        assertEquals(8, rows.doors.size());
        assertEquals(8, rows.windows.size());
        assertEquals(7, rows.rooms.size());
        assertTrue("every room lists its walls", rows.roomWalls.size() >= 7 * 4);
        rows.requireVersion("v1");

        PlanDocument back = PlanMapper.toDocument(rows, warnings);
        assertTrue(warnings.toString(), warnings.isEmpty());
        assertSamePlan(d, back);
    }

    @Test public void aRoundTrippedPlanIsEstimatedExactlyLikeTheOriginal() {
        PlanDocument d = TestPlansAccess.bungalow();
        PlanDocument back = PlanMapper.toDocument(PlanMapper.toRows("v1", d, null), null);
        EstimateSettings s = new EstimateSettings();
        RatePack p = KenyaRatePack.nairobi();
        CostEstimate a = CostEstimator.estimate(MaterialEstimator.estimate(d, s), p, s);
        CostEstimate b = CostEstimator.estimate(MaterialEstimator.estimate(back, s), p, s);
        assertEquals(a.totalAvg, b.totalAvg, 0.0);
        assertEquals(a.grossFloorAreaM2, b.grossFloorAreaM2, 0.0);
        assertEquals(a.lines.size(), b.lines.size());
        for (int i = 0; i < a.lines.size(); i++) {
            assertEquals(a.lines.get(i).item, b.lines.get(i).item);
            assertEquals(a.lines.get(i).qty, b.lines.get(i).qty, 0.0);
        }
    }

    @Test public void roomWallOrderSurvives() {
        PlanDocument d = TestPlansAccess.bungalow();
        PlanDocument back = PlanMapper.toDocument(PlanMapper.toRows("v1", d, null), null);
        for (PlanElement e : d.all()) {
            if (!(e instanceof Room)) continue;
            assertEquals(((Room) e).wallIds, ((Room) back.get(e.id)).wallIds);
        }
    }

    @Test public void rowsOfAnotherVersionAreRefused() {
        PlanRows rows = PlanMapper.toRows("v1", TestPlansAccess.bungalow(), null);
        rows.requireVersion("v1");
        try {
            rows.requireVersion("v2");
            fail("rows of v1 must not be written into v2");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("v1"));
        }
    }

    @Test public void orphansAreLeftOutAndReportedInsteadOfBreakingTheSave() {
        PlanDocument d = TestPlansAccess.bungalow();
        Door door = null;
        for (PlanElement e : d.all()) if (e instanceof Door) { door = (Door) e; break; }
        d.remove(door.wallId);                                   // the wall goes, its door stays in memory
        List<String> warnings = new ArrayList<>();
        PlanRows rows = PlanMapper.toRows("v1", d, warnings);
        assertEquals(8, rows.walls.size());
        assertTrue(warnings.toString(), warnings.toString().contains("Door " + door.id));
        for (var r : rows.doors) assertNotEquals(door.id, r.id);
        // every row that is written points at something that is written
        Set<String> wallIds = new HashSet<>();
        for (WallEntity w : rows.walls) wallIds.add(w.id);
        for (var r : rows.doors) assertTrue(wallIds.contains(r.wallId));
        for (var r : rows.windows) assertTrue(wallIds.contains(r.wallId));
        for (var r : rows.roomWalls) assertTrue(wallIds.contains(r.wallId));
    }

    @Test public void anElementOnAMissingFloorIsLeftOut() {
        PlanDocument d = TestPlansAccess.bungalow();
        d.removeFloor("g");
        List<String> warnings = new ArrayList<>();
        PlanRows rows = PlanMapper.toRows("v1", d, warnings);
        assertEquals(0, rows.totalRows());
        assertFalse(warnings.isEmpty());
    }

    @Test public void unknownEnumNamesFallBackWithAWarning() {
        PlanRows rows = PlanMapper.toRows("v1", TestPlansAccess.bungalow(), null);
        rows.walls.get(0).kind = "BAMBOO";
        rows.doors.get(0).doorType = "";
        List<String> warnings = new ArrayList<>();
        PlanDocument back = PlanMapper.toDocument(rows, warnings);
        assertEquals(2, warnings.size());
        assertTrue(warnings.get(0).contains("BAMBOO"));
        assertEquals(rows.walls.size(), count(back, Wall.class));
    }

    // ---- projects and versions ----------------------------------------------------------------

    @Test public void projectAndVersionRoundTrip() {
        Project p = new Project();
        p.projectId = "p1"; p.projectName = "Kitengela house"; p.ownerName = "Wanjiru"; p.location = "Kitengela"; p.county = "Kajiado";
        p.plotWidthM = 15.24; p.plotLengthM = 30.48; p.budgetKes = 6_500_000; p.buildingType = BuildingType.RENTAL_UNITS;
        p.createdAt = 1L; p.updatedAt = 2L; p.headVersionId = "v1";
        Project back = ProjectMapper.toModel(ProjectMapper.toEntity(p, false), null);
        assertSameFields(p, back);

        ProjectVersion v = new ProjectVersion();
        v.id = "v2"; v.projectId = "p1"; v.number = 2; v.label = "After review"; v.parentVersionId = "v1";
        v.createdAt = 5L; v.createdBy = "u1"; v.workflowState = WorkflowState.OWNER_REVIEW;
        assertSameFields(v, ProjectMapper.toModel(ProjectMapper.toEntity(v), null));
    }

    @Test public void anUnknownWorkflowStateIsReadAsLockedNeverAsDraft() {
        ProjectVersion v = new ProjectVersion();
        v.id = "v"; v.projectId = "p";
        VersionEntity e = ProjectMapper.toEntity(v);
        e.workflowState = "SOMETHING_NEW";
        List<String> warnings = new ArrayList<>();
        assertEquals(WorkflowState.LOCKED, ProjectMapper.toModel(e, warnings).workflowState);
        assertEquals(1, warnings.size());
    }

    // ---- estimates ------------------------------------------------------------------------------

    @Test public void estimateSettingsRoundTripIncludingWastage() {
        EstimateSettings s = new EstimateSettings();
        s.finishLevel = FinishLevel.PREMIUM; s.roofType = RoofType.HIPPED_TILE; s.roofPitchDeg = 27.5; s.ncaLevy = true;
        s.windowGrills = false; s.waterTankLitres = 10_000; s.overheadProfitPct = 9.5;
        s.wastage.mortar = 0.3; s.wastage.timber = 0.15; s.wastage.masonryUnits = 0.07;
        EstimateSettingsEntity e = EstimateMapper.toEntity("v1", s);
        List<String> warnings = new ArrayList<>();
        EstimateSettings back = EstimateMapper.toSettings(e, warnings);
        assertTrue(warnings.isEmpty());
        assertSameFields(s, back);
    }

    @Test public void aStoredEstimateReadsBackExactlyAsPriced() {
        PlanDocument d = TestPlansAccess.bungalow();
        EstimateSettings s = new EstimateSettings();
        CostEstimate est = CostEstimator.estimate(MaterialEstimator.estimate(d, s), KenyaRatePack.nairobi(), s);
        EstimateRows rows = EstimateMapper.toRows("e1", "v1", 123L, est);
        assertEquals(est.lines.size(), rows.lines.size());
        assertEquals(est.addOns.size(), rows.addOns.size());
        EstimateSnapshot snap = EstimateMapper.toSnapshot(rows);
        assertEquals(est.totalAvg, snap.totalAvg, 0.0);
        assertEquals(est.totalMin, snap.totalMin, 0.0);
        assertEquals(est.totalMax, snap.totalMax, 0.0);
        assertEquals(est.ratePackId, snap.ratePackId);
        assertEquals(est.ratePackAsOf, snap.ratePackAsOf);
        assertEquals(est.finishLevel.name(), snap.finishLevel);
        double lines = 0;
        for (EstimateSnapshot.Line l : snap.lines) lines += l.totalAvg;
        assertEquals("stored lines add up to the stored subtotal", est.subtotalAvg, lines, 0.01);
        double addOns = 0;
        for (EstimateSnapshot.AddOn a : snap.addOns) addOns += a.amountAvg;
        assertTrue(addOns > 0);
        assertEquals("subtotal + add-ons = total", snap.totalAvg, snap.subtotalAvg + addOns, 1.0);
        // line numbers are the ordering key and are unique
        Set<Integer> nos = new HashSet<>();
        for (EstimateSnapshot.Line l : snap.lines) assertTrue(nos.add(l.lineNo));
    }

    @Test public void projectRatesOverrideThePackAndInvalidOnesAreIgnored() {
        RatePack base = KenyaRatePack.nairobi();
        EstimateSettings s = new EstimateSettings();
        PlanDocument d = TestPlansAccess.bungalow();
        double before = CostEstimator.estimate(MaterialEstimator.estimate(d, s), base, s).totalAvg;

        Rate cement = base.base(Item.CEMENT_50KG);
        List<RateOverrideEntity> overrides = new ArrayList<>();
        overrides.add(EstimateMapper.toEntity("p1", Item.CEMENT_50KG, new Rate(cement.min * 1.2, cement.avg * 1.2, cement.max * 1.2), 1L));
        RateOverrideEntity bad = EstimateMapper.toEntity("p1", Item.STEEL_KG, new Rate(0, 0, 0), 1L);       // would zero the steel cost
        RateOverrideEntity unordered = EstimateMapper.toEntity("p1", Item.SAND_T, new Rate(900, 500, 800), 1L);
        RateOverrideEntity unknown = EstimateMapper.toEntity("p1", Item.SAND_T, new Rate(1, 2, 3), 1L);
        unknown.itemCode = "UNOBTAINIUM";
        overrides.add(bad); overrides.add(unordered); overrides.add(unknown);

        List<String> warnings = new ArrayList<>();
        RatePack p = EstimateMapper.applyOverrides(base, overrides, warnings);
        assertEquals(3, warnings.size());
        assertNotEquals(base.id, p.id);
        assertTrue(p.id.endsWith("+project"));
        assertEquals(cement.avg * 1.2, p.base(Item.CEMENT_50KG).avg, 1e-9);
        assertEquals(base.base(Item.STEEL_KG).avg, p.base(Item.STEEL_KG).avg, 0.0);
        assertEquals("the base pack is never modified", cement.avg, base.base(Item.CEMENT_50KG).avg, 0.0);
        assertTrue(CostEstimator.estimate(MaterialEstimator.estimate(d, s), p, s).totalAvg > before);

        // nothing usable -> the original pack object comes back, so the estimate does not claim custom rates
        List<String> w2 = new ArrayList<>();
        assertSame(base, EstimateMapper.applyOverrides(base, overrides.subList(1, 4), w2));
        assertSame(base, EstimateMapper.applyOverrides(base, new ArrayList<>(), null));
    }

    // ---- collaboration -----------------------------------------------------------------------------

    @Test public void collaborationRecordsRoundTrip() {
        AppUser u = new AppUser(); u.id = "u1"; u.displayName = "Amina"; u.email = "a@example.com"; u.phone = "0700000000"; u.remoteId = "r1";
        assertSameFields(u, CollaborationMapper.toModel(CollaborationMapper.toEntity(u)));

        Member m = new Member(); m.projectId = "p1"; m.userId = "u1"; m.displayName = "Amina"; m.role = UserRole.ARCHITECT; m.registrationNo = "A123"; m.addedAt = 9;
        assertSameFields(m, CollaborationMapper.toModel(CollaborationMapper.toEntity(m), null));

        Comment c = new Comment(); c.id = "c1"; c.versionId = "v1"; c.elementId = "w1"; c.authorId = "u1"; c.authorName = "Amina";
        c.authorRole = UserRole.ENGINEER; c.text = "Check lintel"; c.createdAt = 3; c.resolved = true;
        assertSameFields(c, CollaborationMapper.toModel(CollaborationMapper.toEntity(c), null));

        Suggestion s = new Suggestion(); s.id = "s1"; s.versionId = "v1"; s.authorId = "u1"; s.authorName = "Amina"; s.source = SuggestionSource.PROFESSIONAL;
        s.status = SuggestionStatus.ACCEPTED; s.title = "Widen door"; s.rationale = "Access"; s.payloadJson = "{\"ops\":[]}"; s.createdAt = 4; s.decidedBy = "u2"; s.decidedAt = 5;
        assertSameFields(s, CollaborationMapper.toModel(CollaborationMapper.toEntity(s), null));

        Approval a = new Approval(); a.id = "a1"; a.versionId = "v1"; a.userId = "u1"; a.userName = "Amina"; a.role = UserRole.ARCHITECT;
        a.stage = WorkflowState.PROFESSIONAL_REVIEW; a.decision = ApprovalDecision.APPROVED; a.comment = "ok"; a.createdAt = 6;
        assertSameFields(a, CollaborationMapper.toModel(CollaborationMapper.toEntity(a), null));
    }

    @Test public void unrecognisedCollaborationValuesFallBackToTheCautiousChoice() {
        MemberEntity m = new MemberEntity(); m.projectId = "p"; m.userId = "u"; m.role = "SUPERUSER";
        List<String> w = new ArrayList<>();
        assertEquals(UserRole.CONTRACTOR, CollaborationMapper.toModel(m, w).role);              // least privileged

        SuggestionEntity s = new SuggestionEntity(); s.id = "s"; s.status = "AUTO_APPLIED"; s.source = "ROBOT";
        Suggestion sm = CollaborationMapper.toModel(s, w);
        assertEquals(SuggestionStatus.WITHDRAWN, sm.status);                                     // will not be applied
        assertEquals(SuggestionSource.AI, sm.source);

        ApprovalEntity a = new ApprovalEntity(); a.id = "a"; a.role = "?"; a.stage = "?"; a.decision = "MAYBE";
        Approval am = CollaborationMapper.toModel(a, w);
        assertEquals(ApprovalDecision.CHANGES_REQUESTED, am.decision);                           // does not count as approval
        assertEquals(WorkflowState.DRAFT, am.stage);
        assertEquals(6, w.size());                                                              // 1 member + 2 suggestion + 3 approval
    }

    // ---- version guard SQL -----------------------------------------------------------------------------

    @Test public void guardStatementsAreCompleteAndDroppable() {
        List<String> create = VersionGuard.createStatements();
        List<String> drop = VersionGuard.dropStatements();
        int tables = VersionGuard.GUARDED_TABLES.size();
        assertEquals(10, tables);
        assertEquals(tables * 3 + 4, create.size());           // insert/update/delete per table + 4 version/approval rules
        assertEquals(create.size(), drop.size());
        Set<String> names = new HashSet<>();
        for (String sql : create) {
            assertTrue(sql, sql.startsWith("CREATE TRIGGER IF NOT EXISTS bv_"));
            String name = sql.split(" ")[5];
            assertTrue("unique trigger name " + name, names.add(name));
            assertTrue(sql, sql.contains("RAISE(ABORT, 'BV_"));
        }
        for (String sql : drop) assertTrue(names.contains(sql.substring("DROP TRIGGER IF EXISTS ".length())));
    }
}
