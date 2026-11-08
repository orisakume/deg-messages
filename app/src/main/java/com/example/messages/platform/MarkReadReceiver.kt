package com.example.messages.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.messages.data.sms.SmsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MarkReadReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: SmsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
        if (threadId < 0) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.markIncomingReadForThread(threadId)
                SmsNotifications.cancel(context, threadId)
            } finally { pending.finish() }
        }
    }

    companion object {
        const val ACTION_MARK_READ = "com.example.messages.MARK_READ"
        const val EXTRA_THREAD_ID = "threadId"
    }
}
