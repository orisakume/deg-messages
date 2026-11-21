package com.example.messages.platform

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.messages.data.sms.SmsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OtpCleanupDependencies {
    fun smsRepository(): SmsRepository
}

class OtpCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val repository = EntryPointAccessors.fromApplication(applicationContext, OtpCleanupDependencies::class.java).smsRepository()
        val moveMessages = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(MOVE_MESSAGES, false)
        repository.expireOtps(moveMessages)
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        const val PREFS = "otp_expiry"
        const val MOVE_MESSAGES = "move_expired_messages"

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "otp-expiration-check", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<OtpCleanupWorker>().build(),
            )
        }
    }
}
