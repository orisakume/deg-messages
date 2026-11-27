package com.example.messages.platform

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import java.io.InputStream
import java.io.OutputStream

internal fun Context.canAccessPickedDocument(uri: Uri, mode: Int): Boolean {
    if (uri.scheme != ContentResolver.SCHEME_CONTENT) return false
    // Strip any cross-profile prefix before checking provider names.
    val authority = uri.authority?.substringAfterLast('@').orEmpty()
    if (authority.isEmpty() || authority == packageName || authority.startsWith("$packageName.") ||
        authority in setOf("sms", "mms", "mms-sms", "telephony", "com.android.contacts", "call_log", "settings")
    ) return false
    return try {
        checkUriPermission(uri, Process.myPid(), Process.myUid(), mode) == PackageManager.PERMISSION_GRANTED
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

internal const val MAX_ATTACHMENT_COPY_BYTES = 32 * 1024 * 1024

internal fun InputStream.copyAttachmentTo(output: OutputStream, limit: Int = MAX_ATTACHMENT_COPY_BYTES) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val count = read(buffer, 0, minOf(buffer.size, limit - total + 1))
        if (count < 0) return
        require(count <= limit - total) { "This attachment exceeds the copy limit." }
        output.write(buffer, 0, count)
        total += count
    }
}
