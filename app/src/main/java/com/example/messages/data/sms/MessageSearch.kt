package com.example.messages.data.sms

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class SearchField { Any, Message, Contact }

data class MessageSearch(
    val text: String = "",
    val field: SearchField = SearchField.Any,
    val from: LocalDate? = null,
    val through: LocalDate? = null,
    val category: MessageCategory? = null,
    val spamOnly: Boolean = false,
) {
    val active get() = spamOnly || hasMessageFilters
    internal val hasMessageFilters get() = text.isNotBlank() || from != null || through != null || category != null

    fun matches(message: SmsRecord, contactName: String): Boolean {
        val normalized = text.filter(Char::isDigit)
        val contactMatches = contactName.contains(text, ignoreCase = true) || message.address.contains(text, ignoreCase = true) ||
            (normalized.length >= 3 && text.all { it.isDigit() || it in "+-. ()" } && message.address.filter(Char::isDigit).contains(normalized))
        val bodyMatches = message.body.contains(text, ignoreCase = true)
        val textMatches = text.isBlank() || when (field) {
            SearchField.Any -> bodyMatches || contactMatches
            SearchField.Message -> bodyMatches
            SearchField.Contact -> contactMatches
        }
        if (!textMatches || (category != null && classifyMessage(message.body) != category)) return false
        if (from == null && through == null) return true
        val date = Instant.ofEpochMilli(message.date).atZone(ZoneId.systemDefault()).toLocalDate()
        return (from == null || date >= from) && (through == null || date <= through)
    }
}

private val url = Regex("""\b(?:https?://|www\.)\S+""", RegexOption.IGNORE_CASE)
private val phishingRequest = Regex("""\b(?:enter|share|submit|confirm|verify|update|provide)\s+(?:your\s+)?(?:password|otp|one.time password|cvv|card (?:number|details)|bank details|upi pin)\b""", RegexOption.IGNORE_CASE)

private val denial = Regex("""\b(?:never|not|don['’]t)\s+$""", RegexOption.IGNORE_CASE)

internal fun isLikelyPhishing(body: String): Boolean = url.containsMatchIn(body) && phishingRequest.findAll(body).any { match ->
    !denial.containsMatchIn(body.substring(maxOf(0, match.range.first - 24), match.range.first))
}
