package com.example.messages.feature.otp

import com.example.messages.core.database.OtpRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpExpirationTest {
    @Test
    fun expiresAt24HoursAndKeepsEarlierRecordsVisible() {
        val expiresAt = 1_800_000_000_000L
        val active = otp(expiresAt = expiresAt)
        val expired = otp(expiresAt = expiresAt - 1)

        assertFalse(active.isExpiredAt(expiresAt - 1))
        assertTrue(active.isExpiredAt(expiresAt))
        assertEquals(listOf(active), listOf(active, expired).visibleAt(expiresAt - 1))
    }

    @Test
    fun removesOtpCardOnFourthCopy() {
        assertFalse(shouldRemoveOtpAfterCopy(3))
        assertTrue(shouldRemoveOtpAfterCopy(4))
    }

    private fun otp(expiresAt: Long) = OtpRecordEntity(
        messageId = 1,
        code = "AB12",
        sender = "Example",
        detectedAt = expiresAt - OtpRecordEntity.EXPIRATION_MS,
        expiresAt = expiresAt,
    )
}
