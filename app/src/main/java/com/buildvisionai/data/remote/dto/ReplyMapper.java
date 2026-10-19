package com.buildvisionai.data.remote.dto;

import com.buildvisionai.ai.AssistantReply;
import com.buildvisionai.collaboration.ChangeProposal;
import com.buildvisionai.collaboration.ProposalOp;
import com.buildvisionai.models.SuggestionSource;

/**
 * Turns an untrusted service reply into an {@link AssistantReply}. Whatever the service claims, its
 * proposals are marked as AI-sourced, malformed ones are dropped and the amount is capped, so a
 * faulty or hostile response cannot flood the review queue or pass itself off as a professional.
 * Proposals still change nothing until a person accepts them (and ops are validated again when applied).
 */
public final class ReplyMapper {
    public static final int MAX_TEXT = 4000;
    public static final int MAX_PROPOSALS = 5;
    public static final int MAX_OPS = 20;
    public static final String ORIGIN = "cloud";

    private ReplyMapper() {}

    public static AssistantReply toReply(AssistantReplyDto dto) {
        String text = dto == null || dto.text == null ? "" : dto.text.trim();
        if (text.length() > MAX_TEXT) text = text.substring(0, MAX_TEXT);
        AssistantReply reply = new AssistantReply(text, ORIGIN);
        if (dto == null || dto.proposals == null) return reply;
        for (ChangeProposal p : dto.proposals) {
            if (reply.proposals.size() >= MAX_PROPOSALS) break;
            if (p == null || p.ops == null) continue;
            ChangeProposal clean = new ChangeProposal(
                    p.title == null || p.title.trim().isEmpty() ? "Suggested change" : clip(p.title.trim(), 80),
                    p.rationale == null ? "" : clip(p.rationale.trim(), 600),
                    SuggestionSource.AI);
            clean.estimatedDeltaKes = p.estimatedDeltaKes;
            for (ProposalOp op : p.ops) {
                if (clean.ops.size() >= MAX_OPS) break;
                if (op != null && op.type != null) clean.add(op);
            }
            if (!clean.ops.isEmpty()) reply.proposals.add(clean);
        }
        return reply;
    }

    private static String clip(String s, int max) { return s.length() <= max ? s : s.substring(0, max); }
}
