package com.example.messages.data.mms

import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.telephony.PhoneNumberUtils
import java.util.Locale
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.os.Bundle
import androidx.core.content.FileProvider
import com.example.messages.data.sms.SmsRecord
import com.example.messages.data.sms.canReplyToSmsAddress
import com.example.messages.platform.MmsSentReceiver
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ImageResolution(val label: String, val maxEdge: Int) {
    Auto("Auto", 1600), Original("Original", 0), HD("HD", 1600), SD("SD", 800);

    companion object {
        fun preferred(context: Context): ImageResolution {
            val name = context.getSharedPreferences("mms_settings", Context.MODE_PRIVATE).getString("resolution", Auto.name)
            return entries.firstOrNull { it.name == name } ?: Auto
        }
    }
}

class MmsStore(private val context: Context) {
    private val resolver = context.contentResolver
    private val errors = context.getSharedPreferences("mms_send_errors", Context.MODE_PRIVATE)
    private val payloadDirectory get() = File(context.cacheDir, "mms-payloads")

    suspend fun send(address: String, text: String, attachment: Uri, subscription: Int, resolution: ImageResolution): Long =
        withContext(Dispatchers.IO) {
            val destination = address.trim()
            require(canReplyToSmsAddress(destination)) { "Enter a valid phone number." }
            val feature = if (Build.VERSION.SDK_INT >= 33) PackageManager.FEATURE_TELEPHONY_MESSAGING else PackageManager.FEATURE_TELEPHONY
            check(context.packageManager.hasSystemFeature(feature)) { "MMS sending is unavailable on this device." }
            val sim = subscription.takeIf { it >= 0 } ?: SubscriptionManager.getDefaultSmsSubscriptionId()
            require(sim >= 0) { "Choose the SIM to use for this MMS." }
            val manager = managerFor(sim)
            val config = manager.carrierConfigValues ?: Bundle.EMPTY
            val carrier = carrierName(sim)
            check(config.getBoolean(SmsManager.MMS_CONFIG_MMS_ENABLED, true)) { "$carrier reports that MMS is disabled for this SIM." }
            val limit = sizeLimit(config)
            val caption = text.takeIf { it.isNotBlank() }.orEmpty()
            val textBytes = caption.toByteArray(Charsets.UTF_8)
            val attachmentLimit = limit - textBytes.size - MMS_OVERHEAD
            require(attachmentLimit > 0) { "The message exceeds your carrier’s MMS size limit." }
            val mime = resolver.getType(attachment).orEmpty().lowercase(Locale.ROOT)
            val media = if (mime.startsWith("video/")) readMmsVideo(context, attachment, mime, attachmentLimit)
                else prepareImage(attachment, resolution, attachmentLimit, config.getInt(SmsManager.MMS_CONFIG_MAX_IMAGE_WIDTH),
                    config.getInt(SmsManager.MMS_CONFIG_MAX_IMAGE_HEIGHT))
            val pdu = composeMmsPdu(carrierAddress(destination, sim), textBytes, media)
            require(pdu.size <= limit) { "This attachment exceeds the MMS limit for $carrier." }
            val thread = Telephony.Threads.getOrCreateThreadId(context, setOf(destination))
            val row = persist(thread, destination, caption, media, sim, pdu.size)
            val id = ContentUris.parseId(row)
            try { dispatch(manager, id, pdu, fallback = false) }
            catch (failure: Exception) {
                updateStatus(id, false, "Android could not start MMS sending.")
                throw failure
            }
            thread
        }

    @Suppress("DEPRECATION")
    private fun managerFor(sim: Int): SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        context.getSystemService(SmsManager::class.java).createForSubscriptionId(sim)
    else SmsManager.getSmsManagerForSubscriptionId(sim)

    private fun carrierName(sim: Int): String = runCatching {
        context.getSystemService(TelephonyManager::class.java)?.createForSubscriptionId(sim)?.simOperatorName
    }.getOrNull().orEmpty().ifBlank { "Your carrier" }

    private fun carrierAddress(address: String, sim: Int): String {
        val normalized = PhoneNumberUtils.extractNetworkPortion(address)
        val country = runCatching {
            context.getSystemService(TelephonyManager::class.java)?.createForSubscriptionId(sim)?.simCountryIso
        }.getOrNull().orEmpty()
        return if (country.isBlank()) normalized else PhoneNumberUtils.formatNumberToE164(normalized, country.uppercase(Locale.ROOT)) ?: normalized
    }

