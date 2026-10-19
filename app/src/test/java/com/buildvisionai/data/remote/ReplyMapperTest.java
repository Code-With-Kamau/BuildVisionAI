package com.buildvisionai.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.buildvisionai.ai.AssistantReply;
import com.buildvisionai.collaboration.ChangeProposal;
import com.buildvisionai.collaboration.ProposalCodec;
import com.buildvisionai.collaboration.ProposalOp;
import com.buildvisionai.data.remote.dto.AssistantReplyDto;
import com.buildvisionai.data.remote.dto.ReplyMapper;
import com.buildvisionai.models.SuggestionSource;

import com.google.gson.Gson;
import java.util.ArrayList;
import org.junit.Test;

public class ReplyMapperTest {
    @Test public void aNullOrEmptyReplyBecomesAnEmptyCloudReply() {
        AssistantReply r = ReplyMapper.toReply(null);
        assertEquals("", r.text);
        assertEquals("cloud", r.origin);
        assertTrue(r.proposals.isEmpty());
        assertEquals("", ReplyMapper.toReply(new AssistantReplyDto()).text);
    }

    @Test public void proposalsAreAlwaysMarkedAsAiWhateverTheServiceClaims() {
        AssistantReplyDto dto = new Gson().fromJson("{\"text\":\" Use 150 mm blocks. \",\"proposals\":[{\"title\":\"Thinner walls\","
                + "\"rationale\":\"Saves cost\",\"source\":\"PROFESSIONAL\",\"ops\":[{\"type\":\"SET_WALL_THICKNESS\",\"targetId\":\"w1\",\"number\":150}]}]}",
                AssistantReplyDto.class);
        AssistantReply r = ReplyMapper.toReply(dto);
        assertEquals("Use 150 mm blocks.", r.text);
        assertEquals(1, r.proposals.size());
        assertEquals(SuggestionSource.AI, r.proposals.get(0).source);
        assertEquals(150, r.proposals.get(0).ops.get(0).number, 0);
    }

    @Test public void malformedAndExcessProposalsAreDroppedOrCapped() {
        AssistantReplyDto dto = new AssistantReplyDto();
        dto.text = "x".repeat(10_000);
        dto.proposals = new ArrayList<>();
        dto.proposals.add(null);
        ChangeProposal noOps = new ChangeProposal("t", "r", SuggestionSource.PROFESSIONAL);
        dto.proposals.add(noOps);
        ChangeProposal badOps = new ChangeProposal("t", "r", SuggestionSource.PROFESSIONAL);
        badOps.ops.add(null);
        badOps.ops.add(new ProposalOp());                         // type == null
        dto.proposals.add(badOps);
        for (int i = 0; i < 9; i++) {
            ChangeProposal p = new ChangeProposal(i % 2 == 0 ? "  " : "y".repeat(200), null, SuggestionSource.PROFESSIONAL);
            for (int k = 0; k < 40; k++) p.add(ProposalOp.note("n" + k));
            dto.proposals.add(p);
        }
        AssistantReply r = ReplyMapper.toReply(dto);
        assertEquals(ReplyMapper.MAX_TEXT, r.text.length());
        assertEquals(ReplyMapper.MAX_PROPOSALS, r.proposals.size());
        for (ChangeProposal p : r.proposals) {
            assertEquals(ReplyMapper.MAX_OPS, p.ops.size());
            assertNotNull(p.rationale);
            assertTrue(p.title.length() <= 80 && !p.title.trim().isEmpty());
            assertEquals(SuggestionSource.AI, p.source);
        }
        assertEquals("Suggested change", r.proposals.get(0).title);
    }

    @Test public void theStoredProposalJsonStillRoundTrips() {
        ChangeProposal p = new ChangeProposal("a", "b", SuggestionSource.AI);
        p.add(ProposalOp.setting(ProposalOp.Type.SET_FINISH_LEVEL, "BASIC", 0));
        ChangeProposal back = ProposalCodec.fromJson(ProposalCodec.toJson(p));
        assertEquals(1, back.ops.size());
        assertEquals("BASIC", back.ops.get(0).text);
    }
}
