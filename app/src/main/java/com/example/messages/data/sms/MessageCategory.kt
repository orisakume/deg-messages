package com.example.messages.data.sms

import com.example.messages.data.otp.OtpDetector

enum class MessageCategory { Personal, Transactions, Promotions, OTP }

private val transactionTerms = Regex("""\b(debited|credited|transaction|payment (?:received|successful|failed)|available balance|amount paid|order (?:confirmed|shipped|delivered)|refund)\b""", RegexOption.IGNORE_CASE)
private val promotionTerms = Regex("""\b(unsubscribe|discount|limited.time offer|sale ends|promo code|cashback offer|special offer|shop now)\b""", RegexOption.IGNORE_CASE)

internal fun classifyMessage(body: String): MessageCategory = when {
    OtpDetector.detect(body) != null -> MessageCategory.OTP
    transactionTerms.containsMatchIn(body) -> MessageCategory.Transactions
    promotionTerms.containsMatchIn(body) -> MessageCategory.Promotions
    else -> MessageCategory.Personal
}
