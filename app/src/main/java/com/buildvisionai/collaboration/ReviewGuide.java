package com.buildvisionai.collaboration;

import com.buildvisionai.models.ApprovalDecision;
import com.buildvisionai.models.UserRole;
import com.buildvisionai.models.WorkflowState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Which review buttons to offer a person on a version: submit, approve or ask for changes, lock.
 * It follows {@link PermissionPolicy} (the same rule the repository enforces), so the screen never
 * offers a button the workflow would refuse. Pure, so the combinations are unit-tested; the wording
 * of each button and of the stage explanations lives in string resources.
 */
public final class ReviewGuide {
    private ReviewGuide() {}

    public enum Action {
        SUBMIT(null),
        PROFESSIONAL_APPROVE(ApprovalDecision.APPROVED),
        PROFESSIONAL_REQUEST_CHANGES(ApprovalDecision.CHANGES_REQUESTED),
        OWNER_APPROVE(ApprovalDecision.APPROVED),
        OWNER_REQUEST_CHANGES(ApprovalDecision.CHANGES_REQUESTED),
        LOCK(null);

        private final ApprovalDecision decision;

        Action(ApprovalDecision decision) { this.decision = decision; }

        /** The decision this action records; null for submitting and locking. */
        public ApprovalDecision decision() { return decision; }

        /** Asking for changes must say what to change, so the author knows what to fix. */
        public boolean commentRequired() { return decision == ApprovalDecision.CHANGES_REQUESTED; }

        /** Whether the person may add a note (approvals and requests); submitting and locking take none. */
        public boolean takesComment() { return decision != null; }
    }

    /** The buttons for this role on a version in this state, in the order to show them. */
    public static List<Action> actions(UserRole role, WorkflowState state) {
        if (role == null || state == null) return Collections.emptyList();
        List<Action> out = new ArrayList<>();
        if (PermissionPolicy.can(role, Permission.SUBMIT_FOR_REVIEW, state)) out.add(Action.SUBMIT);
        if (PermissionPolicy.can(role, Permission.PROFESSIONAL_REVIEW, state)) {
            out.add(Action.PROFESSIONAL_APPROVE);
            out.add(Action.PROFESSIONAL_REQUEST_CHANGES);
        }
        if (PermissionPolicy.can(role, Permission.OWNER_APPROVAL, state)) {
            out.add(Action.OWNER_APPROVE);
            out.add(Action.OWNER_REQUEST_CHANGES);
        }
        if (PermissionPolicy.can(role, Permission.LOCK_VERSION, state)) out.add(Action.LOCK);
        return out;
    }

    /** True when somebody in {@code roles} could act on a version in {@code state}; used to say who it is waiting for. */
    public static boolean anyoneCanAct(Iterable<UserRole> roles, WorkflowState state) {
        for (UserRole r : roles) if (!actions(r, state).isEmpty()) return true;
        return false;
    }
}
