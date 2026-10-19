package com.buildvisionai.data.remote.dto;

/** Body sent to the assistant service: the question and a text summary of the plan, nothing else. */
public final class AssistantRequestDto {
    public String question;
    public String planSummary;
    /** BCP-47 tag so the service can answer in the app language. */
    public String language;

    public AssistantRequestDto(String question, String planSummary, String language) {
        this.question = question;
        this.planSummary = planSummary;
        this.language = language;
    }
}
