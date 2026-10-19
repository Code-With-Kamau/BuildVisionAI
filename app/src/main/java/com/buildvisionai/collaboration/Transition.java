package com.buildvisionai.collaboration;

import com.buildvisionai.models.WorkflowState;

/** Outcome of asking the workflow to move a version. On success {@code to} is the new state to persist. */
public final class Transition {
    public final boolean ok;
    public final WorkflowState from;
    public final WorkflowState to;
    public final String error;

    private Transition(boolean ok, WorkflowState from, WorkflowState to, String error) {
        this.ok = ok;
        this.from = from;
        this.to = to;
        this.error = error;
    }

    static Transition ok(WorkflowState from, WorkflowState to) { return new Transition(true, from, to, null); }

    public static Transition denied(WorkflowState from, String why) { return new Transition(false, from, from, why); }
}
