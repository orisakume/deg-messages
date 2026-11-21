package com.example.messages.core.design

import com.example.messages.data.sms.HighlightKind

internal const val RUPEE_HIGHLIGHT_COLOR = 0xFFFFD700
internal const val MESSAGE_HIGHLIGHT_BACKGROUND = 0xFF21342E

internal val HighlightKind.highlightColor: Long
    get() = when (this) {
        HighlightKind.Link -> 0xFF9AFFBF
        HighlightKind.Email -> 0xFFDDC7FF
        HighlightKind.Phone -> 0xFFC3E6FF
        HighlightKind.Otp -> 0xFFFFDC9C
    }
