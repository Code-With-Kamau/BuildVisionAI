package com.buildvisionai.ai;

/** What is sent to an assistant: the question and a facts-only summary of the plan (no personal data). */
public final class AssistantRequest {
    public final String question;
    public final String planSummary;

    public AssistantRequest(String question, String planSummary) {
        this.question = question;
        this.planSummary = planSummary;
    }
}
