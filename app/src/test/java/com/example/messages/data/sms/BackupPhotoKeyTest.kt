package com.example.messages.data.sms

import com.example.messages.data.mms.MmsAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BackupPhotoKeyTest {
    @Test
    fun providerTimestampRoundingKeepsSamePhotoIdentity() {
        val picture = MmsAttachment("image/png", byteArrayOf(1, 2, 3))
        val rounded = backupPhotoKey("+12025550117", "Photo", 12_000, picture)
        assertEquals(rounded, backupPhotoKey("+12025550117", "Photo", 12_876, picture))
        assertNotEquals(rounded, backupPhotoKey("+12025550117", "Photo", 12_876, picture.copy(bytes = byteArrayOf(4))))
    }
}
