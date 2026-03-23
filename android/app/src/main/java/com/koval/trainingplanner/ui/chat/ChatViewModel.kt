package com.koval.trainingplanner.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koval.trainingplanner.data.repository.ChatRepository
import com.koval.trainingplanner.domain.model.ChatHistory
import com.koval.trainingplanner.domain.model.Message
import com.koval.trainingplanner.domain.model.SseEventType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val histories: List<ChatHistory> = emptyList(),
    val activeChatId: String? = null,
    val isStreaming: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        loadHistories()
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _uiState.value.isStreaming) return

        val userMsg = Message(
            id = UUID.randomUUID().toString(),
            role = "user",
            content = text,
        )
        val assistantId = UUID.randomUUID().toString()
        val assistantMsg = Message(
            id = assistantId,
            role = "assistant",
            content = "",
            isStreaming = true,
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg + assistantMsg,
                isStreaming = true,
                error = null,
            )
        }

        viewModelScope.launch {
            try {
                chatRepository.streamChat(text, _uiState.value.activeChatId)
                    .collect { event ->
                        when (event.event) {
                            SseEventType.CONTENT -> {
                                _uiState.update { state ->
                                    state.copy(
                                        messages = state.messages.map { msg ->
                                            if (msg.id == assistantId) {
                                                msg.copy(content = msg.content + event.data)
                                            } else msg
                                        }
                                    )
                                }
                            }
                            SseEventType.CONVERSATION_ID -> {
                                _uiState.update { it.copy(activeChatId = event.data) }
                            }
                            else -> { /* tool_call, tool_result, status — logged */ }
                        }
                    }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.id == assistantId) msg.copy(isStreaming = false) else msg
                        },
                        isStreaming = false,
                    )
                }
            }
        }
    }

    fun loadHistories() {
        viewModelScope.launch {
            try {
                val histories = chatRepository.getHistories()
                _uiState.update { it.copy(histories = histories) }
            } catch (_: Exception) {}
        }
    }

    fun loadHistory(id: String) {
        viewModelScope.launch {
            try {
                val detail = chatRepository.getHistory(id)
                _uiState.update {
                    it.copy(
                        activeChatId = detail.metadata.id,
                        messages = detail.messages.map { msg ->
                            Message(
                                id = UUID.randomUUID().toString(),
                                role = msg.role,
                                content = msg.content,
                            )
                        },
                        error = null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun newChat() {
        _uiState.update {
            it.copy(
                messages = emptyList(),
                activeChatId = null,
                error = null,
            )
        }
    }

    fun deleteHistory(id: String) {
        viewModelScope.launch {
            try {
                chatRepository.deleteHistory(id)
                _uiState.update { state ->
                    val newHistories = state.histories.filter { it.id != id }
                    if (state.activeChatId == id) {
                        state.copy(
                            histories = newHistories,
                            messages = emptyList(),
                            activeChatId = null,
                        )
                    } else {
                        state.copy(histories = newHistories)
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
