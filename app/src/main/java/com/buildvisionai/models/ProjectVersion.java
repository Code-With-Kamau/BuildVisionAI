package com.buildvisionai.models;

/**
 * An immutable-once-frozen snapshot of a project's plan. Versions are never overwritten: restoring
 * an old version creates a new one that starts as a copy.
 */
public final class ProjectVersion {
    public String id;
    public String projectId;
    /** 1-based, increasing. */
    public int number;
    public String label;
    public String parentVersionId;
    public long createdAt;
    public String createdBy;
    public WorkflowState workflowState = WorkflowState.DRAFT;

    public boolean isFrozen() { return workflowState.isFrozen(); }
}
