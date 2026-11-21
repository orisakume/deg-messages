package com.example.messages.data.sms

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.BaseColumns
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.example.messages.core.database.ConversationMetadataDao
import com.example.messages.core.database.ConversationMetadataEntity
import com.example.messages.core.database.MessageMetadataDao
import com.example.messages.core.database.MessageMetadataEntity
import com.example.messages.core.database.MessagesDatabase
import com.example.messages.core.database.BlockedNumberDao
import com.example.messages.core.database.BlockedNumberEntity
import com.example.messages.data.sms.ConversationFlags
import com.example.messages.data.sms.ContactDetails
import com.example.messages.data.recyclebin.RecycleBinRepository
import com.example.messages.core.database.SmsSendTrackingEntity
import com.example.messages.core.database.SmsSendTrackingDao
import com.example.messages.core.database.OtpRecordDao
import com.example.messages.core.database.OtpRecordEntity
import com.example.messages.data.otp.OtpDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import com.example.messages.platform.SmsSentReceiver
import com.example.messages.platform.SmsDeliveryStatusReceiver

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class SmsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MessagesDatabase,
    private val conversationMetadataDao: ConversationMetadataDao,
    private val messageMetadataDao: MessageMetadataDao,
    private val sendTrackingDao: SmsSendTrackingDao,
    private val recycleBin: RecycleBinRepository,
    private val blockedNumberDao: BlockedNumberDao,
    private val otpRecordDao: OtpRecordDao,
) {
    private val resolver = context.contentResolver
    private val messageRevision = MutableStateFlow(0L)
    private val contactsCache = android.util.LruCache<String, ContactDetails>(512)
    private val mmsStore = com.example.messages.data.mms.MmsStore(context)

    suspend fun sendAttachment(address: String, text: String, image: Uri, sim: Int): Long =
        mmsStore.send(address, text, image, sim, com.example.messages.data.mms.ImageResolution.preferred(context))

    fun observeConversations(): Flow<List<SmsConversation>> =
        messageChanges()
            .mapLatest { readAllMessages() }
            .combine(conversationMetadataDao.observeAll()) { records, metadata -> records to metadata }
            .combine(contactChanges()) { (records, metadata), _ -> records to metadata }
            .flatMapLatest { (records, metadata) ->
                flow {
                    val flags = metadata.associate {
                        it.threadId to ConversationFlags(it.pinned, it.archived, it.muted, it.spam, it.textScale)
                    }
                    // Emit inbox rows before doing the per-conversation Contacts lookups.
                    val conversations = withContext(Dispatchers.Default) {
                        ConversationMapper.map(records, flags) { address -> contactsCache.get(address) ?: ContactDetails() }
                    }
                    emit(conversations)
                    val withContacts = withContext(Dispatchers.IO) {
                        conversations.map { conversation ->
                            ensureActive()
                            val contact = cachedContactDetails(conversation.address)
                            conversation.copy(
                                displayName = contact.displayName.ifBlank { conversation.address.ifBlank { "Unknown sender" } },
                                contactPhotoUri = contact.photoUri,
                            )
                        }
                    }
                    emit(withContacts)
                }
            }
            .distinctUntilChanged()

    fun observeSearch(query: MessageSearch): Flow<Set<Long>?> = if (!query.hasMessageFilters) flow { emit(null) } else
        messageChanges().mapLatest {
            withContext(Dispatchers.IO) {
                val names = mutableMapOf<String, String>()
                readAllMessages().asSequence().filter { message ->
                    val name = if (query.field != SearchField.Message && query.text.isNotBlank()) {
                        names.getOrPut(message.address) {
                            cachedContactDetails(message.address).displayName.ifBlank { smsSenderBrand(message.address)?.name.orEmpty() }
                        }
                    } else ""
                    query.matches(message, name)
                }.map(SmsRecord::threadId).toSet()
            }
        }

    fun observeThread(threadId: Long, limit: Int = Int.MAX_VALUE): Flow<List<SmsRecord>> =
        messageChanges()
            .mapLatest { withContext(Dispatchers.IO) { readThread(threadId, limit) } }
            .distinctUntilChanged()

    fun observeMessageMetadata(): Flow<List<MessageMetadataEntity>> = messageMetadataDao.observeAll()
    fun observeConversationMetadata() = conversationMetadataDao.observeAll()

    suspend fun loadThread(threadId: Long, limit: Int = Int.MAX_VALUE): List<SmsRecord> =
        withContext(Dispatchers.IO) { readThread(threadId, limit) }

    suspend fun indexHistoricalOtpMessages() = withContext(Dispatchers.IO) {
        val earliest = (System.currentTimeMillis() - OtpRecordEntity.EXPIRATION_MS).toString()
        val spamIds = conversationMetadataDao.observeAll().first().filter { it.spam }.map { it.threadId }.toSet()
        readRecords(Telephony.Sms.Inbox.CONTENT_URI, "${Telephony.Sms.DATE}>?", arrayOf(earliest), "${Telephony.Sms.DATE} DESC").forEach { message ->
            if (message.threadId in spamIds) return@forEach
            val code = OtpDetector.detect(message.body) ?: return@forEach
            if (otpRecordDao.get(message.id) == null) {
                otpRecordDao.upsert(
                    OtpRecordEntity(
                        message.id,
                        code,
                        message.address,
                        message.date,
                        expiresAt = message.date + OtpRecordEntity.EXPIRATION_MS,
                    ),
                )
            }
        }
    }

    suspend fun findOrCreateThread(address: String): Long = withContext(Dispatchers.IO) {
        Telephony.Threads.getOrCreateThreadId(context, setOf(address))
    }

    suspend fun searchContacts(query: String): List<ContactCandidate> = withContext(Dispatchers.IO) {
        if (query.isBlank() || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return@withContext emptyList()
        }
        val result = mutableListOf<ContactCandidate>()
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
            arrayOf("%$query%", "%$query%"),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
        )?.use { cursor ->
            val nameColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
            val seen = mutableSetOf<String>()
            while (result.size < 30 && cursor.moveToNext()) {
                val number = cursor.getString(numberColumn).orEmpty()
                if (number.isNotBlank() && seen.add(PhoneNumberUtils.normalizeNumber(number))) {
                    result += ContactCandidate(cursor.getString(nameColumn).orEmpty(), number, cursor.getString(photoColumn).orEmpty())
                }
            }
        }
        result.distinctBy(ContactCandidate::number)
    }

    suspend fun contactDetails(address: String): ContactDetails = withContext(Dispatchers.IO) {
        lookupContactDetailsOnIo(address)
    }

    suspend fun activeSubscriptions(): List<SimOption> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return@withContext emptyList()
        }
        try {
            val manager = context.getSystemService(SubscriptionManager::class.java)
            manager?.activeSubscriptionInfoList.orEmpty().sortedBy { it.simSlotIndex }.map {
                val carrier = it.carrierName?.toString().orEmpty().trim()
                val label = carrier.ifBlank { it.displayName?.toString().orEmpty().trim() }
                SimOption(it.subscriptionId, label.ifBlank { "SIM ${it.simSlotIndex + 1}" }, it.simSlotIndex)
            }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    suspend fun setPinned(threadId: Long, pinned: Boolean) {
        val old = conversationMetadataDao.get(threadId) ?: ConversationMetadataEntity(threadId)
        conversationMetadataDao.upsert(old.copy(pinned = pinned))
    }

    suspend fun setArchived(threadId: Long, archived: Boolean) {
        val old = conversationMetadataDao.get(threadId) ?: ConversationMetadataEntity(threadId)
        conversationMetadataDao.upsert(old.copy(archived = archived))
    }

    suspend fun setMuted(threadId: Long, muted: Boolean) {
        val old = conversationMetadataDao.get(threadId) ?: ConversationMetadataEntity(threadId)
        conversationMetadataDao.upsert(old.copy(muted = muted))
    }

    suspend fun setSpam(threadId: Long, spam: Boolean) {
        val old = conversationMetadataDao.get(threadId) ?: ConversationMetadataEntity(threadId)
        conversationMetadataDao.upsert(old.copy(spam = spam, archived = if (spam) false else old.archived))
    }

    suspend fun setTextScale(threadId: Long, scale: Float) {
        val old = conversationMetadataDao.get(threadId) ?: ConversationMetadataEntity(threadId)
        conversationMetadataDao.upsert(old.copy(textScale = scale.coerceIn(0.8f, 1.6f)))
    }

    fun observeBlockedNumbers() = blockedNumberDao.observeAll()
    suspend fun isBlockedNumber(number: String) = blockedNumberDao.isBlocked(PhoneNumberUtils.normalizeNumber(number).ifBlank { number.trim() })
    suspend fun blockNumber(number: String) {
        val key = PhoneNumberUtils.normalizeNumber(number).ifBlank { number.trim() }
        if (key.isNotBlank()) blockedNumberDao.block(BlockedNumberEntity(key, System.currentTimeMillis()))
    }
    suspend fun unblockNumber(number: String) = blockedNumberDao.unblock(number)

    suspend fun isMuted(threadId: Long): Boolean = conversationMetadataDao.get(threadId)?.muted == true
    suspend fun isSpam(threadId: Long): Boolean = conversationMetadataDao.get(threadId)?.spam == true
    fun isRecycleBinEnabled(): Boolean = recycleBin.isEnabled()

    suspend fun setMessagePinned(messageId: Long, pinned: Boolean) {
        messageMetadataDao.upsert(MessageMetadataEntity(messageId, pinned))
    }

    suspend fun markThreadRead(threadId: Long, read: Boolean) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, if (read) 1 else 0)
            put(Telephony.Sms.SEEN, 1)
        }
        resolver.update(Telephony.Sms.CONTENT_URI, values, "${Telephony.Sms.THREAD_ID}=?", arrayOf(threadId.toString()))
    }

    suspend fun markThreadsRead(threadIds: List<Long>, read: Boolean) = withContext(Dispatchers.IO) {
        if (threadIds.isEmpty()) return@withContext
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, if (read) 1 else 0)
            put(Telephony.Sms.SEEN, 1)
        }
        val placeholders = threadIds.joinToString(",") { "?" }
        resolver.update(Telephony.Sms.CONTENT_URI, values, "${Telephony.Sms.THREAD_ID} IN ($placeholders)", threadIds.map(Long::toString).toTypedArray())
    }

    suspend fun deleteThreads(threadIds: List<Long>) = withContext(Dispatchers.IO) {
        if (threadIds.isEmpty()) return@withContext
        recycleBin.deleteThreads(threadIds)
        threadIds.forEach { thread -> mmsStore.read(thread).forEach { mmsStore.delete(it.id) } }
        conversationMetadataDao.delete(threadIds)
    }

    suspend fun deleteMessages(messageIds: List<Long>): Int = withContext(Dispatchers.IO) {
        if (messageIds.isEmpty()) return@withContext 0
        val mmsIds = messageIds.filter { it < 0 }.distinct()
        val deleted = mmsIds.filter(mmsStore::delete) + recycleBin.deleteMessages(messageIds.filter { it >= 0 })
        if (deleted.isNotEmpty()) {
            messageRevision.update { it + 1 }
            messageMetadataDao.delete(deleted)
        }
        deleted.size
    }

    suspend fun deleteOtpMessage(otp: OtpRecordEntity, requireRecovery: Boolean = false): Boolean {
        if (!recycleBin.deleteOtpSms(otp, requireRecovery)) return false
        messageRevision.update { it + 1 }
        messageMetadataDao.delete(listOf(otp.messageId))
        return true
    }

    suspend fun expiredOtpsInThread(threadId: Long): List<OtpRecordEntity> = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - OtpRecordEntity.EXPIRATION_MS
        readRecords(
            Telephony.Sms.Inbox.CONTENT_URI, "${Telephony.Sms.THREAD_ID}=? AND ${Telephony.Sms.DATE}<=?",
            arrayOf(threadId.toString(), cutoff.toString()), null,
        ).mapNotNull { message ->
            if (messageMetadataDao.get(message.id)?.pinned == true) return@mapNotNull null
            val existing = otpRecordDao.get(message.id)
            if (existing?.dismissed == true) return@mapNotNull null
            val code = OtpDetector.detect(message.body) ?: return@mapNotNull null
            OtpRecordEntity(message.id, code, message.address, message.date)
        }
    }

    suspend fun expireOtps(moveMessages: Boolean, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        if (moveMessages) {
            if (!com.example.messages.platform.DefaultSmsRole.isHeld(context) || !recycleBin.isEnabled()) return@withContext
            otpRecordDao.expiredSources(now - OtpRecordEntity.EXPIRATION_MS).forEach { otp ->
                if (messageMetadataDao.get(otp.messageId)?.pinned != true) deleteOtpMessage(otp, requireRecovery = true)
            }
        }
        otpRecordDao.deleteExpired(now)
    }

    suspend fun markIncomingReadForThread(threadId: Long) = markThreadRead(threadId, true)

    suspend fun receiveSms(address: String, body: String, timestamp: Long, subscriptionId: Int): Long? = withContext(Dispatchers.IO) {
        if (address.isBlank() || body.isBlank()) return@withContext null
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, timestamp)
            put(Telephony.Sms.DATE_SENT, timestamp)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            if (subscriptionId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
        }
        resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)?.lastPathSegment?.toLongOrNull()
    }

    suspend fun threadIdForMessage(messageId: Long): Long? = withContext(Dispatchers.IO) {
        readById(messageId)?.threadId
    }

    suspend fun sendSms(address: String, body: String, subscriptionId: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID, scheduleToken: String? = null): Long = withContext(Dispatchers.IO) {
        val destination = address.trim()
        require(destination.isNotEmpty() && PhoneNumberUtils.isWellFormedSmsAddress(destination)) { "Enter a valid phone number." }
        require(body.isNotBlank()) { "Write a message before sending." }
        val packageManager = context.packageManager
        val supportsSms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEPHONY_MESSAGING)
        } else {
            packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEPHONY)
        }
        check(supportsSms) {
            "SMS sending is not available on this device."
        }

        val threadId = Telephony.Threads.getOrCreateThreadId(context, setOf(destination))
        val baseManager = context.getSystemService(SmsManager::class.java)
        if (subscriptionId < 0 && activeSubscriptions().size > 1 && SubscriptionManager.getDefaultSmsSubscriptionId() < 0) {
            error("Choose the SIM to use for this message.")
        }
        val manager = if (subscriptionId >= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            baseManager.createForSubscriptionId(subscriptionId)
        } else if (subscriptionId >= 0) {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        } else baseManager
        val parts = manager.divideMessage(body)
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, destination)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, 0L)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
            put(Telephony.Sms.THREAD_ID, threadId)
            put(Telephony.Sms.SUBSCRIPTION_ID, if (subscriptionId >= 0) subscriptionId else manager.subscriptionId)
        }
        val messageUri = resolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values)
            ?: error("Android could not save this message to the SMS Provider.")
        val messageId = ContentUris.parseId(messageUri)
        sendTrackingDao.upsert(SmsSendTrackingEntity(messageId, parts.size))
        val sent = ArrayList<android.app.PendingIntent>(parts.size)
        val delivered = ArrayList<android.app.PendingIntent>(parts.size)
        parts.indices.forEach { index ->
            val sentIntent = android.content.Intent(context, SmsSentReceiver::class.java)
                .setAction("com.example.messages.SMS_SENT")
                .setData(Uri.parse("messages://sms/sent/$messageId/$index"))
                .putExtra("schedule_token", scheduleToken)
            val deliveryIntent = android.content.Intent(context, SmsDeliveryStatusReceiver::class.java)
                .setAction("com.example.messages.SMS_DELIVERED")
                .setData(Uri.parse("messages://sms/delivered/$messageId/$index"))
            val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_IMMUTABLE else 0
            sent += android.app.PendingIntent.getBroadcast(context, messageId.toInt() * 100 + index, sentIntent, flags)
            delivered += android.app.PendingIntent.getBroadcast(context, messageId.toInt() * 100 + index + 50_000, deliveryIntent, flags)
        }
        try {
            if (parts.size == 1) manager.sendTextMessage(destination, null, body, sent.first(), delivered.first())
            else manager.sendMultipartTextMessage(destination, null, parts, sent, delivered)
        } catch (error: Exception) {
            resolver.update(messageUri, ContentValues().apply {
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED)
                put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_FAILED)
                put(Telephony.Sms.ERROR_CODE, 1)
            }, null, null)
            sendTrackingDao.delete(messageId)
            throw error
        }
        messageId
    }

    suspend fun recordSentPart(messageId: Long, success: Boolean, resultCode: Int, scheduleToken: String?) = withContext(Dispatchers.IO) {
        val progress = sendTrackingDao.recordPart(messageId, success) ?: return@withContext
        val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId)
        if (!success) {
            resolver.update(uri, ContentValues().apply {
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED)
                put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_FAILED)
                put(Telephony.Sms.ERROR_CODE, resultCode)
            }, null, null)
        } else if (progress.completedParts >= progress.totalParts && !progress.hasFailure) {
            resolver.update(uri, ContentValues().apply {
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_NONE)
                put(Telephony.Sms.DATE_SENT, System.currentTimeMillis())
            }, null, null)
        }
        if (progress.completedParts >= progress.totalParts) {
            scheduleToken?.let { com.example.messages.data.schedule.ScheduledSmsStore(context).finish(it, !progress.hasFailure) }
            sendTrackingDao.delete(messageId)
        }
    }

    suspend fun updateDeliveryStatus(messageId: Long, delivered: Boolean) = withContext(Dispatchers.IO) {
        if (!delivered) return@withContext
        resolver.update(
            ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId),
            ContentValues().apply { put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_COMPLETE) },
            null,
            null,
        )
    }

    suspend fun retrySms(messageId: Long) = withContext(Dispatchers.IO) {
        val record = readById(messageId) ?: error("This failed message is no longer available.")
        // Retry uses the original subscription if Android recorded one; a missing SIM fails normally.
        sendSms(record.address, record.body, record.subscriptionId)
    }

    private fun messageChanges() = providerChanges(Telephony.Sms.CONTENT_URI, Telephony.Mms.CONTENT_URI)
        .combine(messageRevision) { _, _ -> Unit }
        .conflate()

    private fun contactChanges(): Flow<Unit> = if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED) {
        providerChanges(ContactsContract.Contacts.CONTENT_URI).mapLatest { contactsCache.evictAll() }
    } else flow { emit(Unit) }

    private fun providerChanges(vararg uris: Uri) = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
            override fun onChange(selfChange: Boolean, changedUri: Uri?) { trySend(Unit) }
        }
        try {
            uris.forEach { resolver.registerContentObserver(it, true, observer) }
            trySend(Unit)
            awaitClose()
        } finally {
            resolver.unregisterContentObserver(observer)
        }
    }

    private suspend fun readAllMessages(): List<SmsRecord> = withContext(Dispatchers.IO) {
        readRecords(Telephony.Sms.CONTENT_URI, null, null, "${Telephony.Sms.DATE} DESC") + mmsStore.read()
    }

    private suspend fun readThread(threadId: Long, limit: Int = Int.MAX_VALUE): List<SmsRecord> = withContext(Dispatchers.IO) {
        val sms = readRecords(Telephony.Sms.CONTENT_URI, "${Telephony.Sms.THREAD_ID}=?", arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC, ${BaseColumns._ID} DESC", limit)
        (sms + mmsStore.read(threadId, limit)).sortedWith(compareByDescending<SmsRecord> { it.date }
            .thenByDescending { it.id }).take(limit).asReversed()
    }

    private fun readById(messageId: Long): SmsRecord? = readRecords(
        Telephony.Sms.CONTENT_URI,
        "${BaseColumns._ID}=?",
        arrayOf(messageId.toString()),
        null,
    ).firstOrNull()

    private fun readRecords(uri: Uri, selection: String?, args: Array<String>?, order: String?, limit: Int = Int.MAX_VALUE): List<SmsRecord> {
        val result = mutableListOf<SmsRecord>()
        resolver.query(uri, SMS_PROJECTION, selection, args, order)?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(BaseColumns._ID)
            val thread = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val address = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val body = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val read = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
            val type = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val status = cursor.getColumnIndexOrThrow(Telephony.Sms.STATUS)
            val subId = cursor.getColumnIndexOrThrow(Telephony.Sms.SUBSCRIPTION_ID)
            while (result.size < limit && cursor.moveToNext()) {
                result += SmsRecord(
                    cursor.getLong(id), cursor.getLong(thread), cursor.getString(address).orEmpty(),
                    cursor.getString(body).orEmpty(), cursor.getLong(date), cursor.getInt(read) != 0,
                    cursor.getInt(type), cursor.getInt(status), cursor.getInt(subId),
                )
            }
        }
        return result
    }

    private fun cachedContactDetails(address: String): ContactDetails = contactsCache.get(address)
        ?: lookupContactDetailsOnIo(address).also { contactsCache.put(address, it) }

    private fun lookupContactDetailsOnIo(address: String): ContactDetails {
        if (address.isBlank() || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return ContactDetails()
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
            resolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) ContactDetails(cursor.getString(0).orEmpty(), cursor.getString(1).orEmpty())
                else ContactDetails()
            } ?: ContactDetails()
        } catch (_: SecurityException) {
            ContactDetails()
        }
    }

    companion object {
        val SMS_PROJECTION = arrayOf(
            BaseColumns._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE,
            Telephony.Sms.STATUS,
            Telephony.Sms.SUBSCRIPTION_ID,
        )
    }
}
