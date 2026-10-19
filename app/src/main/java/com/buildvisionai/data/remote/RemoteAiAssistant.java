package com.buildvisionai.data.remote;

import androidx.annotation.NonNull;

import com.buildvisionai.ai.AiAssistant;
import com.buildvisionai.ai.AssistantReply;
import com.buildvisionai.ai.AssistantRequest;
import com.buildvisionai.data.remote.dto.AssistantReplyDto;
import com.buildvisionai.data.remote.dto.AssistantRequestDto;
import com.buildvisionai.data.remote.dto.ReplyMapper;

import java.util.Locale;

import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Asks a cloud service for open-ended help and falls back to the on-device assistant when the
 * network or service fails, so the button always does something useful. Only the question and a
 * text summary of the plan leave the phone (see PlanSummarizer: no owner name, phone or exact
 * location). Replies are untrusted: {@link ReplyMapper} sanitises them, and their proposals are only
 * ever suggestions a person must accept.
 *
 * <p>Retrofit delivers its callbacks on the main thread on Android, which is where the UI wants them.
 */
public final class RemoteAiAssistant implements AiAssistant {
    private final AssistantApi api;
    private final AiAssistant fallback;

    public RemoteAiAssistant(String baseUrl, AiAssistant fallback) {
        String url = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.api = new Retrofit.Builder()
                .baseUrl(url)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AssistantApi.class);
        this.fallback = fallback;
    }

    @Override
    public void ask(AssistantRequest request, AiAssistant.Callback callback) {
        AssistantRequestDto body = new AssistantRequestDto(request.question, request.planSummary,
                Locale.getDefault().toLanguageTag());
        api.ask(body).enqueue(new retrofit2.Callback<AssistantReplyDto>() {
            @Override
            public void onResponse(@NonNull Call<AssistantReplyDto> call, @NonNull Response<AssistantReplyDto> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    fallBack(request, callback);
                    return;
                }
                AssistantReply reply = ReplyMapper.toReply(response.body());
                if (reply.text.isEmpty() && reply.proposals.isEmpty()) {
                    fallBack(request, callback);
                    return;
                }
                callback.onReply(reply);
            }

            @Override
            public void onFailure(@NonNull Call<AssistantReplyDto> call, @NonNull Throwable t) {
                fallBack(request, callback);
            }
        });
    }

    private void fallBack(AssistantRequest request, AiAssistant.Callback callback) {
        fallback.ask(request, new AiAssistant.Callback() {
            @Override public void onReply(AssistantReply reply) {
                AssistantReply marked = new AssistantReply(
                        "Could not reach the online assistant, so this answer comes from your phone.\n\n" + reply.text, reply.origin);
                marked.proposals.addAll(reply.proposals);
                callback.onReply(marked);
            }

            @Override public void onError(String message) { callback.onError(message); }
        });
    }
}
