package com.buildvisionai.collaboration;

import com.buildvisionai.models.Approval;
import com.buildvisionai.models.ApprovalDecision;
import com.buildvisionai.models.UserRole;
import com.buildvisionai.models.WorkflowState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Draft -> Professional Review -> Owner Review -> Approved -> Locked.
 * "Changes requested" at either review stage returns the version to Draft. The workflow only
 * decides; callers persist the new state and the approval record.
 */
public final class ApprovalWorkflow {
    private ApprovalWorkflow() {}

    /** Distinct professional approvals needed before the owner is asked. */
    public static final int DEFAULT_REQUIRED_PROFESSIONALS = 1;

    public static Transition submitForReview(WorkflowState current, UserRole actor, boolean planHasContent) {
        if (!PermissionPolicy.can(actor, Permission.SUBMIT_FOR_REVIEW, current)) {
            return Transition.denied(current, current == WorkflowState.DRAFT
                    ? actor.label + "s cannot submit a design for review."
                    : "Only a draft can be submitted for review.");
        }
        if (!planHasContent) return Transition.denied(current, "Draw at least one wall before submitting for review.");
        return Transition.ok(current, WorkflowState.PROFESSIONAL_REVIEW);
    }

    /**
     * A professional reviews the version.
     */
    public static Transition professionalDecision(WorkflowState current, UserRole actor, String actorId,
                                                  ApprovalDecision decision, List<Approval> earlier, int required) {
        if (!PermissionPolicy.can(actor, Permission.PROFESSIONAL_REVIEW, current)) {
            return Transition.denied(current, "Only a professional can review a design in the professional review stage.");
        }
        if (decision == ApprovalDecision.CHANGES_REQUESTED) return Transition.ok(current, WorkflowState.DRAFT);
        Set<String> approvers = new HashSet<>();
        for (Approval a : earlier) {
            if (a.stage == WorkflowState.PROFESSIONAL_REVIEW && a.decision == ApprovalDecision.APPROVED) approvers.add(a.userId);
        }
        approvers.add(actorId);
        return approvers.size() >= Math.max(1, required)
                ? Transition.ok(current, WorkflowState.OWNER_REVIEW)
                : Transition.ok(current, WorkflowState.PROFESSIONAL_REVIEW);
    }

    public static Transition ownerDecision(WorkflowState current, UserRole actor, ApprovalDecision decision) {
        if (!PermissionPolicy.can(actor, Permission.OWNER_APPROVAL, current)) {
            return Transition.denied(current, "Only the owner can approve, and only after professional review.");
        }
        return decision == ApprovalDecision.APPROVED
                ? Transition.ok(current, WorkflowState.APPROVED)
                : Transition.ok(current, WorkflowState.DRAFT);
    }

    public static Transition lock(WorkflowState current, UserRole actor) {
        if (!PermissionPolicy.can(actor, Permission.LOCK_VERSION, current)) {
            return Transition.denied(current, "Only the owner can lock an approved version.");
        }
        return Transition.ok(current, WorkflowState.LOCKED);
    }

    /**
     * The approvals that count for the review round now under way: everything recorded after the
     * latest "changes requested" on this version. Without this, an approval given in a round that was
     * sent back would still count once the design is changed and resubmitted. {@code history} must be
     * in the order the decisions were recorded.
     */
    public static List<Approval> currentRound(List<Approval> history) {
        List<Approval> round = new ArrayList<>();
        for (Approval a : history) {
            if (a.decision == ApprovalDecision.CHANGES_REQUESTED) round.clear();
            else round.add(a);
        }
        return round;
    }
}
