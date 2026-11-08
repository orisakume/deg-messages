package com.example.messages.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SubscriptionManager
import android.provider.Telephony
import com.example.messages.data.sms.SmsRepository
import com.example.messages.core.database.OtpRecordDao
import com.example.messages.core.database.OtpRecordEntity
import com.example.messages.data.otp.OtpDetector
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Saves platform-delivered SMS parts into Android's canonical SMS Provider. */
@AndroidEntryPoint
class SmsDeliveryReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: SmsRepository
    @Inject lateinit var otpDao: OtpRecordDao

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent).orEmpty()
        if (parts.isEmpty()) return
        val address = parts.firstNotNullOfOrNull { it.originatingAddress }.orEmpty()
        val body = parts.joinToString(separator = "") { it.messageBody.orEmpty() }
        val timestamp = parts.minOfOrNull { it.timestampMillis } ?: System.currentTimeMillis()
        val subscriptionId = intent.getIntExtra(
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            SubscriptionManager.INVALID_SUBSCRIPTION_ID,
        )
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val messageId = repository.receiveSms(address, body, timestamp, subscriptionId) ?: return@launch
                val otp = OtpDetector.detect(body)
                if (otp != null) {
                    otpDao.upsert(
                        OtpRecordEntity(
                            messageId,
                            otp,
                            address,
                            timestamp,
                            expiresAt = timestamp + OtpRecordEntity.EXPIRATION_MS,
                        ),
                    )
                }
                val threadId = repository.threadIdForMessage(messageId) ?: return@launch
                repository.setArchived(threadId, false)
                val blocked = repository.isBlockedNumber(address)
                if (blocked) repository.setSpam(threadId, true)
                if (!blocked && !repository.isSpam(threadId) && !repository.isMuted(threadId)) SmsNotifications.showIncoming(context, threadId, address, body, otp, messageId)
            } catch (_: SecurityException) {
                // SMS Provider permissions are controlled by the SMS role.
            } finally {
                pending.finish()
            }
        }
    }
}
