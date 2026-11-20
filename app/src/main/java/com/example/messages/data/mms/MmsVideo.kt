package com.example.messages.data.mms

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.util.Locale

internal fun readMmsVideo(context: Context, uri: Uri, mime: String, limit: Int): MmsAttachment {
    val canonical = when (mime.lowercase(Locale.ROOT)) {
        "video/mp4" -> "video/mp4"
        "video/3gpp", "video/3gp" -> "video/3gpp"
        else -> error("Choose an MP4 or 3GP video for MMS.")
    }

    val resolver = context.contentResolver
    val bytes = checkNotNull(resolver.openInputStream(uri)) { "Could not open the video." }.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit) {
                "This video exceeds the carrier limit of ${limit / 1024} KiB for the attachment. Choose a shorter or smaller clip."
            }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
    val metadata = MediaMetadataRetriever()
    val duration = try {
        metadata.setDataSource(context, uri)
        val detected = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        require(detected == "video/mp4" || detected == "video/3gpp") { "Choose a valid MP4 or 3GP video." }
        require(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") { "This file has no video track." }
        metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
    } finally {
        metadata.release()
    }
    require(duration != null && duration > 0) { "Could not read the video duration." }
    return MmsAttachment(canonical, bytes, duration)
}
