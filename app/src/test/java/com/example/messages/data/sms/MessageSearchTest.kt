package com.example.messages.data.sms

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSearchTest {
    private val day = LocalDate.of(2026, 11, 19)
    private val message = SmsRecord(1, 2, "+12025550118", "Payment received.",
        day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), false, 1, 0, 1)

    @Test
    fun searchCombinesContentDateAndCategoryOnTheSameMessage() {
        val search = MessageSearch("payment", SearchField.Message, day, day, MessageCategory.Transactions)
        assertTrue(search.matches(message, "Alex"))
        assertFalse(search.copy(from = day.plusDays(1)).matches(message, "Alex"))
        assertFalse(search.copy(category = MessageCategory.OTP).matches(message, "Alex"))
    }

    @Test
    fun contactSearchUsesNamesAndFormattedNumbers() {
        assertTrue(MessageSearch("alex", SearchField.Contact).matches(message, "Alex"))
        assertTrue(MessageSearch("(202) 555-0118", SearchField.Contact).matches(message, "Alex"))
        assertFalse(MessageSearch("payment", SearchField.Contact).matches(message, "Alex"))
    }

    @Test
    fun phishingRequiresBothALinkAndARequestForSensitiveDetails() {
        assertTrue(isLikelyPhishing("Enter your UPI PIN at https://example.com"))
        assertFalse(isLikelyPhishing("Never share your OTP. Help: https://example.com"))
        assertFalse(isLikelyPhishing("View your receipt at https://example.com"))
    }
}
