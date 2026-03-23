package com.koval.trainingplanner.domain.model

data class ChatHistory(
    val id: String,
    val title: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

data class ConversationMessage(
    val role: String, // "user" or "assistant"
    val content: String,
)

data class ChatHistoryDetail(
    val metadata: ChatHistory,
    val messages: List<ConversationMessage>,
)

data class Message(
    val id: String,
    val role: String,
    val content: String,
    val isStreaming: Boolean = false,
)

data class SseEvent(
    val event: SseEventType,
    val data: String,
)
