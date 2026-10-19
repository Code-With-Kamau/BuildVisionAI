package com.buildvisionai.collaboration;

import com.buildvisionai.models.ProjectStatus;
import com.buildvisionai.models.ProjectVersion;
import com.buildvisionai.models.WorkflowState;
import com.buildvisionai.utils.Ids;

import java.util.List;

/**
 * Version rules. A version is never overwritten once it leaves Draft; a new one is cloned from it.
 * Restoring an old version also creates a new one, so history only ever grows.
 */
public final class VersionPolicy {
    private VersionPolicy() {}

    public static boolean isEditable(ProjectVersion v) { return v.workflowState == WorkflowState.DRAFT; }

    /** Status shown in project lists, derived from the head version. */
    public static ProjectStatus projectStatus(ProjectVersion head) {
        return head == null ? ProjectStatus.DRAFT : head.workflowState.toProjectStatus();
    }

    public static int nextNumber(List<ProjectVersion> all) {
        int n = 0;
        for (ProjectVersion v : all) n = Math.max(n, v.number);
        return n + 1;
    }

    /** New draft that starts as a copy of {@code source}; the caller clones the plan rows. */
    public static ProjectVersion newDraftFrom(ProjectVersion source, List<ProjectVersion> all, String createdBy,
                                              long now, String label) {
        ProjectVersion v = new ProjectVersion();
        v.id = Ids.newId();
        v.projectId = source.projectId;
        v.number = nextNumber(all);
        v.label = label != null ? label : "Version " + v.number;
        v.parentVersionId = source.id;
        v.createdAt = now;
        v.createdBy = createdBy;
        v.workflowState = WorkflowState.DRAFT;
        return v;
    }

    public static ProjectVersion restore(ProjectVersion old, List<ProjectVersion> all, String createdBy, long now) {
        return newDraftFrom(old, all, createdBy, now, "Restored from version " + old.number);
    }

    /** What to do when someone opens a version to edit it. */
    public enum OpenAction { EDIT_IN_PLACE, CREATE_NEW_DRAFT }

    public static OpenAction onEditRequested(ProjectVersion v) {
        return isEditable(v) ? OpenAction.EDIT_IN_PLACE : OpenAction.CREATE_NEW_DRAFT;
    }
}
