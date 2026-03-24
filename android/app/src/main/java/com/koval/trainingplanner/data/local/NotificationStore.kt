package com.koval.trainingplanner.data.local

import android.content.Context
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class StoredNotification(
    val id: Long,
    val title: String,
    val body: String,
    val type: String?,
    val timestamp: Long,
    val read: Boolean = false,
)

@Singleton
class NotificationStore @Inject constructor(
    @ApplicationContext context: Context,
    moshi: Moshi,
) {
    private val prefs = context.getSharedPreferences("koval_notifications", Context.MODE_PRIVATE)
    private val adapter: JsonAdapter<List<StoredNotification>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, StoredNotification::class.java)
    )

    private companion object {
        const val KEY = "notifications"
        const val MAX_NOTIFICATIONS = 50
    }

    fun getAll(): List<StoredNotification> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return try { adapter.fromJson(json) ?: emptyList() } catch (_: Exception) { emptyList() }
    }

    fun add(title: String, body: String, type: String?) {
        val list = getAll().toMutableList()
        list.add(0, StoredNotification(
            id = System.currentTimeMillis(),
            title = title,
            body = body,
            type = type,
            timestamp = System.currentTimeMillis(),
        ))
        if (list.size > MAX_NOTIFICATIONS) {
            list.subList(MAX_NOTIFICATIONS, list.size).clear()
        }
        prefs.edit().putString(KEY, adapter.toJson(list)).apply()
    }

    fun markAllRead() {
        val list = getAll().map { it.copy(read = true) }
        prefs.edit().putString(KEY, adapter.toJson(list)).apply()
    }

    fun unreadCount(): Int = getAll().count { !it.read }
}
