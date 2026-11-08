package com.example.messages.platform

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.provider.Telephony

object DefaultSmsRole {
    fun isHeld(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_SMS) == true
    } else {
        @Suppress("DEPRECATION")
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }
}
