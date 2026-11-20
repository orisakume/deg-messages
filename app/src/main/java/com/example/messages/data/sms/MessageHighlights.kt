package com.example.messages.data.sms

import com.example.messages.data.otp.OtpDetector

internal enum class HighlightKind(val label: String) { Link("Link"), Email("Email address"), Phone("Phone number"), Otp("OTP") }

internal data class MessageHighlight(val kind: HighlightKind, val value: String, val range: IntRange)

private val links = Regex("""(?i)\b(?:https?://|https?\./{2,}|www\.)[^\s<>"“”]+""")
private const val EMAIL_ATOM = """[\p{L}\p{M}\p{N}!#$%&'*+/=?^_\x60{|}~-]+"""
private const val EMAIL_DOMAIN_LABEL = """[\p{L}\p{M}\p{N}](?:[\p{L}\p{M}\p{N}-]*[\p{L}\p{M}\p{N}])?"""
private val emails = Regex(
    """(?<![\p{L}\p{M}\p{N}._@+-])(?:$EMAIL_ATOM(?:\.$EMAIL_ATOM)*|"(?:[^"\\\r\n]|\\.)+")@(?:$EMAIL_DOMAIN_LABEL\.)+(?:[\p{L}][\p{L}\p{M}]+|xn--[a-z0-9-]+)(?![\p{L}\p{M}\p{N}_-])""",
    RegexOption.IGNORE_CASE,
)
private val phones = Regex("""(?<![\p{L}\p{N}])(?:\+?\d|\(\d{2,5}\))(?:[ \t().-]*\d){6,14}(?![\p{L}\p{N}])""")
private val dates = Regex("""(?:\d{4}[.-]\d{1,2}[.-]\d{1,2}|\d{1,2}[.-]\d{1,2}[.-]\d{4})(?:[ \t]\d{1,2})?""")
private val ipv4 = Regex("""(?:\d{1,3}\.){3}\d{1,3}""")

internal fun messageHighlights(body: String): List<MessageHighlight> {
    val found = mutableListOf<MessageHighlight>()
    fun add(kind: HighlightKind, value: String, start: Int) {
        val range = start until start + value.length
        if (value.isNotEmpty() && found.none { range.first <= it.range.last && it.range.first <= range.last }) {
            found += MessageHighlight(kind, value, range)
        }
    }
    links.findAll(body).forEach { match ->
        val link = match.value.trimEnd('.', ',', '!', '?', ';', ':', '\'', '’').trimClosingDelimiters()
        add(HighlightKind.Link, link, match.range.first)
    }
    emails.findAll(body).forEach { match -> add(HighlightKind.Email, match.value, match.range.first) }
    OtpDetector.detectMatch(body)?.let { code -> add(HighlightKind.Otp, code.value, code.range.first) }
    phones.findAll(body).forEach { match ->
        val number = match.value
        if (number.count(Char::isDigit) in 7..15 && !dates.matches(number) && !ipv4.matches(number)) {
            add(HighlightKind.Phone, number, match.range.first)
        }
    }
    return found.sortedBy { it.range.first }
}

private fun String.trimClosingDelimiters(): String {
    var end = length
    while (end > 0) {
        val close = this[end - 1]
        val open = when (close) {
            ')' -> '('
            ']' -> '['
            '}' -> '{'
            else -> break
        }
        if ((0 until end).count { this[it] == close } <= (0 until end).count { this[it] == open }) break
        end--
    }
    return substring(0, end)
}
