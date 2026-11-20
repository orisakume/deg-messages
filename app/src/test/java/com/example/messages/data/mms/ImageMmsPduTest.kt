package com.example.messages.data.mms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMmsPduTest {
    @Test
    fun sendRequestIncludesRecipientPresentationImageAndCaption() {
        val caption = "Hello from DEG"
        val bytes = composeMmsPdu("+12025550118", caption.toByteArray(), MmsAttachment("image/jpeg", ByteArray(300) { 0x42 }))
        val raw = bytes.toString(Charsets.ISO_8859_1)

        assertEquals(0x8c.toByte(), bytes[0])
        assertEquals(0x80.toByte(), bytes[1])
        assertTrue(raw.contains("+12025550118/TYPE=PLMN"))
        assertTrue(raw.contains("<presentation>"))
        assertTrue(raw.contains("photo.jpg"))
        assertTrue(raw.contains("B".repeat(300)))
        assertTrue(raw.endsWith(caption))
    }

    @Test
    fun videoUsesTimedSmilVideoPartAndPreservesPayload() {
        val raw = composeMmsPdu("+12025550118", byteArrayOf(),
            MmsAttachment("video/mp4", "fixture-video".toByteArray(), durationMillis = 1500))
            .toString(Charsets.ISO_8859_1)
        assertTrue(raw.contains("<video src=\"video.mp4\""))
        assertTrue(raw.contains("dur=\"1500ms\""))
        assertTrue(raw.contains("video/mp4"))
        assertTrue(raw.endsWith("fixture-video"))
    }

    @Test
    fun confirmationRequiresSendConfAndAcceptedStatus() {
        fun response(type: Int, status: Int) =
            byteArrayOf(0x8c.toByte(), type.toByte(), 0x98.toByte(), 'x'.code.toByte(), 0,
                0x8d.toByte(), 0x92.toByte(), 0x92.toByte(), status.toByte())

        assertTrue(acceptedMmsResponse(response(0x81, 0x80)))
        assertFalse(acceptedMmsResponse(response(0x81, 0x81)))
        assertFalse(acceptedMmsResponse(response(0x84, 0x80)))
        assertFalse(acceptedMmsResponse(response(0x81, 0x80).copyOf(8)))
    }
}
