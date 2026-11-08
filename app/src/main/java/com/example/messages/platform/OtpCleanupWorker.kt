package com.example.messages.platform

import android.content.Context
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.messages.core.database.MessagesDatabase

/** Removes expired OTP cards only; the original SMS remains in the conversation. */
class OtpCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val database = Room.databaseBuilder(applicationContext, MessagesDatabase::class.java, "messages-metadata.db")
            .addMigrations(
                MessagesDatabase.MIGRATION_1_2,
                MessagesDatabase.MIGRATION_2_3,
                MessagesDatabase.MIGRATION_3_4,
                MessagesDatabase.MIGRATION_4_5,
                MessagesDatabase.MIGRATION_5_6,
            )
            .build()
        try {
            database.otpRecordDao().deleteExpired(System.currentTimeMillis())
            Result.success()
        } finally {
            database.close()
        }
    } catch (_: Exception) {
        Result.retry()
    }
}
