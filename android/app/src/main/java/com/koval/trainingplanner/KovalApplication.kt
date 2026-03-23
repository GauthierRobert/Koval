package com.koval.trainingplanner

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KovalApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel("default", "General", NotificationManager.IMPORTANCE_HIGH).apply {
                    lightColor = 0xFFFF9D00.toInt()
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 250, 250)
                },
                NotificationChannel("workouts", "Workouts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Training assignments and schedule updates"
                },
                NotificationChannel("sessions", "Club Sessions", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Club session reminders and updates"
                },
            )
            val nm = getSystemService(NotificationManager::class.java)
            channels.forEach { nm.createNotificationChannel(it) }
        }
    }
}
