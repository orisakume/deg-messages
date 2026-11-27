package com.example.messages.data.sms

import android.telephony.PhoneNumberUtils

data class SmsRecord(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val read: Boolean,
    val type: Int,
    val status: Int,
    val subscriptionId: Int,
    val attachmentUri: String? = null,
    val attachmentMime: String? = null,
    val sendError: String? = null,
)

data class SmsConversation(
    val threadId: Long,
    val address: String,
    val displayName: String,
    val preview: String,
    val date: Long,
    val unreadCount: Int,
    val messageCount: Int,
    val pinned: Boolean,
    val archived: Boolean,
    val muted: Boolean,
    val spam: Boolean,
    val textScale: Float,
    val contactPhotoUri: String = "",
    val category: MessageCategory = MessageCategory.Personal,
    val receivedBySubscription: Map<Int, SimInboxPreview> = emptyMap(),
) {
    internal fun receivedOn(subscriptionId: Int): SmsConversation? = receivedBySubscription[subscriptionId]?.let {
        copy(preview = it.text, date = it.date, unreadCount = it.unreadCount, messageCount = it.messageCount)
    }
}

data class SimInboxPreview(val text: String, val date: Long, val unreadCount: Int, val messageCount: Int)

data class ContactDetails(val displayName: String = "", val photoUri: String = "")

/** True only for address strings Android considers valid SMS destinations. */
fun canReplyToSmsAddress(address: String): Boolean =
    address.isNotBlank() && PhoneNumberUtils.isWellFormedSmsAddress(address.trim())

data class ContactCandidate(val name: String, val number: String, val photoUri: String = "")
data class SimOption(val subscriptionId: Int, val displayName: String, val slotIndex: Int)
data class ConversationFlags(val pinned: Boolean, val archived: Boolean, val muted: Boolean, val spam: Boolean, val textScale: Float)

object ConversationMapper {
    internal val inboxOrder = compareByDescending<SmsConversation> { it.pinned }.thenByDescending { it.date }

    fun map(
        messages: List<SmsRecord>,
        metadata: Map<Long, ConversationFlags>,
        contactDetails: (String) -> ContactDetails = { ContactDetails() },
    ): List<SmsConversation> {
        val threads = mutableMapOf<Long, Summary>()
        messages.forEach { message ->
            if (message.threadId >= 0) threads.getOrPut(message.threadId) { Summary(message) }.add(message)
        }
        return threads.map { (threadId, summary) ->
            val latest = summary.latest
            val address = latest.address.ifBlank { summary.firstAddress }
            val flags = metadata[threadId] ?: ConversationFlags(false, false, false, false, 1f)
            val contact = contactDetails(address)
            SmsConversation(
                threadId = threadId,
                address = address,
                displayName = contact.displayName.ifBlank { address.ifBlank { "Unknown sender" } },
                preview = latest.preview(),
                date = latest.date,
                unreadCount = summary.unread,
                messageCount = summary.count,
                pinned = flags.pinned,
                archived = flags.archived,
                muted = flags.muted,
                spam = flags.spam,
                textScale = flags.textScale,
                contactPhotoUri = contact.photoUri,
                category = classifyMessage(summary.latestIncoming?.body.orEmpty()),
                receivedBySubscription = summary.receivedBySubscription.mapValues { (_, sim) ->
                    SimInboxPreview(sim.latest.preview(), sim.latest.date, sim.unread, sim.count)
                },
            )
        }.sortedWith(inboxOrder)
    }

    private class Summary(message: SmsRecord) {
        var latest = message
        var firstAddress = message.address
        var latestIncoming: SmsRecord? = null
        var count = 0
        var unread = 0
        val receivedBySubscription = mutableMapOf<Int, Received>()

        fun add(message: SmsRecord) {
            count++
            if (message.date > latest.date) latest = message
            if (firstAddress.isBlank()) firstAddress = message.address
            if (message.type != INBOX_TYPE) return
            if (!message.read) unread++
            if (latestIncoming?.let { message.date > it.date } != false) latestIncoming = message
            if (message.subscriptionId >= 0) {
                val received = receivedBySubscription.getOrPut(message.subscriptionId) { Received(message) }
                received.count++
                if (!message.read) received.unread++
                if (message.date > received.latest.date) received.latest = message
            }
        }
    }

    private class Received(var latest: SmsRecord, var count: Int = 0, var unread: Int = 0)

    private fun SmsRecord.preview() = body.ifBlank {
        when {
            attachmentMime?.startsWith("video/") == true -> "Video"
            attachmentUri != null -> "Photo"
            else -> ""
        }
    }

    const val INBOX_TYPE = 1
}
