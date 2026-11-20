package com.example.messages.data.schedule

import android.content.Context
import android.content.SharedPreferences
import android.telephony.SubscriptionManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.messages.data.sms.canReplyToSmsAddress
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.json.JSONObject

enum class SendState { Queued, Sending, Failed, Unconfirmed }

data class ScheduledSms(
    val id: String,
    val token: String,
    val address: String,
    val body: String,
    val time: Long,
    val subscription: Int,
    val state: SendState,
)

class ScheduledSmsStore(context: Context) {
    private val app = context.applicationContext
    val preferences: SharedPreferences = app.getSharedPreferences("scheduled_sms", Context.MODE_PRIVATE)
    private val work get() = WorkManager.getInstance(app)

    fun list(): List<ScheduledSms> = preferences.all.keys.mapNotNull(::read).sortedBy { it.time }

    fun save(address: String, body: String, time: Long, subscription: Int, previous: ScheduledSms? = null) {
        require(canReplyToSmsAddress(address)) { "Enter a valid phone number." }
        require(body.isNotBlank()) { "Write a message before scheduling." }
        require(time > System.currentTimeMillis()) { "Choose a time in the future." }
        val sim = if (subscription >= 0) subscription else SubscriptionManager.getDefaultSmsSubscriptionId()
        require(sim >= 0) { "Choose a SIM or set a default SMS SIM in Android settings." }
        replace(previous, address.trim(), body, time, sim)
    }

    fun retry(item: ScheduledSms) {
        check(item.state == SendState.Failed)
        replace(item, item.address, item.body, System.currentTimeMillis(), item.subscription)
    }

    private fun replace(previous: ScheduledSms?, address: String, body: String, time: Long, sim: Int) {
        synchronized(lock) {
            if (previous != null) {
                val current = read(previous.id)
                check(current == previous && current.state in setOf(SendState.Queued, SendState.Failed)) {
                    "This message has already started sending."
                }
            }
            val item = ScheduledSms(previous?.id ?: UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                address, body, time, sim, SendState.Queued)
            write(item)
            try {
                enqueue(item).result.get(10, TimeUnit.SECONDS)
            } catch (failure: Exception) {
                if (previous == null) preferences.edit().remove(item.id).commit() else write(previous)
                work.cancelUniqueWork(item.token)
                throw IllegalStateException("Android could not queue this SMS. Try again.", failure)
            }
            previous?.let { work.cancelUniqueWork(it.token) }
        }
    }

    fun cancel(item: ScheduledSms) = synchronized(lock) {
        val current = read(item.id)
        check(current == item && current.state != SendState.Sending) { "This message has already started sending." }
        check(preferences.edit().remove(item.id).commit()) { "Could not update scheduled messages." }
        work.cancelUniqueWork(item.token)
    }

    fun restoreQueuedWork() = synchronized(lock) {
        list().filter { it.state == SendState.Queued }.forEach { enqueue(it) }
    }

    private fun enqueue(item: ScheduledSms): androidx.work.Operation {
        val request = OneTimeWorkRequestBuilder<ScheduledSmsWorker>()
            .setInitialDelay((item.time - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("id" to item.id, "token" to item.token))
            .build()
        return work.enqueueUniqueWork(item.token, ExistingWorkPolicy.KEEP, request)
    }

    internal fun claim(id: String, token: String): ScheduledSms? = synchronized(lock) {
        val item = read(id) ?: return null
        if (item.token != token || item.state != SendState.Queued) return null
        val timeout = OneTimeWorkRequestBuilder<ScheduledSmsTimeoutWorker>()
            .setInputData(workDataOf("id" to id, "token" to token))
            .setInitialDelay(30, TimeUnit.MINUTES)
            .build()
        work.enqueueUniqueWork("timeout-$token", ExistingWorkPolicy.KEEP, timeout).result.get(10, TimeUnit.SECONDS)
        write(item.copy(state = SendState.Sending))
        item
    }

    internal fun expire(id: String, token: String) = synchronized(lock) {
        val item = read(id) ?: return
        if (item.token == token && item.state == SendState.Sending) write(item.copy(state = SendState.Unconfirmed))
    }

    fun finish(token: String, success: Boolean): Unit = synchronized(lock) {
        val item = list().firstOrNull { it.token == token && it.state in setOf(SendState.Sending, SendState.Unconfirmed) } ?: return
        if (success) check(preferences.edit().remove(item.id).commit())
        else write(item.copy(state = SendState.Failed))
        work.cancelUniqueWork("timeout-$token")
        Unit
    }

    private fun write(item: ScheduledSms) {
        val json = JSONObject()
            .put("token", item.token).put("address", item.address).put("body", item.body)
            .put("time", item.time).put("subscription", item.subscription).put("state", item.state.name)
        check(preferences.edit().putString(item.id, json.toString()).commit()) { "Could not save the scheduled message." }
    }

    private fun read(id: String): ScheduledSms? {
        val raw = preferences.getString(id, null) ?: return null
        val json = JSONObject(raw)
        return ScheduledSms(id, json.getString("token"), json.getString("address"), json.getString("body"),
            json.getLong("time"), json.getInt("subscription"), SendState.valueOf(json.getString("state")))
    }

    private companion object { val lock = Any() }
}
