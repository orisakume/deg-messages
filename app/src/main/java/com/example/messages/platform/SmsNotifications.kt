package com.example.messages.platform

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.example.messages.MainActivity
import com.example.messages.MessagesApplication

object SmsNotifications {
    fun showIncoming(context: Context, threadId: Long, address: String, body: String, otpCode: String? = null, messageId: Long = -1L) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val openIntent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_THREAD_ID, threadId)
            .putExtra(MainActivity.EXTRA_ADDRESS, address)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val content = PendingIntent.getActivity(
            context, threadId.toInt(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
        )
        val readIntent = Intent(context, MarkReadReceiver::class.java)
            .setAction(MarkReadReceiver.ACTION_MARK_READ)
            .setData(Uri.parse("messages://read/$threadId"))
            .putExtra(MarkReadReceiver.EXTRA_THREAD_ID, threadId)
        val markRead = PendingIntent.getBroadcast(
            context, threadId.toInt(), readIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
        )
        val hidePreview = context.getSharedPreferences("notification_privacy", Context.MODE_PRIVATE).getBoolean("hide_preview", false)
        val displayBody = if (hidePreview) "New message" else body
        val notification = NotificationCompat.Builder(context, MessagesApplication.MESSAGE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(address)
            .setContentText(displayBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayBody))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(content)
            .addAction(android.R.drawable.checkbox_on_background, "Mark as read", markRead)
            .apply {
                if (otpCode != null && messageId >= 0L) {
                    val copyIntent = Intent(context, CopyOtpReceiver::class.java)
                        .setAction(CopyOtpReceiver.ACTION_COPY_OTP)
                        .setData(Uri.parse("messages://otp/copy/$messageId"))
                        .putExtra(CopyOtpReceiver.EXTRA_MESSAGE_ID, messageId)
                    val copy = PendingIntent.getBroadcast(context, messageId.toInt(), copyIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag())
                    addAction(android.R.drawable.ic_menu_save, "Copy OTP", copy)
                } else {
                    val number = detectSinglePhoneNumber(body)
                    if (number != null && messageId >= 0L) {
                        val numberIntent = Intent(context, NotificationActionReceiver::class.java)
                            .setAction(NotificationActionReceiver.ACTION_COPY_NUMBER)
                            .setData(Uri.parse("messages://number/copy/$messageId"))
                            .putExtra(NotificationActionReceiver.EXTRA_VALUE, number)
                        val numberAction = PendingIntent.getBroadcast(context, messageId.toInt(), numberIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag())
                        addAction(android.R.drawable.ic_menu_save, "Copy number", numberAction)
                    } else if (messageId >= 0L) {
                        val deleteIntent = Intent(context, NotificationActionReceiver::class.java)
                            .setAction(NotificationActionReceiver.ACTION_DELETE_MESSAGE)
                            .setData(Uri.parse("messages://message/delete/$messageId"))
                            .putExtra(NotificationActionReceiver.EXTRA_MESSAGE_ID, messageId)
                            .putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, threadId)
                        val delete = PendingIntent.getBroadcast(context, messageId.toInt(), deleteIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag())
                        addAction(android.R.drawable.ic_menu_delete, "Delete", delete)
                    }
                }
            }
            .build()
        try {
            NotificationManagerCompat.from(context).notify(threadId.toInt(), notification)
        } catch (_: SecurityException) {
            // Notification access can be revoked while the app is running.
        }
    }

    fun cancel(context: Context, threadId: Long) = NotificationManagerCompat.from(context).cancel(threadId.toInt())

    private fun immutableFlag(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    private fun detectSinglePhoneNumber(body: String): String? {
        val candidates = Regex("(?<!\\d)(?:\\+?\\d[\\d\\s().-]{6,}\\d)(?!\\d)")
            .findAll(body)
            .map { it.value.trim() }
            .filter { value -> value.count(Char::isDigit) in 7..15 }
            .distinct()
            .toList()
        return candidates.singleOrNull()
    }
}
