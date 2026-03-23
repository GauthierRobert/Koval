package com.koval.trainingplanner.data.repository

import com.koval.trainingplanner.data.local.TokenManager
import com.koval.trainingplanner.data.remote.api.NotificationApi
import com.koval.trainingplanner.data.remote.dto.NotificationTokenRequest
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val notificationApi: NotificationApi,
    private val tokenManager: TokenManager,
) {

    suspend fun registerFcmToken() {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            tokenManager.saveFcmToken(token)
            notificationApi.registerToken(NotificationTokenRequest(token))
        } catch (e: Exception) {
            // Silently fail — notifications are not critical
            e.printStackTrace()
        }
    }

    suspend fun unregisterFcmToken() {
        try {
            val token = tokenManager.getFcmToken() ?: return
            notificationApi.unregisterToken(NotificationTokenRequest(token))
        } catch (_: Exception) {
            // Best effort
        }
    }
}
