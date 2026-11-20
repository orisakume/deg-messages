package com.example.messages.feature.conversation

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.messages.core.design.AppColors
import com.example.messages.core.design.ActionDropdown
import com.example.messages.core.design.ActionTone
import com.example.messages.core.design.SheetAction
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AddAttachmentButton(enabled: Boolean, onSelected: (Uri) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onSelected(uri)
    }
    Box {
        IconButton(enabled = enabled, onClick = { choosing = true }) {
            Icon(Icons.Default.AttachFile, "Attach media")
        }
        if (choosing) ActionDropdown(listOf(
            SheetAction("Photo", Icons.Default.Image, tone = ActionTone.Teal) { picker.launch("image/*") },
            SheetAction("Video", Icons.Default.Videocam, tone = ActionTone.Lilac) { picker.launch("video/*") },
        )) { choosing = false }
    }
}

@Composable
internal fun AttachedMedia(uri: Uri, onRemove: () -> Unit) {
    val context = LocalContext.current
    val video by produceState(false, uri) {
        value = withContext(Dispatchers.IO) { context.contentResolver.getType(uri)?.startsWith("video/") == true }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MessageImage(uri, Modifier.size(74.dp), if (video) "Attached video" else "Attached image", 180, video)
        IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Remove attachment") }
    }
}

@Composable
internal fun MessageImage(uri: Uri, modifier: Modifier, description: String, maxEdge: Int = 600, video: Boolean = false) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri, video) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (video) {
                    val metadata = MediaMetadataRetriever()
                    try {
                        metadata.setDataSource(context, uri)
                        val width = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: maxEdge
                        val height = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: maxEdge
                        val scale = minOf(1.0, maxEdge.toDouble() / maxOf(width, height, 1))
                        metadata.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            (width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
                    } finally { metadata.release() }
                } else ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    val width = info.size.width
                    val height = info.size.height
                    val scale = minOf(1.0, maxEdge.toDouble() / maxOf(width, height))
                    decoder.setTargetSize((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
                }
            }.getOrNull()
        }
    }
    val image = bitmap
    if (image != null) Image(image.asImageBitmap(), description, modifier, contentScale = ContentScale.Fit)
    else Text(if (video) "Video" else "Photo", color = AppColors.TextSecondary)
}

internal suspend fun mediaIntent(context: Context, source: Uri, mime: String, text: String, view: Boolean = false): Intent =
    withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "shared-images")
        check(directory.exists() || directory.mkdirs())
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        directory.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        val suffix = when (mime) {
            "image/jpeg" -> "jpg"
            "video/3gpp" -> "3gp"
            else -> mime.substringAfter('/').filter(Char::isLetterOrDigit).ifBlank { "media" }
        }
        val file = File.createTempFile("attachment-", ".$suffix", directory)
        try {
            val input = context.contentResolver.openInputStream(source) ?: error("Could not open this attachment.")
            input.use { stream -> file.outputStream().use { stream.copyTo(it) } }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.mms", file)
            val intent = if (view) Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                else Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, text)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri("Attachment", uri) }
        } catch (failure: Exception) {
            file.delete()
            throw failure
        }
    }
