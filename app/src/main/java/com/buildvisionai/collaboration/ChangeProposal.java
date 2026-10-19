package com.buildvisionai.collaboration;

import com.buildvisionai.models.SuggestionSource;

import java.util.ArrayList;
import java.util.List;

/**
 * A reviewable set of changes with a reason. Produced by professionals or by the AI assistant and
 * stored on a Suggestion; applied only after an authorised person accepts it.
 */
public final class ChangeProposal {
    public String title;
    public String rationale;
    public SuggestionSource source = SuggestionSource.PROFESSIONAL;
    public final List<ProposalOp> ops = new ArrayList<>();
    /** Estimated change in the average total cost (KES) if applied; NaN when not computed. Negative saves money. */
    public double estimatedDeltaKes = Double.NaN;

    public ChangeProposal() { }

    public ChangeProposal(String title, String rationale, SuggestionSource source) {
        this.title = title;
        this.rationale = rationale;
        this.source = source;
    }

    public ChangeProposal add(ProposalOp op) {
        ops.add(op);
        return this;
    }

    public boolean hasAutomaticChanges() {
        for (ProposalOp op : ops) if (op.type != ProposalOp.Type.NOTE) return true;
        return false;
    }
}
