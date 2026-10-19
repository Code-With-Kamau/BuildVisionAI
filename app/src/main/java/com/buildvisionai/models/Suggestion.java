package com.buildvisionai.models;

/**
 * A proposed change. It never touches the plan by itself: an authorised person must accept it, and
 * accepting applies it as a normal undoable edit to the draft version.
 */
public final class Suggestion {
    public String id;
    public String versionId;
    public String authorId;
    public String authorName;
    public SuggestionSource source;
    public SuggestionStatus status = SuggestionStatus.PROPOSED;
    public String title;
    public String rationale;
    /** JSON of a {@code ChangeProposal} (the only JSON payload in the schema). */
    public String payloadJson;
    public long createdAt;
    public String decidedBy;
    public long decidedAt;
}
