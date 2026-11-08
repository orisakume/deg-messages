package com.example.messages.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/** Minimal WAP_PUSH_DELIVER endpoint retained for Android SMS-role eligibility. MMS handling is deferred to Version 2. */
class MmsDeliveryReceiver : BroadcastReceiver() {
    // Version 1 does not process or persist MMS payloads.
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        // MMS handling is intentionally deferred; do not parse or persist the payload here.
    }
}
