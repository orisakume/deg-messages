package com.example.messages.data.otp

/** Local, conservative OTP candidate extraction. Never logs or uploads message text. */
object OtpDetector {
    private const val LABEL = """\b(?:otp|one[- ]time (?:password|code)|verification code|verify code|authentication code|auth code|passcode|login code|security code)\b"""
    private const val VALUE = """\b([A-Z0-9]{4,10})\b"""
    private val candidates = listOf(
        Regex("""$LABEL\s*(?:is\b\s*)?[:#=–-]?\s*$VALUE""", RegexOption.IGNORE_CASE),
        Regex("""$VALUE\s+(?:is your|is the|as your)\s+$LABEL""", RegexOption.IGNORE_CASE),
        Regex("""$LABEL[^.!?\n]{0,48}?\bis\s+$VALUE""", RegexOption.IGNORE_CASE),
    )

    fun detect(body: String): String? = detectMatch(body)?.value

    internal fun detectMatch(body: String): MatchGroup? = candidates.firstNotNullOfOrNull { pattern ->
        pattern.findAll(body).mapNotNull { it.groups[1] }
            .firstOrNull { group -> group.value.any(Char::isDigit) }
    }
}
