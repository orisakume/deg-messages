package com.example.messages.platform

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.example.messages.MainActivity
import java.util.UUID

/** Only the app's immutable notification PendingIntent can enter through this activity. */
class NotificationEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1)
        val address = intent.getStringExtra(EXTRA_ADDRESS).orEmpty()
        if (threadId > 0 && address.isNotBlank() && address.length <= 512) {
            val main = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            NotificationRoutes.authorize(main, threadId, address)
            startActivity(main)
        }
        finish()
    }

    companion object {
        internal const val EXTRA_THREAD_ID = "threadId"
        internal const val EXTRA_ADDRESS = "address"
    }
}

internal object NotificationRoutes {
    private const val EXTRA_TOKEN = "notificationRouteToken"
    private val pending = LinkedHashMap<String, Pair<Long, String>>()

    @Synchronized
    fun authorize(intent: Intent, threadId: Long, address: String) {
        if (pending.size >= 8) pending.remove(pending.keys.first())
        val token = UUID.randomUUID().toString()
        pending[token] = threadId to address
        intent.putExtra(EXTRA_TOKEN, token)
    }

    @Synchronized
    fun consume(intent: Intent): Pair<Long, String>? {
        // MainActivity is exported for the launcher and SMS role. Its extras are untrusted.
        val token = try {
            intent.getStringExtra(EXTRA_TOKEN)
        } catch (_: RuntimeException) {
            null
        }
        return pending.remove(token ?: return null)
    }
}
