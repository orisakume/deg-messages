package com.example.messages.data.recyclebin

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.BaseColumns
import android.provider.Telephony
import com.example.messages.core.database.DeletedItemDao
import com.example.messages.core.database.DeletedItemEntity
import com.example.messages.core.database.OtpRecordDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Singleton
class RecycleBinRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DeletedItemDao,
    private val otpDao: OtpRecordDao,
) {
    fun observe() = dao.observeAll()

    fun isEnabled(): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    suspend fun deleteMessages(ids: List<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val rows = mutableListOf<DeletedItemEntity>()
        ids.chunked(500).forEach { chunk ->
            val marks = chunk.joinToString(",") { "?" }
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                PROJECTION,
                "${BaseColumns._ID} IN ($marks)",
                chunk.map(Long::toString).toTypedArray(), null,
            )?.use { c ->
                while (c.moveToNext()) rows += c.toDeletedItem()
            }
        }
        val recoverable = isEnabled()
        if (rows.isNotEmpty() && recoverable) dao.insert(rows)
        val marks = ids.joinToString(",") { "?" }
        context.contentResolver.delete(Telephony.Sms.CONTENT_URI, "${BaseColumns._ID} IN ($marks)", ids.map(Long::toString).toTypedArray())
        if (!recoverable) otpDao.delete(ids)
    }

    suspend fun deleteThreads(threadIds: List<Long>) = withContext(Dispatchers.IO) {
        if (threadIds.isEmpty()) return@withContext
        val marks = threadIds.joinToString(",") { "?" }
        val ids = mutableListOf<Long>()
        context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(BaseColumns._ID), "${Telephony.Sms.THREAD_ID} IN ($marks)", threadIds.map(Long::toString).toTypedArray(), null)?.use { c ->
            while (c.moveToNext()) ids += c.getLong(0)
        }
        deleteMessages(ids)
    }

    suspend fun restore(ids: List<Long>) = withContext(Dispatchers.IO) {
        val records = dao.observeAll().kotlinxFirst().filter { it.id in ids }
        records.forEach { record ->
            val thread = Telephony.Threads.getOrCreateThreadId(context, setOf(record.address))
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, record.address); put(Telephony.Sms.BODY, record.body)
                put(Telephony.Sms.DATE, record.date); put(Telephony.Sms.DATE_SENT, record.date)
                put(Telephony.Sms.READ, if (record.read) 1 else 0); put(Telephony.Sms.SEEN, if (record.read) 1 else 0)
                put(Telephony.Sms.TYPE, record.type); put(Telephony.Sms.STATUS, record.status)
                put(Telephony.Sms.THREAD_ID, thread); if (record.subscriptionId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, record.subscriptionId)
            }
            // Preserve the original SMS type. Restoring every non-inbox item through the Sent URI
            // silently turns failed and pending messages into sent messages.
            val restored = context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
                ?: error("Android could not restore one or more messages.")
            val newId = ContentUris.parseId(restored)
            otpDao.retarget(record.originalMessageId, newId)
            dao.delete(listOf(record.id))
        }
    }

    suspend fun permanentlyDelete(ids: List<Long>) {
        val records = dao.observeAll().kotlinxFirst().filter { it.id in ids }
        otpDao.delete(records.map(DeletedItemEntity::originalMessageId))
        dao.delete(ids)
    }
    suspend fun empty() {
        val records = dao.observeAll().kotlinxFirst()
        otpDao.delete(records.map(DeletedItemEntity::originalMessageId))
        dao.clear()
    }

    private fun android.database.Cursor.toDeletedItem(): DeletedItemEntity {
        val deletedAt = System.currentTimeMillis()
        val retention = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_DAYS, 30).coerceIn(7, 30)
        return DeletedItemEntity(
            originalMessageId = getLong(0), threadId = getLong(1), address = getString(2).orEmpty(), body = getString(3).orEmpty(),
            date = getLong(4), read = getInt(5) != 0, type = getInt(6), status = getInt(7), subscriptionId = getInt(8),
            deletedAt = deletedAt, expiresAt = deletedAt + retention * DAY_MS,
        )
    }

    companion object {
        const val PREFS = "recycle_bin"
        const val KEY_ENABLED = "enabled"
        const val KEY_DAYS = "days"
        const val DAY_MS = 86_400_000L
        private val PROJECTION = arrayOf(BaseColumns._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.READ, Telephony.Sms.TYPE, Telephony.Sms.STATUS, Telephony.Sms.SUBSCRIPTION_ID)
    }
}

private suspend fun <T> Flow<T>.kotlinxFirst(): T = first()
