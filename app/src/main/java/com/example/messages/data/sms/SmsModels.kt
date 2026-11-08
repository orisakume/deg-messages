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
    val searchContent: String,
    val contactPhotoUri: String = "",
)

data class ContactDetails(val displayName: String = "", val photoUri: String = "")

/** True only for address strings Android considers valid SMS destinations. */
fun canReplyToSmsAddress(address: String): Boolean =
    address.isNotBlank() && PhoneNumberUtils.isWellFormedSmsAddress(address.trim())

data class ContactCandidate(val name: String, val number: String)
data class SimOption(val subscriptionId: Int, val displayName: String, val slotIndex: Int)
data class ConversationFlags(val pinned: Boolean, val archived: Boolean, val muted: Boolean, val spam: Boolean, val textScale: Float)

object ConversationMapper {
    fun map(
        messages: List<SmsRecord>,
        metadata: Map<Long, ConversationFlags>,
        contactDetails: (String) -> ContactDetails = { ContactDetails() },
    ): List<SmsConversation> = messages
        .groupBy(SmsRecord::threadId)
        .mapNotNull { (threadId, records) ->
            if (threadId < 0 || records.isEmpty()) return@mapNotNull null
            val latest = records.maxByOrNull(SmsRecord::date) ?: return@mapNotNull null
            val address = latest.address.ifBlank { records.firstOrNull { it.address.isNotBlank() }?.address.orEmpty() }
            val flags = metadata[threadId] ?: ConversationFlags(false, false, false, false, 1f)
            val contact = contactDetails(address)
            SmsConversation(
                threadId = threadId,
                address = address,
                displayName = contact.displayName.ifBlank { address.ifBlank { "Unknown sender" } },
                preview = latest.body,
                date = latest.date,
                unreadCount = records.count { it.type == INBOX_TYPE && !it.read },
                messageCount = records.size,
                pinned = flags.pinned,
                archived = flags.archived,
                muted = flags.muted,
                spam = flags.spam,
                textScale = flags.textScale,
                searchContent = records.joinToString(" ") { "${it.address} ${it.body}" },
                contactPhotoUri = contact.photoUri,
            )
        }
        .sortedWith(compareByDescending<SmsConversation> { it.pinned }.thenByDescending { it.date })

    const val INBOX_TYPE = 1
}
