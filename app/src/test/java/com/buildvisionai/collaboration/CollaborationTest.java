package com.buildvisionai.collaboration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.buildvisionai.estimation.EstimateSettings;
import com.buildvisionai.estimation.FinishLevel;
import com.buildvisionai.models.Approval;
import com.buildvisionai.models.ApprovalDecision;
import com.buildvisionai.models.Floor;
import com.buildvisionai.models.PlanElement;
import com.buildvisionai.models.ProjectStatus;
import com.buildvisionai.models.ProjectVersion;
import com.buildvisionai.models.Room;
import com.buildvisionai.models.RoomType;
import com.buildvisionai.models.SuggestionSource;
import com.buildvisionai.models.UserRole;
import com.buildvisionai.models.Wall;
import com.buildvisionai.models.WallKind;
import com.buildvisionai.models.WallMaterial;
import com.buildvisionai.models.WorkflowState;
import com.buildvisionai.planner.EditorController;
import com.buildvisionai.planner.PlanDocument;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CollaborationTest {

    // ---- permissions ---------------------------------------------------------------------

    @Test public void onlyDraftsCanBeEdited() {
        for (WorkflowState s : WorkflowState.values()) {
            boolean editable = s == WorkflowState.DRAFT;
            assertEquals(s + " owner edit", editable, PermissionPolicy.can(UserRole.OWNER, Permission.EDIT_PLAN, s));
            assertEquals(s + " architect edit", editable, PermissionPolicy.can(UserRole.ARCHITECT, Permission.EDIT_PLAN, s));
        }
    }

    @Test public void rolesCannotDoWhatTheyAreNotGiven() {
        assertFalse(PermissionPolicy.can(UserRole.CONTRACTOR, Permission.EDIT_PLAN, WorkflowState.DRAFT));
        assertFalse(PermissionPolicy.can(UserRole.ENGINEER, Permission.EDIT_PLAN, WorkflowState.DRAFT));
        assertFalse(PermissionPolicy.can(UserRole.QUANTITY_SURVEYOR, Permission.OWNER_APPROVAL, WorkflowState.OWNER_REVIEW));
        assertFalse(PermissionPolicy.can(UserRole.ARCHITECT, Permission.MANAGE_MEMBERS, WorkflowState.DRAFT));
        assertTrue(PermissionPolicy.can(UserRole.OWNER, Permission.MANAGE_MEMBERS, WorkflowState.LOCKED));
        assertTrue(PermissionPolicy.can(UserRole.CONTRACTOR, Permission.SUGGEST_CHANGES, WorkflowState.DRAFT));
        assertTrue(PermissionPolicy.can(UserRole.CONTRACTOR, Permission.VIEW_PLAN, WorkflowState.LOCKED));
        assertTrue(PermissionPolicy.can(UserRole.QUANTITY_SURVEYOR, Permission.EDIT_ESTIMATE, WorkflowState.APPROVED));
        assertFalse(PermissionPolicy.can(UserRole.QUANTITY_SURVEYOR, Permission.EDIT_ESTIMATE, WorkflowState.LOCKED));
        assertFalse(PermissionPolicy.can(null, Permission.VIEW_PLAN, WorkflowState.DRAFT));
    }

    @Test public void reviewPermissionsFollowTheStage() {
        assertTrue(PermissionPolicy.can(UserRole.ENGINEER, Permission.PROFESSIONAL_REVIEW, WorkflowState.PROFESSIONAL_REVIEW));
        assertFalse(PermissionPolicy.can(UserRole.ENGINEER, Permission.PROFESSIONAL_REVIEW, WorkflowState.DRAFT));
        assertFalse(PermissionPolicy.can(UserRole.ENGINEER, Permission.PROFESSIONAL_REVIEW, WorkflowState.OWNER_REVIEW));
        assertTrue(PermissionPolicy.can(UserRole.OWNER, Permission.OWNER_APPROVAL, WorkflowState.OWNER_REVIEW));
        assertFalse(PermissionPolicy.can(UserRole.OWNER, Permission.OWNER_APPROVAL, WorkflowState.PROFESSIONAL_REVIEW));
        assertTrue(PermissionPolicy.can(UserRole.OWNER, Permission.LOCK_VERSION, WorkflowState.APPROVED));
        assertFalse(PermissionPolicy.can(UserRole.OWNER, Permission.LOCK_VERSION, WorkflowState.DRAFT));
    }

    // ---- workflow ------------------------------------------------------------------------

    @Test public void fullApprovalPath() {
        Transition t = ApprovalWorkflow.submitForReview(WorkflowState.DRAFT, UserRole.OWNER, true);
        assertTrue(t.ok);
        assertEquals(WorkflowState.PROFESSIONAL_REVIEW, t.to);

        t = ApprovalWorkflow.professionalDecision(t.to, UserRole.ARCHITECT, "arch1", ApprovalDecision.APPROVED,
                new ArrayList<Approval>(), 1);
        assertTrue(t.ok);
        assertEquals(WorkflowState.OWNER_REVIEW, t.to);

        t = ApprovalWorkflow.ownerDecision(t.to, UserRole.OWNER, ApprovalDecision.APPROVED);
        assertEquals(WorkflowState.APPROVED, t.to);

        t = ApprovalWorkflow.lock(t.to, UserRole.OWNER);
        assertTrue(t.ok);
        assertEquals(WorkflowState.LOCKED, t.to);
    }

    @Test public void changesRequestedSendsTheVersionBackToDraft() {
        Transition t = ApprovalWorkflow.professionalDecision(WorkflowState.PROFESSIONAL_REVIEW, UserRole.ENGINEER, "e1",
                ApprovalDecision.CHANGES_REQUESTED, new ArrayList<Approval>(), 1);
        assertEquals(WorkflowState.DRAFT, t.to);
        t = ApprovalWorkflow.ownerDecision(WorkflowState.OWNER_REVIEW, UserRole.OWNER, ApprovalDecision.CHANGES_REQUESTED);
        assertEquals(WorkflowState.DRAFT, t.to);
    }

    @Test public void twoProfessionalApprovalsAreCountedByDistinctPeople() {
        Approval first = new Approval();
        first.userId = "arch1";
        first.stage = WorkflowState.PROFESSIONAL_REVIEW;
        first.decision = ApprovalDecision.APPROVED;
        List<Approval> earlier = Collections.singletonList(first);

        Transition same = ApprovalWorkflow.professionalDecision(WorkflowState.PROFESSIONAL_REVIEW, UserRole.ARCHITECT, "arch1",
                ApprovalDecision.APPROVED, earlier, 2);
        assertEquals("the same person twice is still one approval", WorkflowState.PROFESSIONAL_REVIEW, same.to);

        Transition other = ApprovalWorkflow.professionalDecision(WorkflowState.PROFESSIONAL_REVIEW, UserRole.ENGINEER, "eng1",
                ApprovalDecision.APPROVED, earlier, 2);
        assertEquals(WorkflowState.OWNER_REVIEW, other.to);
    }

    @Test public void illegalTransitionsAreRefusedWithAReason() {
        Transition t = ApprovalWorkflow.submitForReview(WorkflowState.DRAFT, UserRole.CONTRACTOR, true);
        assertFalse(t.ok);
        assertNotNull(t.error);
        assertEquals(WorkflowState.DRAFT, t.to);
        assertFalse(ApprovalWorkflow.submitForReview(WorkflowState.DRAFT, UserRole.OWNER, false).ok);
        assertFalse(ApprovalWorkflow.submitForReview(WorkflowState.APPROVED, UserRole.OWNER, true).ok);
        assertFalse(ApprovalWorkflow.ownerDecision(WorkflowState.PROFESSIONAL_REVIEW, UserRole.OWNER, ApprovalDecision.APPROVED).ok);
        assertFalse(ApprovalWorkflow.ownerDecision(WorkflowState.OWNER_REVIEW, UserRole.ARCHITECT, ApprovalDecision.APPROVED).ok);
        assertFalse(ApprovalWorkflow.lock(WorkflowState.OWNER_REVIEW, UserRole.OWNER).ok);
        assertFalse(ApprovalWorkflow.professionalDecision(WorkflowState.PROFESSIONAL_REVIEW, UserRole.CONTRACTOR, "c1",
                ApprovalDecision.APPROVED, new ArrayList<Approval>(), 1).ok);
    }

    // ---- versions ------------------------------------------------------------------------

    @Test public void editingAFrozenVersionCreatesANewDraftAndNeverTouchesTheOld() {
        ProjectVersion v1 = new ProjectVersion();
        v1.id = "v1"; v1.projectId = "p"; v1.number = 1; v1.workflowState = WorkflowState.LOCKED;
        assertEquals(VersionPolicy.OpenAction.CREATE_NEW_DRAFT, VersionPolicy.onEditRequested(v1));
        ProjectVersion v2 = VersionPolicy.newDraftFrom(v1, Arrays.asList(v1), "u1", 1000L, null);
        assertEquals(2, v2.number);
        assertEquals("v1", v2.parentVersionId);
        assertEquals(WorkflowState.DRAFT, v2.workflowState);
        assertEquals(WorkflowState.LOCKED, v1.workflowState);
        assertEquals(VersionPolicy.OpenAction.EDIT_IN_PLACE, VersionPolicy.onEditRequested(v2));
        assertFalse(v2.id.equals(v1.id));

        ProjectVersion v3 = VersionPolicy.restore(v1, Arrays.asList(v1, v2), "u1", 2000L);
        assertEquals(3, v3.number);
        assertTrue(v3.label.contains("1"));
    }

    @Test public void projectStatusIsDerivedFromTheHeadVersion() {
        ProjectVersion v = new ProjectVersion();
        v.workflowState = WorkflowState.OWNER_REVIEW;
        assertEquals(ProjectStatus.REVIEW, VersionPolicy.projectStatus(v));
        v.workflowState = WorkflowState.LOCKED;
        assertEquals(ProjectStatus.LOCKED, VersionPolicy.projectStatus(v));
        assertEquals(ProjectStatus.DRAFT, VersionPolicy.projectStatus(null));
    }

    // ---- proposals -----------------------------------------------------------------------

    private EditorController editorWithWall() {
        PlanDocument doc = new PlanDocument();
        doc.putFloor(new Floor("g", 0, "Ground"));
        doc.put(new Wall("w1", "g", 0, 0, 4000, 0, WallKind.EXTERNAL));
        doc.put(new Wall("w2", "g", 4000, 0, 4000, 3000, WallKind.EXTERNAL));
        doc.put(new Wall("w3", "g", 4000, 3000, 0, 3000, WallKind.EXTERNAL));
        doc.put(new Wall("w4", "g", 0, 3000, 0, 0, WallKind.EXTERNAL));
        EditorController c = new EditorController(doc, "g");
        c.updateElements("rooms", new ArrayList<EditorController.ElementEdit>());
        return c;
    }

    @Test public void proposalJsonRoundTripAndGarbageIsRejectedNotThrown() {
        ChangeProposal p = new ChangeProposal("Thicker wall", "Load-bearing", SuggestionSource.PROFESSIONAL);
        p.add(ProposalOp.plan(ProposalOp.Type.SET_WALL_THICKNESS, "w1", null, 225));
        p.add(ProposalOp.setting(ProposalOp.Type.SET_FINISH_LEVEL, "BASIC", 0));
        p.add(ProposalOp.note("Check soil"));
        p.estimatedDeltaKes = -12345.5;
        ChangeProposal back = ProposalCodec.fromJson(ProposalCodec.toJson(p));
        assertNotNull(back);
        assertEquals("Thicker wall", back.title);
        assertEquals(3, back.ops.size());
        assertEquals(ProposalOp.Type.SET_WALL_THICKNESS, back.ops.get(0).type);
        assertEquals(225, back.ops.get(0).number, 0);
        assertEquals("BASIC", back.ops.get(1).text);
        assertEquals(-12345.5, back.estimatedDeltaKes, 1e-9);
        assertEquals(SuggestionSource.PROFESSIONAL, back.source);

        ChangeProposal nan = new ChangeProposal("x", "y", SuggestionSource.AI);
        assertTrue(Double.isNaN(ProposalCodec.fromJson(ProposalCodec.toJson(nan)).estimatedDeltaKes));

        assertNull(ProposalCodec.fromJson(null));
        assertNull(ProposalCodec.fromJson("   "));
        assertNull(ProposalCodec.fromJson("{not json"));
        assertNull(ProposalCodec.fromJson("{\"title\":\"x\",\"ops\":[{\"type\":\"NO_SUCH_TYPE\"}]}"));
    }

    @Test public void acceptedProposalAppliesAsOneUndoStepAndInvalidOpsAreSkipped() {
        EditorController c = editorWithWall();
        EstimateSettings s = new EstimateSettings();
        ChangeProposal p = new ChangeProposal("Rework", "because", SuggestionSource.PROFESSIONAL);
        p.add(ProposalOp.plan(ProposalOp.Type.SET_WALL_THICKNESS, "w1", null, 225));
        p.add(ProposalOp.plan(ProposalOp.Type.SET_WALL_MATERIAL, "w2", "STONE_225", 0));
        p.add(ProposalOp.plan(ProposalOp.Type.SET_WALL_THICKNESS, "w3", null, 5000));   // out of range
        p.add(ProposalOp.plan(ProposalOp.Type.SET_WALL_KIND, "gone", "INTERNAL", 0));    // missing element
        p.add(ProposalOp.plan(ProposalOp.Type.SET_ROOM_TYPE, "w4", "BEDROOM", 0));       // wrong target type
        p.add(ProposalOp.setting(ProposalOp.Type.SET_FINISH_LEVEL, "BASIC", 0));
        p.add(ProposalOp.setting(ProposalOp.Type.SET_ROOF_PITCH, null, 80));            // out of range
        p.add(ProposalOp.note("just a note"));

        ProposalApplier.Result r = ProposalApplier.apply(p, c, s);
        assertEquals(3, r.applied);                  // 2 wall edits + 1 setting
        assertEquals(4, r.skipped.size());
        assertEquals(225, ((Wall) c.doc().get("w1")).thicknessMm, 0);
        assertEquals(WallMaterial.STONE_225, ((Wall) c.doc().get("w2")).material);
        assertEquals(WallMaterial.BLOCK_200.thicknessMm, ((Wall) c.doc().get("w3")).thicknessMm, 0);
        assertEquals(FinishLevel.BASIC, s.finishLevel);
        assertEquals(20, s.roofPitchDeg, 0);

        assertTrue(c.undo());                         // both wall edits come back together
        assertEquals(200, ((Wall) c.doc().get("w1")).thicknessMm, 0);
        assertEquals(WallMaterial.BLOCK_200, ((Wall) c.doc().get("w2")).material);
    }

    @Test public void proposalCannotEditALockedVersion() {
        EditorController c = editorWithWall();
        c.setReadOnly(true);
        ChangeProposal p = new ChangeProposal("t", "r", SuggestionSource.AI);
        p.add(ProposalOp.plan(ProposalOp.Type.SET_WALL_THICKNESS, "w1", null, 225));
        ProposalApplier.apply(p, c, new EstimateSettings());
        assertEquals(200, ((Wall) c.doc().get("w1")).thicknessMm, 0);
    }

    @Test public void nonFiniteNumbersFromAnUntrustedProposalAreRefused() {
        EditorController c = editorWithWall();
        EstimateSettings s = new EstimateSettings();
        ChangeProposal p = new ChangeProposal("t", "r", SuggestionSource.AI);
        p.add(ProposalOp.plan(ProposalOp.Type.SET_WALL_THICKNESS, "w1", null, Double.NaN));
        p.add(ProposalOp.plan(ProposalOp.Type.SET_WALL_HEIGHT, "w1", null, Double.POSITIVE_INFINITY));
        p.add(ProposalOp.setting(ProposalOp.Type.SET_ROOF_PITCH, null, Double.NaN));
        p.add(ProposalOp.setting(ProposalOp.Type.SET_CONTINGENCY_PCT, null, Double.NaN));
        ProposalApplier.Result r = ProposalApplier.apply(p, c, s);
        assertEquals(0, r.applied);
        assertEquals(4, r.skipped.size());
        assertEquals(200, ((Wall) c.doc().get("w1")).thicknessMm, 0);
        assertEquals(20, s.roofPitchDeg, 0);
        assertEquals(5, s.contingencyPct, 0);
    }

    @Test public void roomOpsNeedARoom() {
        EditorController c = editorWithWall();
        c.selection().clear();
        // draw rooms through the sync the controller would do after an edit
        c.updateElements("touch", Collections.singletonList(new EditorController.ElementEdit("w1", e -> ((Wall) e).heightMm = 2700)));
        Room room = null;
        for (PlanElement e : c.doc().all()) if (e instanceof Room) room = (Room) e;
        assertNotNull(room);
        ChangeProposal p = new ChangeProposal("name it", "r", SuggestionSource.PROFESSIONAL);
        p.add(ProposalOp.plan(ProposalOp.Type.RENAME_ROOM, room.id, "Study", 0));
        p.add(ProposalOp.plan(ProposalOp.Type.SET_ROOM_TYPE, room.id, "OFFICE", 0));
        ProposalApplier.Result r = ProposalApplier.apply(p, c, new EstimateSettings());
        assertEquals(2, r.applied);
        Room after = (Room) c.doc().get(room.id);
        assertEquals("Study", after.name);
        assertEquals(RoomType.OFFICE, after.roomType);
    }

    // ---- review rounds --------------------------------------------------------------------------

    private static Approval decision(String user, ApprovalDecision d, WorkflowState stage) {
        Approval a = new Approval();
        a.id = user + d + stage;
        a.userId = user;
        a.role = UserRole.ARCHITECT;
        a.stage = stage;
        a.decision = d;
        return a;
    }

    @Test public void anApprovalFromARoundThatWasSentBackDoesNotCountInTheNextRound() {
        List<Approval> history = new ArrayList<>();
        history.add(decision("anne", ApprovalDecision.APPROVED, WorkflowState.PROFESSIONAL_REVIEW));            // round 1
        history.add(decision("bob", ApprovalDecision.CHANGES_REQUESTED, WorkflowState.PROFESSIONAL_REVIEW));   // sent back to draft
        history.add(decision("cara", ApprovalDecision.APPROVED, WorkflowState.PROFESSIONAL_REVIEW));           // round 2
        List<Approval> round = ApprovalWorkflow.currentRound(history);
        assertEquals(1, round.size());
        assertEquals("cara", round.get(0).userId);

        // two professionals required: cara alone is not enough, and anne's old approval is not reused
        Transition t = ApprovalWorkflow.professionalDecision(WorkflowState.PROFESSIONAL_REVIEW, UserRole.ARCHITECT, "cara",
                ApprovalDecision.APPROVED, round, 2);
        assertEquals(WorkflowState.PROFESSIONAL_REVIEW, t.to);
        // a different professional completes the round
        t = ApprovalWorkflow.professionalDecision(WorkflowState.PROFESSIONAL_REVIEW, UserRole.ENGINEER, "dan",
                ApprovalDecision.APPROVED, round, 2);
        assertEquals(WorkflowState.OWNER_REVIEW, t.to);
        // without the round filter the stale approval would have counted
        t = ApprovalWorkflow.professionalDecision(WorkflowState.PROFESSIONAL_REVIEW, UserRole.ENGINEER, "cara",
                ApprovalDecision.APPROVED, history, 2);
        assertEquals("shows why the filter matters", WorkflowState.OWNER_REVIEW, t.to);
    }

    @Test public void aHistoryWithNoSendBackIsOneRound() {
        List<Approval> history = Arrays.asList(
                decision("anne", ApprovalDecision.APPROVED, WorkflowState.PROFESSIONAL_REVIEW),
                decision("cara", ApprovalDecision.APPROVED, WorkflowState.PROFESSIONAL_REVIEW));
        assertEquals(2, ApprovalWorkflow.currentRound(history).size());
        assertEquals(0, ApprovalWorkflow.currentRound(new ArrayList<Approval>()).size());
    }
}
