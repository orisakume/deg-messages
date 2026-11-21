package com.example.messages.data.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageHighlightsTest {
    @Test
    fun extractsOnlyTheOriginalLinkPhoneAndOtp() {
        val body = "Order status (https://example.com/order?id=2025550118). Call +1 202-555-0118. Your OTP is 482913."
        val highlights = messageHighlights(body).actions

        assertEquals(listOf(HighlightKind.Link, HighlightKind.Phone, HighlightKind.Otp), highlights.map { it.kind })
        assertEquals(listOf("https://example.com/order?id=2025550118", "+1 202-555-0118", "482913"), highlights.map { it.value })
        assertTrue(highlights.all { body.substring(it.range) == it.value })
    }

    @Test
    fun retainsBalancedUrlPunctuationAndKeepsMalformedLinksCopyable() {
        val body = "See https://example.com/a(b); or http.///example.test/check."
        assertEquals(
            listOf("https://example.com/a(b)", "http.///example.test/check"),
            messageHighlights(body).actions.map { it.value },
        )
    }

    @Test
    fun doesNotTreatDatesOrUnlabelledVerificationNumbersAsHighlights() {
        assertTrue(messageHighlights("Order 482913 ships on 2026-11-20.").actions.isEmpty())
    }

    @Test
    fun extractsEmailAddressesWithoutTreatingTheirDigitsAsPhoneNumbers() {
        val body = "Email (team+orders@gmail.com), 2025550118@support.example.co.in, or jo@büro.example."
        val highlights = messageHighlights(body).actions
        assertEquals(listOf("team+orders@gmail.com", "2025550118@support.example.co.in", "jo@büro.example"),
            highlights.map { it.value })
        assertTrue(highlights.all { it.kind == HighlightKind.Email && body.substring(it.range) == it.value })
    }

    @Test
    fun keepsEmailQueryParametersInsideTheLink() {
        val link = "https://example.test/?email=team@company.test"
        assertEquals(listOf(MessageHighlight(HighlightKind.Link, link, 0 until link.length)), messageHighlights(link).actions)
    }

    @Test
    fun highlightsOnlyTheRupeeMarkersBesideAmounts() {
        val body = "Debit Rs. 1,25,000.50. Credits: Rs500 and ₹\u00a0250. Balance ₹५००. 4 hrs remain."
        val formatting = messageHighlights(body)

        assertEquals(listOf("Rs.", "Rs", "₹", "₹"), formatting.rupeeMarkers.map { body.substring(it) })
        assertTrue(formatting.actions.isEmpty())
    }

    @Test
    fun keepsRupeeAmountsSeparateFromPhonesLinksEmailsAndOtps() {
        val body = "Paid ₹2025550118 9876543210. OTP: 123456. See https://example.test/?price=₹500 or Rs500@example.test."
        val formatting = messageHighlights(body)

        assertEquals(listOf("₹"), formatting.rupeeMarkers.map { body.substring(it) })
        assertEquals(listOf(HighlightKind.Phone, HighlightKind.Otp, HighlightKind.Link, HighlightKind.Email), formatting.actions.map { it.kind })
        assertEquals(listOf("9876543210", "123456", "https://example.test/?price=₹500", "Rs500@example.test"), formatting.actions.map { it.value })
    }
}
