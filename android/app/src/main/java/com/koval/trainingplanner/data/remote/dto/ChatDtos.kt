package com.koval.trainingplanner.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ChatRequest(
    val message: String,
    val chatHistoryId: String? = null,
)

@JsonClass(generateAdapter = true)
data class ChatHistoryDto(
    val id: String,
    val title: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class ConversationMessageDto(
    val role: String,
    val content: String,
)

@JsonClass(generateAdapter = true)
data class ChatHistoryDetailDto(
    val metadata: ChatHistoryDto? = null,
    val messages: List<ConversationMessageDto>? = null,
    // Flat fields for backward compat
    val id: String? = null,
    val title: String? = null,
)
