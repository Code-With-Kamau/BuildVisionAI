package com.buildvisionai.ai;

import com.buildvisionai.collaboration.ChangeProposal;

import java.util.ArrayList;
import java.util.List;

/**
 * An assistant's answer. It can only carry text and proposals; there is no way for a reply to edit a
 * plan. Proposals become Suggestions that a person must accept.
 */
public final class AssistantReply {
    public final String text;
    public final List<ChangeProposal> proposals = new ArrayList<>();
    /** "offline" for the built-in rule engine, otherwise the remote service name. */
    public final String origin;

    public AssistantReply(String text, String origin) {
        this.text = text;
        this.origin = origin;
    }
}
