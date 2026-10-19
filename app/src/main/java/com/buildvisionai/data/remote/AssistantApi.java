package com.buildvisionai.data.remote;

import com.buildvisionai.data.remote.dto.AssistantReplyDto;
import com.buildvisionai.data.remote.dto.AssistantRequestDto;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/** The one endpoint of the optional cloud assistant. */
public interface AssistantApi {
    @POST("v1/assistant")
    Call<AssistantReplyDto> ask(@Body AssistantRequestDto body);
}
