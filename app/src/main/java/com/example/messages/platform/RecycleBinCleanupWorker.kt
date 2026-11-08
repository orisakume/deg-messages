package com.example.messages.platform

import android.content.Context
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.messages.core.database.MessagesDatabase

class RecycleBinCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val database = Room.databaseBuilder(applicationContext, MessagesDatabase::class.java, "messages-metadata.db")
            .addMigrations(MessagesDatabase.MIGRATION_1_2, MessagesDatabase.MIGRATION_2_3, MessagesDatabase.MIGRATION_3_4, MessagesDatabase.MIGRATION_4_5, MessagesDatabase.MIGRATION_5_6)
            .build()
        return try {
            val expired = database.deletedItemDao().expired(System.currentTimeMillis())
            database.otpRecordDao().delete(expired.map { it.originalMessageId })
            database.deletedItemDao().deleteExpired(System.currentTimeMillis())
            Result.success()
        } catch (_: Exception) { Result.retry() }
        finally { database.close() }
    }
}
