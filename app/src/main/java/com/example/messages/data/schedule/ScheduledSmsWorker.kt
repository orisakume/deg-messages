package com.example.messages.data.schedule

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.messages.data.sms.SmsRepository
import com.example.messages.platform.DefaultSmsRole
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScheduledSmsDependencies {
    fun smsRepository(): SmsRepository
}

class ScheduledSmsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.failure()
        val token = inputData.getString("token") ?: return Result.failure()
        val store = ScheduledSmsStore(applicationContext)
        val item = try {
            store.claim(id, token)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return Result.retry()
        } ?: return Result.success()
        return try {
            check(DefaultSmsRole.isHeld(applicationContext))
            val repository = EntryPointAccessors.fromApplication(applicationContext, ScheduledSmsDependencies::class.java).smsRepository()
            repository.sendSms(item.address, item.body, item.subscription, item.token)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            store.finish(token, false)
            Result.failure()
        }
    }
}

class ScheduledSmsTimeoutWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.failure()
        val token = inputData.getString("token") ?: return Result.failure()
        ScheduledSmsStore(applicationContext).expire(id, token)
        return Result.success()
    }
}
