package com.buildvisionai.collaboration;

public enum Permission {
    VIEW_PLAN, VIEW_COSTS, COMMENT, SUGGEST_CHANGES,
    /** Edit the drawing of a DRAFT version. */
    EDIT_PLAN,
    /**
     * Change the pricing inputs (the project's own rate quotes) and re-run the estimate. The estimate
     * <em>assumptions</em> (roof type, finish level, slab thicknesses...) are part of the design: they
     * are frozen with the version in the database and are changed on a draft with {@link #EDIT_PLAN},
     * or proposed with {@link #SUGGEST_CHANGES}.
     */
    EDIT_ESTIMATE,
    /** Accept or reject a suggestion (accepting applies it to the draft). */
    DECIDE_SUGGESTION,
    CREATE_VERSION,
    SUBMIT_FOR_REVIEW,
    PROFESSIONAL_REVIEW,
    OWNER_APPROVAL,
    LOCK_VERSION,
    EXPORT_REPORT,
    MANAGE_MEMBERS
}
