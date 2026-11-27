package com.example.messages.platform

import android.content.ClipData
import android.os.PersistableBundle

internal fun sensitiveOtpClip(code: String): ClipData =
    ClipData.newPlainText("Verification code", code).apply {
        // The literal is also supported on devices that predate the Android 13 constant.
        description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
