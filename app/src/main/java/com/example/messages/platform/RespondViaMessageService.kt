package com.example.messages.platform

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.messages.data.sms.SmsRepository
import com.example.messages.data.sms.canReplyToSmsAddress
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RespondViaMessageService : Service() {
    @Inject lateinit var repository: SmsRepository

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.data?.schemeSpecificPart.orEmpty()
        val body = intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (canReplyToSmsAddress(address) && body.isNotBlank()) {
                    try {
                        repository.sendSms(address, body)
                    } catch (_: Exception) {
                        // The caller has no interactive surface for reporting an asynchronous send failure.
                    }
                }
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