    private fun sizeLimit(config: Bundle): Int = config.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE)
        .takeIf { it > 0 }?.coerceAtMost(8 * 1024 * 1024) ?: 300_000

    private fun dispatch(manager: SmsManager, id: Long, pdu: ByteArray, fallback: Boolean) {
        val key = UUID.randomUUID().toString()
        val file = File(payloadDirectory, key)
        try {
            check(payloadDirectory.isDirectory || payloadDirectory.mkdirs())
            file.writeBytes(pdu)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.mms", file)
            val intent = Intent(context, MmsSentReceiver::class.java)
                .setData(Uri.parse("messages://mms/sent/$id/$key")).putExtra("compressed_retry", fallback)
            // The telephony service attaches the carrier response in the fill-in intent.
            val mutability = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val sent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_ONE_SHOT or mutability)
            manager.sendMultimediaMessage(context, uri, null, null, sent)
        } catch (failure: Exception) {
            file.delete()
            throw failure
        }
    }

    private fun prepareImage(uri: Uri, mode: ImageResolution, limit: Int, maxWidth: Int, maxHeight: Int): MmsAttachment {
        if (mode == ImageResolution.Original) {
            val data = resolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var length = input.read(buffer)
                while (length != -1) {
                    if (output.size() + length > limit) return prepareImage(uri, ImageResolution.Auto, limit, maxWidth, maxHeight)
                    output.write(buffer, 0, length)
                    length = input.read(buffer)
                }
                output.toByteArray()
            } ?: error("Could not read this image.")
            // Check the exact bytes we will send. A provider can return different bytes on each open.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            require(hasSafeMediaDimensions(bounds.outWidth, bounds.outHeight)) { "This image is invalid or too large." }
            val mime = bounds.outMimeType
            if (mime !in setOf("image/jpeg", "image/png", "image/gif", "image/webp") ||
                (maxWidth > 0 && bounds.outWidth > maxWidth) || (maxHeight > 0 && bounds.outHeight > maxHeight)) {
                return prepareImage(uri, ImageResolution.Auto, limit, maxWidth, maxHeight)
            }
            return MmsAttachment(requireNotNull(mime), data)
        }
        val source = ImageDecoder.createSource(resolver, uri)
        var edge = mode.maxEdge
        repeat(9) {
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                require(hasSafeMediaDimensions(width, height)) { "This image is invalid or too large." }
                val scale = minOf(1.0, edge.toDouble() / maxOf(width, height),
                    if (maxWidth > 0) maxWidth.toDouble() / width else 1.0,
                    if (maxHeight > 0) maxHeight.toDouble() / height else 1.0)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSize((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
            }
            try {
                val encoded = ByteArrayOutputStream()
                for (quality in (if (mode == ImageResolution.SD) listOf(82, 67, 50, 35) else listOf(92, 80, 65, 50, 35))) {
                    encoded.reset()
                    if (bitmap.compress(Bitmap.CompressFormat.JPEG, quality, encoded) && encoded.size() <= limit) {
                        return MmsAttachment("image/jpeg", encoded.toByteArray())
                    }
                }
            } finally {
                bitmap.recycle()
            }
            edge = edge * 3 / 4
        }
        error("This picture could not fit your carrier’s MMS limit.")
    }

    private fun persist(thread: Long, address: String, text: String, image: MmsAttachment, sim: Int, size: Int,
                        date: Long = System.currentTimeMillis(), box: Int = 4): Uri {
        val row = resolver.insert(Telephony.Mms.CONTENT_URI, ContentValues().apply {
            put("thread_id", thread)
            put("date", date / 1000)
            put("msg_box", box)
            put("read", 1)
            put("seen", 1)
            put("m_type", 128)
            put("v", 18)
            put("ct_t", "application/vnd.wap.multipart.related")
            put("m_size", size)
            put("sub_id", sim)
        }) ?: error("Could not save the MMS.")
        val id = ContentUris.parseId(row)
        val base = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, id)
        try {
            checkNotNull(resolver.insert(Uri.withAppendedPath(base, "addr"), ContentValues().apply {
                put("address", address)
                put("type", 151)
                put("charset", 106)
            }))
            val part = Uri.withAppendedPath(base, "part")
            val picture = resolver.insert(part, ContentValues().apply {
                put("ct", image.mime)
                put("seq", 0)
                put("cl", image.filename)
            }) ?: error("Could not save the MMS attachment.")
            checkNotNull(resolver.openOutputStream(picture)).use { it.write(image.bytes) }
            if (text.isNotBlank()) checkNotNull(resolver.insert(part, ContentValues().apply {
                put("ct", "text/plain")
                put("seq", 1)
                put("chset", 106)
                put("text", text)
            }))
            resolver.notifyChange(Telephony.Mms.CONTENT_URI, null)
            return row
        } catch (failure: Exception) {
            resolver.delete(base, null, null)
            throw failure
        }
    }

    fun finish(id: Long, token: String, success: Boolean, sizeRejected: Boolean, alreadyRetried: Boolean, failure: String?) {
        val payload = File(payloadDirectory, UUID.fromString(token).toString())
        try {
            if (sizeRejected && !alreadyRetried && retrySmaller(id, payload.length())) return
            updateStatus(id, success, failure)
        } finally { payload.delete() }
    }

    private fun retrySmaller(id: Long, priorSize: Long): Boolean = runCatching {
        val record = read(messageId = id).singleOrNull() ?: return false
        if (record.attachmentMime?.startsWith("video/") == true) return false
        val source = Uri.parse(checkNotNull(record.attachmentUri))
        val caption = record.body.toByteArray(Charsets.UTF_8)
        val manager = managerFor(record.subscriptionId)
        val config = manager.carrierConfigValues ?: Bundle.EMPTY
        val total = minOf(sizeLimit(config), (priorSize / 2).toInt())
        val imageLimit = total - caption.size - MMS_OVERHEAD
        require(imageLimit > 0)
        val picture = prepareImage(source, ImageResolution.SD, imageLimit, config.getInt(SmsManager.MMS_CONFIG_MAX_IMAGE_WIDTH),
            config.getInt(SmsManager.MMS_CONFIG_MAX_IMAGE_HEIGHT))
        val pdu = composeMmsPdu(carrierAddress(record.address, record.subscriptionId), caption, picture)
        check(pdu.size <= total)
        // Keep the same history row. A rejected request cannot have delivered a photo.
        dispatch(manager, id, pdu, fallback = true)
        true
    }.getOrDefault(false)

    private fun updateStatus(id: Long, success: Boolean, failure: String?) {
        if (success || failure == null) errors.edit().remove(id.toString()).apply()
        else errors.edit().putString(id.toString(), failure).apply()
        resolver.update(ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, id), ContentValues().apply {
            put("msg_box", if (success) 2 else 5)
            if (success) put("date_sent", System.currentTimeMillis() / 1000)
        }, null, null)
        resolver.notifyChange(Telephony.Mms.CONTENT_URI, null)
    }

    fun read(threadId: Long? = null, limit: Int = Int.MAX_VALUE, messageId: Long? = null): List<SmsRecord> {
        val rows = mutableListOf<SmsRecord>()
        val filter = "m_type=128 AND msg_box IN (2,4,5)"
        val selection = when {
            messageId != null -> "$filter AND _id=?"
            threadId != null -> "$filter AND thread_id=?"
            else -> filter
        }
        resolver.query(Telephony.Mms.CONTENT_URI, arrayOf("_id", "thread_id", "date", "msg_box", "sub_id"),
            selection, (messageId ?: threadId)?.let { arrayOf(it.toString()) }, "date DESC, _id DESC")?.use { cursor ->
            while (rows.size < limit && cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val base = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, id)
                var address = ""
                resolver.query(Uri.withAppendedPath(base, "addr"), arrayOf("address"), "type=151", null, null)?.use {
                    if (it.moveToFirst()) address = it.getString(0).orEmpty()
                }
                var text = ""
                var image: String? = null
                var mime: String? = null
                resolver.query(Uri.withAppendedPath(base, "part"), arrayOf("_id", "ct", "text"), null, null, "seq ASC")?.use {
                    while (it.moveToNext()) {
                        val type = it.getString(1).orEmpty()
                        if (type == "text/plain") text = it.getString(2).orEmpty()
                        if ((type.startsWith("image/") || type.startsWith("video/")) && image == null) {
                            image = "content://mms/part/${it.getLong(0)}"
                            mime = type
                        }
                    }
                }
                if (image == null) continue
                val type = when (cursor.getInt(3)) {
                    2 -> Telephony.Sms.MESSAGE_TYPE_SENT
                    5 -> Telephony.Sms.MESSAGE_TYPE_FAILED
                    else -> Telephony.Sms.MESSAGE_TYPE_OUTBOX
                }
                rows += SmsRecord(-id, cursor.getLong(1), address, text, cursor.getLong(2) * 1000,
                    true, type, Telephony.Sms.STATUS_NONE, cursor.getInt(4), image, mime, errors.getString(id.toString(), null))
            }
        }
        return rows
    }

    internal fun restoreOutgoing(address: String, body: String, image: MmsAttachment, date: Long, failed: Boolean) {
        val thread = Telephony.Threads.getOrCreateThreadId(context, setOf(address))
        persist(thread, address, body, image, SubscriptionManager.INVALID_SUBSCRIPTION_ID, image.bytes.size,
            date = date, box = if (failed) 5 else 2)
    }

    fun delete(id: Long): Boolean {
        val deleted = resolver.delete(ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, -id), null, null) == 1
        if (!deleted) return false
        errors.edit().remove((-id).toString()).apply()
        resolver.notifyChange(Telephony.Mms.CONTENT_URI, null)
        return true
    }

    private companion object { const val MMS_OVERHEAD = 2048 }

    fun removeOldPayloads() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        payloadDirectory.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }
}

internal data class MmsAttachment(val mime: String, val bytes: ByteArray, val durationMillis: Long = 5000) {
    val isVideo get() = mime.startsWith("video/")
    val filename get() = when (mime) {
        "image/jpeg" -> "photo.jpg"
        "video/mp4" -> "video.mp4"
        "video/3gpp" -> "video.3gp"
        else -> "photo." + mime.substringAfter('/')
    }
}
