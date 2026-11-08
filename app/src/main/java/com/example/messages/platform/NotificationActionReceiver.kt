package com.example.messages.platform

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.messages.data.sms.SmsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: SmsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
        when (intent.action) {
            ACTION_COPY_NUMBER -> {
                val number = intent.getStringExtra(EXTRA_VALUE).orEmpty()
                if (number.isNotBlank()) {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("Phone number", number))
                    Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                }
            }
            ACTION_DELETE_MESSAGE -> {
                if (messageId < 0L) return
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        repository.deleteMessages(listOf(messageId))
                        if (threadId >= 0L) SmsNotifications.cancel(context, threadId)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_COPY_NUMBER = "com.example.messages.COPY_NUMBER"
        const val ACTION_DELETE_MESSAGE = "com.example.messages.DELETE_MESSAGE"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_THREAD_ID = "thread_id"
        const val EXTRA_VALUE = "value"
    }
}
