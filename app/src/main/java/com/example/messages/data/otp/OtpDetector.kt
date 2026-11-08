package com.example.messages.data.otp

/** Local, conservative OTP candidate extraction. Never logs or uploads message text. */
object OtpDetector {
    private val context = Regex("(?i)\\b(otp|one[- ]time (?:password|code)|verification code|verify code|authentication code|auth code|passcode|login code|security code)\\b")
    private val candidates = listOf(
        Regex("(?i)\\b(?:code|otp|password|passcode|is|:|-)\\s*[:#-]?\\s*([A-Z0-9]{4,10})\\b"),
        Regex("(?i)\\b([0-9]{4,8})\\s+(?:is your|is the|as your)\\s+(?:verification|login|security|one[- ]time)\\b"),
    )

    fun detect(body: String): String? {
        if (!context.containsMatchIn(body)) return null
        return candidates.firstNotNullOfOrNull { pattern ->
            pattern.findAll(body).mapNotNull { it.groupValues.getOrNull(1) }
                .firstOrNull { code -> code.any(Char::isDigit) && code.any(Char::isLetterOrDigit) }
        }
    }
}
