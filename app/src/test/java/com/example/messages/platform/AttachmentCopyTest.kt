package com.example.messages.platform

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AttachmentCopyTest {
    @Test
    fun acceptsAnAttachmentAtTheCopyLimit() {
        val content = byteArrayOf(2, 4, 8)
        val output = ByteArrayOutputStream()
        content.inputStream().copyAttachmentTo(output, limit = content.size)
        assertArrayEquals(content, output.toByteArray())
    }

    @Test
    fun rejectsAnOversizeAttachment() {
        val output = ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java) {
            byteArrayOf(2, 4, 8).inputStream().copyAttachmentTo(output, limit = 2)
        }
    }
}
