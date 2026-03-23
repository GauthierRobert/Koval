package com.koval.trainingplanner.data.repository

import com.koval.trainingplanner.data.remote.api.ChatApi
import com.koval.trainingplanner.data.remote.sse.SseClient
import com.koval.trainingplanner.domain.model.ChatHistory
import com.koval.trainingplanner.domain.model.ChatHistoryDetail
import com.koval.trainingplanner.domain.model.ConversationMessage
import com.koval.trainingplanner.domain.model.SseEvent
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatApi: ChatApi,
    private val sseClient: SseClient,
) {

    fun streamChat(message: String, chatHistoryId: String?): Flow<SseEvent> {
        return sseClient.streamChat(message, chatHistoryId)
    }

    suspend fun getHistories(): List<ChatHistory> {
        return chatApi.getHistories().map {
            ChatHistory(
                id = it.id,
                title = it.title,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
            )
        }
    }

    suspend fun getHistory(id: String): ChatHistoryDetail {
        val dto = chatApi.getHistory(id)
        val metadata = dto.metadata?.let {
            ChatHistory(it.id, it.title, it.createdAt, it.updatedAt)
        } ?: ChatHistory(id = dto.id ?: id, title = dto.title)
        val messages = dto.messages?.map {
            ConversationMessage(it.role, it.content)
        } ?: emptyList()
        return ChatHistoryDetail(metadata, messages)
    }

    suspend fun deleteHistory(id: String) {
        chatApi.deleteHistory(id)
    }
}
