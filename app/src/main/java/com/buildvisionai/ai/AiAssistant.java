package com.buildvisionai.ai;

/**
 * Contract for AI help. Implementations answer questions and propose changes; they never receive
 * an editor or a document handle, so they cannot modify a design. The remote implementation lives
 * in the data layer; {@link OfflineAssistant} works without a network.
 */
public interface AiAssistant {
    interface Callback {
        void onReply(AssistantReply reply);

        void onError(String message);
    }

    void ask(AssistantRequest request, Callback callback);
}
