package com.example.messages.data.sms

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageCategoryTest {
    @Test
    fun codeTakesPrecedenceOverTransactionTerms() {
        assertEquals(MessageCategory.OTP, classifyMessage("Your OTP is 123456 for this transaction."))
    }

    @Test
    fun distinguishesTransactionPromotionAndPersonalText() {
        assertEquals(MessageCategory.Transactions, classifyMessage("Payment received."))
        assertEquals(MessageCategory.Promotions, classifyMessage("Special offer with a discount."))
        assertEquals(MessageCategory.Personal, classifyMessage("See you this evening."))
    }
}
