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
        assertTrue(conversations.single().searchContent.contains("older"))
        assertFalse(conversations.single().pinned)
        assertEquals("content://contacts/photo", conversations.single().contactPhotoUri)
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
    ) = SmsRecord(id, threadId, address, body, date, read, type, -1, -1)
}
