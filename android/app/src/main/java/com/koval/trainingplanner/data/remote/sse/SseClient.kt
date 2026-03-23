package com.koval.trainingplanner.data.remote.sse

import com.koval.trainingplanner.BuildConfig
import com.koval.trainingplanner.data.local.TokenManager
import com.koval.trainingplanner.domain.model.SseEvent
import com.koval.trainingplanner.domain.model.SseEventType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SseClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val tokenManager: TokenManager,
) {

    fun streamChat(message: String, chatHistoryId: String?): Flow<SseEvent> = callbackFlow {
        val json = JSONObject().apply {
            put("message", message)
            chatHistoryId?.let { put("chatHistoryId", it) }
        }

        val request = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/api/ai/chat/stream")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .apply {
                tokenManager.getToken()?.let {
                    addHeader("Authorization", "Bearer $it")
                }
            }
            .addHeader("Accept", "text/event-stream")
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val eventType = type?.let { SseEventType.fromString(it) } ?: SseEventType.CONTENT
                trySend(SseEvent(eventType, data))
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(t ?: Exception("SSE stream failed: ${response?.code}"))
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }
        }

        val eventSource = EventSources.createFactory(okHttpClient)
            .newEventSource(request, listener)

        awaitClose { eventSource.cancel() }
    }
}
