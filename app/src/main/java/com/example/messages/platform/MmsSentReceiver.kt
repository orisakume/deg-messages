package com.example.messages.platform

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.example.messages.data.mms.acceptedMmsResponse
import com.example.messages.data.mms.MmsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.data?.pathSegments?.getOrNull(1)?.toLongOrNull() ?: return
        val token = intent.data?.lastPathSegment ?: return
        val response = intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA)
        val success = resultCode == Activity.RESULT_OK && (response == null || acceptedMmsResponse(response))
        val code = resultCode
        val http = intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, 0)
        val sizeRejected = code == SmsManager.MMS_ERROR_HTTP_FAILURE && http == 413
        val failure = when (code) {
            Activity.RESULT_OK -> if (success) null else "The carrier rejected the MMS."
            SmsManager.MMS_ERROR_NO_DATA_NETWORK, SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS ->
                "Android could not connect to the carrier’s MMS network. Check mobile data and carrier MMS support for this SIM."
            SmsManager.MMS_ERROR_INVALID_APN, SmsManager.MMS_ERROR_CONFIGURATION_ERROR ->
                "The carrier MMS APN or configuration is unavailable."
            SmsManager.MMS_ERROR_HTTP_FAILURE -> if (sizeRejected) "The carrier rejected the attachment size."
                else if (http > 0) "The MMS server returned HTTP $http." else "The MMS server rejected the request."
            SmsManager.MMS_ERROR_INACTIVE_SUBSCRIPTION, SmsManager.MMS_ERROR_INVALID_SUBSCRIPTION_ID -> "The selected SIM is inactive or unavailable."
            SmsManager.MMS_ERROR_MMS_DISABLED_BY_CARRIER -> "MMS is disabled by the carrier for this SIM."
            SmsManager.MMS_ERROR_DATA_DISABLED -> "Mobile data is disabled for this SIM."
            else -> "Android could not send the MMS (code $code)."
        }
        android.util.Log.i("DegMms", "Send result=$code HTTP=$http accepted=$success")
        val alreadyRetried = intent.getBooleanExtra("compressed_retry", false)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MmsStore(context.applicationContext).finish(id, token, success, sizeRejected, alreadyRetried, failure)
            } catch (failure: Exception) {
                android.util.Log.w("MmsSentReceiver", "Could not update MMS status", failure)
            } finally {
                pending.finish()
            }
        }
    }
}
