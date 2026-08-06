package com.psd.floramind.network

import retrofit2.http.*
import com.psd.floramind.model.network.DeepSeekRequest
import com.psd.floramind.model.network.DeepSeekResponse

interface DeepSeekApi {
    @POST("v1/chat/completions")
    @Headers("Content-Type: application/json")
    suspend fun chatCompletion(
        @Header("Authorization") auth: String,
        @Body request: DeepSeekRequest
    ): DeepSeekResponse
}