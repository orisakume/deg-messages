package com.example.messages.data.sms

import android.content.ContentProviderOperation
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Base64
import com.example.messages.data.mms.MmsStore
import com.example.messages.data.mms.MmsAttachment
import java.security.MessageDigest
import android.provider.Telephony.Sms
import com.example.messages.platform.DefaultSmsRole
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class BackupPhotoKey(val address: String, val body: String, val seconds: Long, val mime: String, val digest: String)

internal fun backupPhotoKey(address: String, body: String, date: Long, image: MmsAttachment): BackupPhotoKey {
    val digest = MessageDigest.getInstance("SHA-256").digest(image.bytes).joinToString("") { "%02x".format(it) }
    // Android's MMS provider persists dates in whole seconds.
    return BackupPhotoKey(address, body, date / 1000, image.mime, digest)
}

internal class MessageBackup(private val context: Context) {
    private val resolver = context.contentResolver
    private val mms = MmsStore(context)
    private val pictures = setOf("image/jpeg", "image/png", "image/gif", "image/webp")
    private data class Photo(val address: String, val body: String, val date: Long, val failed: Boolean, val image: MmsAttachment) {
        fun key() = backupPhotoKey(address, body, date, image)
    }
    private data class Entry(val address: String, val body: String, val date: Long, val type: Int, val read: Boolean)
    private val columns = arrayOf(Sms.ADDRESS, Sms.BODY, Sms.DATE, Sms.TYPE, Sms.READ)
    private val types = setOf(Sms.MESSAGE_TYPE_INBOX, Sms.MESSAGE_TYPE_SENT, Sms.MESSAGE_TYPE_FAILED)

    suspend fun write(uri: Uri): Int = withContext(Dispatchers.IO) {
        check(DefaultSmsRole.isHeld(context)) { "Set DEG Messages as the default SMS app first." }
        val json = JSONArray()
        val rows = readProvider()
        rows.forEach { entry ->
            json.put(JSONObject().put("address", entry.address).put("body", entry.body)
                .put("date", entry.date).put("type", entry.type).put("read", entry.read))
        }
        val photos = JSONArray()
        var imageBudget = maxBytes * 3 / 4
        settledPhotos().forEach { record ->
            val photo = loadPhoto(record)
            imageBudget -= photo.image.bytes.size
            require(imageBudget >= 0) { "This message backup exceeds the 32 MiB limit." }
            photos.put(JSONObject().put("address", photo.address).put("body", photo.body).put("date", photo.date)
                .put("failed", photo.failed).put("mime", photo.image.mime)
                .put("image", Base64.encodeToString(photo.image.bytes, Base64.NO_WRAP)))
        }
        val bytes = JSONObject().put("format", "deg-sms-backup").put("version", 2).put("messages", json)
            .put("photos", photos).toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= maxBytes) { "This message backup exceeds the 32 MiB limit." }
        val output = resolver.openOutputStream(uri, "wt") ?: error("Could not open the backup destination.")
        output.use { it.write(bytes) }
        rows.size + photos.length()
    }

    suspend fun restore(uri: Uri): Int = withContext(Dispatchers.IO) {
        check(DefaultSmsRole.isHeld(context)) { "Set DEG Messages as the default SMS app first." }
        val data = checkNotNull(resolver.openInputStream(uri)) { "Could not open the backup file." }.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                require(output.size() + count <= maxBytes) { "This message backup exceeds the 32 MiB limit." }
                output.write(buffer, 0, count)
            }
            JSONObject(output.toString("UTF-8"))
        }
        require(data.optString("format") == "deg-sms-backup" && data.optInt("version") in 1..2) { "Choose a DEG Messages backup." }
        val json = data.getJSONArray("messages")
        val entries = List(json.length()) { index ->
            val row = json.getJSONObject(index)
            Entry(row.getString("address"), row.getString("body"), row.getLong("date"), row.getInt("type"), row.getBoolean("read"))
                .also { require(it.address.isNotBlank() && it.address.length <= 512 && it.body.length <= 45_000 && it.date >= 0 && it.type in types) { "This SMS backup contains an invalid message." } }
        }
        val photos = data.optJSONArray("photos") ?: JSONArray()
        val photoEntries = List(photos.length()) { index ->
            val row = photos.getJSONObject(index)
            val address = row.getString("address")
            val body = row.getString("body")
            val date = row.getLong("date")
            val mime = row.getString("mime")
            val bytes = Base64.decode(row.getString("image"), Base64.NO_WRAP)
            require(canReplyToSmsAddress(address) && address.length <= 512 && body.length <= 45_000 && date >= 0 && mime in pictures && bytes.isNotEmpty() && bytes.size <= maxImageBytes) {
                "This backup contains an invalid photo message."
            }
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outMimeType == mime && bounds.outWidth > 0 && bounds.outHeight > 0) { "A backup image is damaged." }
            Photo(address, body, date, row.getBoolean("failed"), MmsAttachment(mime, bytes))
        }
        val existingPhotos = if (photoEntries.isEmpty()) mutableSetOf() else settledPhotos().mapNotNull {
            runCatching { loadPhoto(it).key() }.getOrNull()
        }.toMutableSet()
        val existing = readProvider().map { it.copy(read = false) }.toHashSet()
        val missing = entries.filter { existing.add(it.copy(read = false)) }
        missing.chunked(8).forEach { chunk ->
            val operations = ArrayList(chunk.map { entry ->
                ContentProviderOperation.newInsert(Sms.CONTENT_URI)
                    .withValue(Sms.ADDRESS, entry.address)
                    .withValue(Sms.THREAD_ID, android.provider.Telephony.Threads.getOrCreateThreadId(context, entry.address))
                    .withValue(Sms.BODY, entry.body)
                    .withValue(Sms.DATE, entry.date)
                    .withValue(Sms.TYPE, entry.type)
                    .withValue(Sms.READ, if (entry.read) 1 else 0)
                    .withValue(Sms.SEEN, 1)
                    .build()
            })
            resolver.applyBatch(Sms.CONTENT_URI.authority!!, operations)
        }
        var restored = missing.size
        photoEntries.filter { existingPhotos.add(it.key()) }.forEach {
            mms.restoreOutgoing(it.address, it.body, it.image, it.date, it.failed)
            restored++
        }
        restored
    }

    private fun settledPhotos() = mms.read().filter { it.type != Sms.MESSAGE_TYPE_OUTBOX && it.attachmentMime in pictures && canReplyToSmsAddress(it.address) }

    private fun loadPhoto(record: SmsRecord): Photo {
        val uri = Uri.parse(checkNotNull(record.attachmentUri))
        val bytes = checkNotNull(resolver.openInputStream(uri)) { "Could not read a photo attachment." }.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                require(output.size() + count <= maxImageBytes) { "A photo in the backup exceeds 10 MiB." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        return Photo(record.address, record.body, record.date, record.type == Sms.MESSAGE_TYPE_FAILED,
            MmsAttachment(checkNotNull(record.attachmentMime), bytes))
    }

    private fun readProvider(): List<Entry> = buildList {
        resolver.query(Sms.CONTENT_URI, columns, "type IN (1,2,5)", null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (!cursor.getString(0).isNullOrBlank()) add(cursor.entry())
            }
        } ?: error("Android could not read SMS history.")
    }

    private fun Cursor.entry() = Entry(getString(0), getString(1).orEmpty(), getLong(2), getInt(3), getInt(4) != 0)

    private companion object {
        const val maxBytes = 32 * 1024 * 1024
        const val maxImageBytes = 10 * 1024 * 1024
    }
}
