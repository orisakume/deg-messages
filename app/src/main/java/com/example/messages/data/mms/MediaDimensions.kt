package com.example.messages.data.mms

internal fun hasSafeMediaDimensions(width: Int, height: Int): Boolean =
    width > 0 && height > 0 && width.toLong() * height <= 200_000_000L
