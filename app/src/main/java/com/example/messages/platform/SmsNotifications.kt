package com.example.messages.platform

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.view.View
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import android.Manifest
import android.content.pm.PackageManager
import com.example.messages.MessagesApplication
import com.example.messages.R
import com.example.messages.core.design.MESSAGE_HIGHLIGHT_BACKGROUND
import com.example.messages.core.design.RUPEE_HIGHLIGHT_COLOR
import com.example.messages.core.design.highlightColor
import com.example.messages.data.sms.messageHighlights
import com.example.messages.data.sms.ContactDetails
import com.example.messages.data.sms.smsSenderBrand
import java.io.IOException

object SmsNotifications {
    suspend fun showIncoming(
        context: Context,
        threadId: Long,
        address: String,
        body: String,
        otpCode: String? = null,
        messageId: Long = -1L,
        lookupSender: suspend () -> ContactDetails,
    ) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val openIntent = Intent(context, NotificationEntryActivity::class.java)
            .setData(Uri.parse("messages://conversation/$threadId"))
            .putExtra(NotificationEntryActivity.EXTRA_THREAD_ID, threadId)
            .putExtra(NotificationEntryActivity.EXTRA_ADDRESS, address)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
        val pinEnabled = isAppPinEnabled(context)
        val hidePreview = pinEnabled || notificationPreviewHidden(context)
        val displayBody = if (hidePreview) "New message" else highlightedPreview(body.take(5_120))
        val sender = if (pinEnabled) null else lookupSender()
        val title = if (pinEnabled) "DEG Messages" else sender?.displayName.orEmpty().ifBlank { address }
        val photo = if (pinEnabled) null else {
            sender?.photoUri?.takeIf { it.isNotBlank() }?.let { loadContactPhoto(context, it) }
                ?: smsSenderBrand(address)?.let { ContextCompat.getDrawable(context, it.logo)?.toBitmap(128, 128) }
        }
        val notification = NotificationCompat.Builder(context, MessagesApplication.MESSAGE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(title)
            .setContentText(displayBody)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, MessagesApplication.MESSAGE_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.sym_action_chat)
                    .setContentTitle("DEG Messages")
                    .setContentText("New message")
                    .build()
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(content)
            .addAction(android.R.drawable.checkbox_on_background, "Mark as read", markRead)
            .apply {
                if (displayBody is Spanned) {
                    // The standard Android template can recolor or strip styled text.
                    setStyle(NotificationCompat.DecoratedCustomViewStyle())
                    setCustomContentView(previewView(context, title, displayBody, photo, maxLines = 1))
                    setCustomBigContentView(previewView(context, title, displayBody, photo, maxLines = 7))
                    setCustomHeadsUpContentView(previewView(context, title, displayBody, photo, maxLines = 1))
                } else {
                    if (photo != null) setLargeIcon(photo)
                    setStyle(NotificationCompat.BigTextStyle().bigText(displayBody))
                }
                if (hidePreview) return@apply
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

    private fun loadContactPhoto(context: Context, uri: String): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))) { decoder, info, _ ->
            val longestSide = maxOf(info.size.width, info.size.height)
            if (longestSide > 128) {
                decoder.setTargetSize(
                    (info.size.width * 128 / longestSide).coerceAtLeast(1),
                    (info.size.height * 128 / longestSide).coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun previewView(context: Context, sender: String, body: Spanned, photo: Bitmap?, maxLines: Int): RemoteViews =
        RemoteViews(context.packageName, R.layout.notification_highlighted_message).apply {
            if (photo != null) {
                setImageViewBitmap(R.id.notification_sender_photo, photo)
                setViewVisibility(R.id.notification_sender_photo, View.VISIBLE)
            }
            setTextViewText(R.id.notification_sender, sender)
            setTextViewText(R.id.notification_message, body)
            setInt(R.id.notification_message, "setMaxLines", maxLines)
        }

    private fun highlightedPreview(body: String): CharSequence {
        val highlights = messageHighlights(body)
        if (highlights.actions.isEmpty() && highlights.rupeeMarkers.isEmpty()) return body
        val preview = SpannableString(body)
        fun style(range: IntRange, color: Long, underline: Boolean) {
            val start = range.first
            val end = range.last + 1
            val flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            preview.setSpan(ForegroundColorSpan(color.toInt()), start, end, flags)
            preview.setSpan(BackgroundColorSpan(MESSAGE_HIGHLIGHT_BACKGROUND.toInt()), start, end, flags)
            preview.setSpan(StyleSpan(Typeface.BOLD), start, end, flags)
            if (underline) preview.setSpan(UnderlineSpan(), start, end, flags)
        }
        highlights.actions.forEach { style(it.range, it.kind.highlightColor, underline = true) }
        highlights.rupeeMarkers.forEach { style(it, RUPEE_HIGHLIGHT_COLOR, underline = false) }
        return preview
    }

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

internal fun notificationPreviewHidden(context: Context): Boolean =
    context.getSharedPreferences("notification_privacy", Context.MODE_PRIVATE).getBoolean("hide_preview", false)
