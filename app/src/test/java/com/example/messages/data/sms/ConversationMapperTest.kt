package com.example.messages.data.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMapperTest {
    @Test
    fun groupsSmsByThreadAndCountsOnlyUnreadIncomingMessages() {
        val conversations = ConversationMapper.map(
            messages = listOf(
                sms(id = 1, threadId = 10, address = "sender-a", body = "older", date = 1, read = false, type = 1),
                sms(id = 2, threadId = 10, address = "sender-a", body = "latest", date = 2, read = true, type = 1),
                sms(id = 3, threadId = 10, address = "sender-a", body = "reply", date = 3, read = true, type = 2),
            ),
            metadata = emptyMap(),
            contactDetails = { ContactDetails(photoUri = "content://contacts/photo") },
        )

        assertEquals(1, conversations.size)
        assertEquals("reply", conversations.single().preview)
        assertEquals(1, conversations.single().unreadCount)
        assertEquals(3, conversations.single().messageCount)
        assertFalse(conversations.single().pinned)
        assertEquals("content://contacts/photo", conversations.single().contactPhotoUri)
    }

    @Test
    fun simPreviewsUseOnlyReceivedMessagesForThatSubscription() {
        val conversation = ConversationMapper.map(
            messages = listOf(
                sms(1, 10, "sender", "SIM 1 unread", 1, read = false, sim = 31),
                sms(2, 10, "sender", "SIM 2 received", 2, sim = 78),
                sms(3, 10, "sender", "SIM 1 latest", 3, sim = 31),
                sms(4, 10, "sender", "Unknown SIM", 4),
                sms(5, 10, "sender", "SIM 2 outgoing", 5, type = 2, sim = 78),
            ),
            metadata = emptyMap(),
        ).single()

        assertEquals("SIM 2 outgoing", conversation.preview)
        assertEquals(setOf(31, 78), conversation.receivedBySubscription.keys)
        assertEquals(SimInboxPreview("SIM 1 latest", 3, 1, 2), conversation.receivedBySubscription[31])
        assertEquals(SimInboxPreview("SIM 2 received", 2, 0, 1), conversation.receivedBySubscription[78])
        assertEquals("SIM 2 received", conversation.receivedOn(78)?.preview)
        assertEquals(1, conversation.receivedOn(78)?.messageCount)
        assertEquals(null, conversation.receivedOn(-1))
    }

    @Test
    fun pinnedConversationsSortBeforeNewerUnpinnedThreads() {
        val conversations = ConversationMapper.map(
            messages = listOf(
                sms(id = 1, threadId = 10, address = "sender-a", body = "pinned", date = 1),
                sms(id = 2, threadId = 20, address = "sender-b", body = "newer", date = 2),
            ),
            metadata = mapOf(10L to ConversationFlags(true, false, false, true, 1.25f)),
            contactDetails = { ContactDetails() },
        )

        assertEquals(listOf(10L, 20L), conversations.map(SmsConversation::threadId))
        assertTrue(conversations.first().pinned)
        assertTrue(conversations.first().spam)
        assertEquals(1.25f, conversations.first().textScale)
        assertFalse(conversations.last().pinned)
    }

    private fun sms(
        id: Long,
        threadId: Long,
        address: String,
        body: String,
        date: Long,
        read: Boolean = true,
        type: Int = 1,
        sim: Int = -1,
    ) = SmsRecord(id, threadId, address, body, date, read, type, -1, sim)
}
