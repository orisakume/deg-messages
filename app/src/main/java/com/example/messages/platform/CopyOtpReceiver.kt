package com.example.messages.platform

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.messages.core.database.OtpRecordDao
import com.example.messages.feature.otp.shouldRemoveOtpAfterCopy
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CopyOtpReceiver : BroadcastReceiver() {
    @Inject lateinit var dao: OtpRecordDao
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        if (id < 0L) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val record = dao.get(id) ?: return@launch
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("Verification code", record.code))
                    ?: return@launch
                if (dao.incrementCopyCount(id) > 0 &&
                    shouldRemoveOtpAfterCopy(dao.get(id)?.copyCount ?: com.example.messages.core.database.OtpRecordEntity.MAX_COPIES)
                ) {
                    dao.delete(listOf(id))
                }
                android.os.Handler(context.mainLooper).post { Toast.makeText(context, "OTP copied", Toast.LENGTH_SHORT).show() }
            } finally { pending.finish() }
        }
    }
    companion object { const val ACTION_COPY_OTP = "com.example.messages.COPY_OTP"; const val EXTRA_MESSAGE_ID = "message_id" }
}
