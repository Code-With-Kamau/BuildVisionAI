package com.buildvisionai.collaboration;

import static com.buildvisionai.collaboration.ReviewGuide.Action.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.buildvisionai.models.ApprovalDecision;
import com.buildvisionai.models.UserRole;
import com.buildvisionai.models.WorkflowState;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ReviewGuideTest {
    @Test
    public void draftCanBeSubmittedByOwnerAndArchitectOnly() {
        assertEquals(Collections.singletonList(SUBMIT), ReviewGuide.actions(UserRole.OWNER, WorkflowState.DRAFT));
        assertEquals(Collections.singletonList(SUBMIT), ReviewGuide.actions(UserRole.ARCHITECT, WorkflowState.DRAFT));
        assertTrue(ReviewGuide.actions(UserRole.ENGINEER, WorkflowState.DRAFT).isEmpty());
        assertTrue(ReviewGuide.actions(UserRole.QUANTITY_SURVEYOR, WorkflowState.DRAFT).isEmpty());
        assertTrue(ReviewGuide.actions(UserRole.CONTRACTOR, WorkflowState.DRAFT).isEmpty());
    }

    @Test
    public void professionalsReviewAndTheOwnerDoesNot() {
        List<ReviewGuide.Action> pro = Arrays.asList(PROFESSIONAL_APPROVE, PROFESSIONAL_REQUEST_CHANGES);
        assertEquals(pro, ReviewGuide.actions(UserRole.ARCHITECT, WorkflowState.PROFESSIONAL_REVIEW));
        assertEquals(pro, ReviewGuide.actions(UserRole.ENGINEER, WorkflowState.PROFESSIONAL_REVIEW));
        assertEquals(pro, ReviewGuide.actions(UserRole.QUANTITY_SURVEYOR, WorkflowState.PROFESSIONAL_REVIEW));
        assertTrue(ReviewGuide.actions(UserRole.OWNER, WorkflowState.PROFESSIONAL_REVIEW).isEmpty());
        assertTrue(ReviewGuide.actions(UserRole.CONTRACTOR, WorkflowState.PROFESSIONAL_REVIEW).isEmpty());
    }

    @Test
    public void onlyTheOwnerApprovesThenLocks() {
        assertEquals(Arrays.asList(OWNER_APPROVE, OWNER_REQUEST_CHANGES), ReviewGuide.actions(UserRole.OWNER, WorkflowState.OWNER_REVIEW));
        assertTrue(ReviewGuide.actions(UserRole.ARCHITECT, WorkflowState.OWNER_REVIEW).isEmpty());
        assertEquals(Collections.singletonList(LOCK), ReviewGuide.actions(UserRole.OWNER, WorkflowState.APPROVED));
        assertTrue(ReviewGuide.actions(UserRole.ARCHITECT, WorkflowState.APPROVED).isEmpty());
        for (UserRole r : UserRole.values()) assertTrue(ReviewGuide.actions(r, WorkflowState.LOCKED).isEmpty());
    }

    @Test
    public void requestsForChangesNeedACommentButApprovalsDoNot() {
        assertTrue(PROFESSIONAL_REQUEST_CHANGES.commentRequired());
        assertTrue(OWNER_REQUEST_CHANGES.commentRequired());
        assertFalse(PROFESSIONAL_APPROVE.commentRequired());
        assertFalse(OWNER_APPROVE.commentRequired());
        assertTrue(PROFESSIONAL_APPROVE.takesComment());
        assertFalse(SUBMIT.takesComment());
        assertFalse(LOCK.takesComment());
        assertEquals(ApprovalDecision.APPROVED, OWNER_APPROVE.decision());
        assertEquals(ApprovalDecision.CHANGES_REQUESTED, PROFESSIONAL_REQUEST_CHANGES.decision());
    }

    /** The buttons offered and the workflow's own answer must agree for every role and state. */
    @Test
    public void everyOfferedActionIsAcceptedByTheWorkflowAndNothingElseIs() {
        for (UserRole role : UserRole.values()) {
            for (WorkflowState state : WorkflowState.values()) {
                List<ReviewGuide.Action> offered = ReviewGuide.actions(role, state);
                assertEquals(role + " " + state + " submit", offered.contains(SUBMIT),
                        ApprovalWorkflow.submitForReview(state, role, true).ok);
                assertEquals(role + " " + state + " professional approve", offered.contains(PROFESSIONAL_APPROVE),
                        ApprovalWorkflow.professionalDecision(state, role, "u1", ApprovalDecision.APPROVED, Collections.emptyList(), 1).ok);
                assertEquals(role + " " + state + " professional changes", offered.contains(PROFESSIONAL_REQUEST_CHANGES),
                        ApprovalWorkflow.professionalDecision(state, role, "u1", ApprovalDecision.CHANGES_REQUESTED, Collections.emptyList(), 1).ok);
                assertEquals(role + " " + state + " owner approve", offered.contains(OWNER_APPROVE),
                        ApprovalWorkflow.ownerDecision(state, role, ApprovalDecision.APPROVED).ok);
                assertEquals(role + " " + state + " owner changes", offered.contains(OWNER_REQUEST_CHANGES),
                        ApprovalWorkflow.ownerDecision(state, role, ApprovalDecision.CHANGES_REQUESTED).ok);
                assertEquals(role + " " + state + " lock", offered.contains(LOCK), ApprovalWorkflow.lock(state, role).ok);
            }
        }
    }

    @Test
    public void knowsWhetherAnyoneOnTheTeamCanAct() {
        assertTrue(ReviewGuide.anyoneCanAct(Arrays.asList(UserRole.OWNER, UserRole.CONTRACTOR), WorkflowState.DRAFT));
        assertFalse(ReviewGuide.anyoneCanAct(Arrays.asList(UserRole.OWNER, UserRole.CONTRACTOR), WorkflowState.PROFESSIONAL_REVIEW));
        assertTrue(ReviewGuide.anyoneCanAct(Arrays.asList(UserRole.OWNER, UserRole.ENGINEER), WorkflowState.PROFESSIONAL_REVIEW));
    }
}
