package com.koval.trainingplanner.data.remote

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.koval.trainingplanner.MainActivity
import com.koval.trainingplanner.R
import com.koval.trainingplanner.data.local.NotificationStore
import com.koval.trainingplanner.data.local.TokenManager
import com.koval.trainingplanner.data.remote.api.NotificationApi
import com.koval.trainingplanner.data.remote.dto.NotificationTokenRequest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class KovalMessagingService : FirebaseMessagingService() {

    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var notificationApi: NotificationApi
    @Inject lateinit var notificationStore: NotificationStore

    override fun onNewToken(token: String) {
        tokenManager.saveFcmToken(token)
        if (tokenManager.getToken() != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    notificationApi.registerToken(NotificationTokenRequest(token))
                } catch (_: Exception) {}
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"]
        val title = message.notification?.title ?: message.data["title"] ?: "Koval"
        val body = message.notification?.body ?: message.data["body"] ?: ""

        // Persist locally for the notification list
        notificationStore.add(title, body, type)

        val channelId = when (type) {
            "TRAINING_ASSIGNED" -> "workouts"
            "SESSION_CREATED" -> "sessions"
            else -> "default"
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("koval://calendar"))
            .setClass(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(0xFFFF9D00.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(this)
                .notify(System.currentTimeMillis().toInt(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS permission not granted
        }
    }
}
