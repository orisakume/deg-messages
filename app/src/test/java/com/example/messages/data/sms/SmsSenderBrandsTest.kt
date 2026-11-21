package com.example.messages.data.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsSenderBrandsTest {
    @Test
    fun recognizesRegisteredHeadersAcrossCarrierPrefixesAndCategories() {
        assertEquals("Zepto", smsSenderBrand("VA-ZEPTOO-T")?.name)
        assertEquals("Zepto", smsSenderBrand("vm-zepton-s")?.name)
        assertEquals("India Post", smsSenderBrand("JD-INPOST-S")?.name)
        assertEquals("India Post", smsSenderBrand("VM-DOPBNK-T")?.name)
        assertEquals("HDFC Bank", smsSenderBrand("AD-HDFCBK-T")?.name)
        assertEquals("Amazon", smsSenderBrand("AMZNIN")?.name)
        assertEquals("Flipkart", smsSenderBrand("VM-FLPKRT-S")?.name)
    }

    @Test
    fun rejectsLookAlikeUnknownAndPhoneNumberSenders() {
        listOf("VA-ZEPT00-T", "VA-XZEPTOO-T", "VA-ZEPTOO-X", "VA-ZEРTOO-T",
            "VA-ZOMATO-S", "+919876543210", "Bank-ZEPTOO", "Example").forEach { address ->
            assertNull(address, smsSenderBrand(address))
        }
    }
}
