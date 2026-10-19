package com.buildvisionai.data.remote.dto;

import com.buildvisionai.collaboration.ChangeProposal;

import java.util.List;

/** What the assistant service returns. Gson fills this by reflection, so every field may be null and is checked in {@link ReplyMapper}. */
public final class AssistantReplyDto {
    public String text;
    public List<ChangeProposal> proposals;
}
