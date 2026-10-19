package com.buildvisionai.collaboration;

import com.buildvisionai.models.UserRole;
import com.buildvisionai.models.WorkflowState;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Who may do what. Two layers: the role matrix says what a role may ever do; the version state then
 * removes anything that would change a frozen version. A design under review, approved or locked is
 * never edited in place: changes go into a new draft version.
 */
public final class PermissionPolicy {
    private PermissionPolicy() {}

    private static final Map<UserRole, Set<Permission>> MATRIX = new EnumMap<>(UserRole.class);

    static {
        Set<Permission> common = EnumSet.of(Permission.VIEW_PLAN, Permission.VIEW_COSTS, Permission.COMMENT, Permission.EXPORT_REPORT);

        Set<Permission> owner = EnumSet.copyOf(common);
        owner.addAll(EnumSet.of(Permission.EDIT_PLAN, Permission.EDIT_ESTIMATE, Permission.DECIDE_SUGGESTION,
                Permission.CREATE_VERSION, Permission.SUBMIT_FOR_REVIEW, Permission.OWNER_APPROVAL,
                Permission.LOCK_VERSION, Permission.MANAGE_MEMBERS, Permission.SUGGEST_CHANGES));
        MATRIX.put(UserRole.OWNER, owner);

        Set<Permission> architect = EnumSet.copyOf(common);
        architect.addAll(EnumSet.of(Permission.EDIT_PLAN, Permission.SUGGEST_CHANGES, Permission.DECIDE_SUGGESTION,
                Permission.CREATE_VERSION, Permission.SUBMIT_FOR_REVIEW, Permission.PROFESSIONAL_REVIEW));
        MATRIX.put(UserRole.ARCHITECT, architect);

        Set<Permission> engineer = EnumSet.copyOf(common);
        engineer.addAll(EnumSet.of(Permission.SUGGEST_CHANGES, Permission.PROFESSIONAL_REVIEW));
        MATRIX.put(UserRole.ENGINEER, engineer);

        Set<Permission> qs = EnumSet.copyOf(common);
        qs.addAll(EnumSet.of(Permission.SUGGEST_CHANGES, Permission.PROFESSIONAL_REVIEW, Permission.EDIT_ESTIMATE));
        MATRIX.put(UserRole.QUANTITY_SURVEYOR, qs);

        Set<Permission> contractor = EnumSet.copyOf(common);
        contractor.addAll(EnumSet.of(Permission.SUGGEST_CHANGES, Permission.EDIT_ESTIMATE));
        MATRIX.put(UserRole.CONTRACTOR, contractor);
    }

    /** Permissions that only make sense on a draft; frozen versions refuse them for everyone. */
    private static final Set<Permission> DRAFT_ONLY = EnumSet.of(Permission.EDIT_PLAN, Permission.DECIDE_SUGGESTION, Permission.SUBMIT_FOR_REVIEW);

    public static boolean can(UserRole role, Permission p, WorkflowState state) {
        if (role == null || state == null) return false;
        if (!MATRIX.get(role).contains(p)) return false;
        if (state.isFrozen() && DRAFT_ONLY.contains(p)) return false;
        switch (p) {
            case PROFESSIONAL_REVIEW: return state == WorkflowState.PROFESSIONAL_REVIEW;
            case OWNER_APPROVAL: return state == WorkflowState.OWNER_REVIEW;
            case LOCK_VERSION: return state == WorkflowState.APPROVED;
            case SUGGEST_CHANGES: return state == WorkflowState.DRAFT || state == WorkflowState.PROFESSIONAL_REVIEW;
            case EDIT_ESTIMATE: return state != WorkflowState.LOCKED;
            default: return true;
        }
    }

    /** Role grants ignoring version state (for settings screens that list what a role can do). */
    public static Set<Permission> grantedTo(UserRole role) { return EnumSet.copyOf(MATRIX.get(role)); }
}
