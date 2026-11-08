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
}
