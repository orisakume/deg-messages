package com.example.messages.platform

import android.content.Context

internal fun isAppPinEnabled(context: Context): Boolean =
    context.getSharedPreferences("pin_lock", Context.MODE_PRIVATE).contains("verifier")
