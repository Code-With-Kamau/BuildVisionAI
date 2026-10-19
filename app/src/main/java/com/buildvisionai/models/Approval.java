package com.buildvisionai.models;

/** One sign-off decision on one version. Approvals are append-only history. */
public final class Approval {
    public String id;
    public String versionId;
    public String userId;
    public String userName;
    public UserRole role;
    public WorkflowState stage;
    public ApprovalDecision decision;
    public String comment;
    public long createdAt;
}
