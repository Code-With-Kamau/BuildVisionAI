package com.buildvisionai.models;

/** Fine-grained approval state stored on each version. Anything but DRAFT is frozen. */
public enum WorkflowState {
    DRAFT, PROFESSIONAL_REVIEW, OWNER_REVIEW, APPROVED, LOCKED;

    public boolean isFrozen() { return this != DRAFT; }

    public ProjectStatus toProjectStatus() {
        switch (this) {
            case DRAFT: return ProjectStatus.DRAFT;
            case PROFESSIONAL_REVIEW:
            case OWNER_REVIEW: return ProjectStatus.REVIEW;
            case APPROVED: return ProjectStatus.APPROVED;
            default: return ProjectStatus.LOCKED;
        }
    }
}
