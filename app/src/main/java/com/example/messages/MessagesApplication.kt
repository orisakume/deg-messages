package com.example.messages

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.example.messages.platform.OtpCleanupWorker
import com.example.messages.platform.RecycleBinCleanupWorker

@HiltAndroidApp
class MessagesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread({
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "otp-expiration-cleanup",
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<OtpCleanupWorker>(15, TimeUnit.MINUTES).build(),
            )
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "recycle-bin-retention-cleanup",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RecycleBinCleanupWorker>(24, TimeUnit.HOURS).build(),
            )
        }, "maintenance-work-setup").start()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MESSAGE_CHANNEL_ID,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "New SMS messages" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val MESSAGE_CHANNEL_ID = "messages"
    }
}
