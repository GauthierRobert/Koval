package com.koval.trainingplanner.data.remote.api

import com.koval.trainingplanner.data.remote.dto.ChatHistoryDetailDto
import com.koval.trainingplanner.data.remote.dto.ChatHistoryDto
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path

interface ChatApi {

    @GET("api/ai/history")
    suspend fun getHistories(): List<ChatHistoryDto>

    @GET("api/ai/history/{id}")
    suspend fun getHistory(@Path("id") id: String): ChatHistoryDetailDto

    @DELETE("api/ai/history/{id}")
    suspend fun deleteHistory(@Path("id") id: String)
}
