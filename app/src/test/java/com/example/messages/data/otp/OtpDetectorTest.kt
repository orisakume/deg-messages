package com.example.messages.data.otp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpDetectorTest {
    @Test fun detectsContextualNumericOtp() {
        assertEquals("482913", OtpDetector.detect("Your verification code is 482913. Do not share it."))
        assertEquals("504910", OtpDetector.detect("Delivery Authentication Code (DAC) for booking is 504910. Please share the code."))
    }
    @Test fun detectsAlphanumericCodeOnlyWithContext() {
        assertEquals("AB12CD", OtpDetector.detect("Your login code: AB12CD"))
        assertNull(OtpDetector.detect("Your transaction number is 482913"))
    }

    @Test fun detectsFourDigitCodesInCommonVerificationTemplates() {
        assertEquals("0234", OtpDetector.detect("Your sign-in code is 0234."))
        assertEquals("8491", OtpDetector.detect("8491 is your Example Bank OTP."))
        assertEquals("4291", OtpDetector.detect("OTP for account verification: 4291"))
        assertEquals("3816", OtpDetector.detect("Use 3816 to sign in to Example."))
    }

    @Test fun requiresVerificationContextForFourDigitNumbers() {
        assertNull(OtpDetector.detect("Your transaction number is 8491."))
        assertNull(OtpDetector.detect("Use 8491 to redeem your offer."))
        assertNull(OtpDetector.detect("8491"))
    }

    @Test fun detectsSixAndEightDigitCodesInVerificationTemplates() {
        assertEquals("523513", OtpDetector.detect("523513 is your Example Bank OTP."))
        assertEquals("02740156", OtpDetector.detect("Use 02740156 to verify your account."))
        assertNull(OtpDetector.detect("Use 02740156 to redeem your offer."))
    }
}
