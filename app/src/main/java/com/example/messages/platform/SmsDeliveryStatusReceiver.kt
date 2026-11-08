package com.example.messages.platform

import android.app.Activity
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
class SmsDeliveryStatusReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: SmsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.data?.pathSegments?.getOrNull(1)?.toLongOrNull() ?: return
        val delivered = resultCode == Activity.RESULT_OK
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { repository.updateDeliveryStatus(messageId, delivered) } finally { pending.finish() }
        }
    }
}
