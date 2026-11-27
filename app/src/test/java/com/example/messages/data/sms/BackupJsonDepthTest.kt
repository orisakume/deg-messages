package com.example.messages.data.sms

import org.junit.Assert.assertThrows
import org.junit.Test

class BackupJsonDepthTest {
    @Test
    fun allowsBracketsApostrophesAndLinksInMessageText() {
        checkBackupJsonDepth(
            """{"messages":[{"body":"It's a link: https://example.com/ # [ { \"quote\""}]}"""
        )
    }

    @Test
    fun rejectsNestingBeyondTheImportLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            checkBackupJsonDepth("[".repeat(17) + "]".repeat(17))
        }
    }
}
