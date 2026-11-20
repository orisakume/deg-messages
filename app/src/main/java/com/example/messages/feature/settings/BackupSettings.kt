package com.example.messages.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.messages.core.design.AppColors
import com.example.messages.data.sms.MessageBackup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BackupSettings() {
    val context = LocalContext.current
    val store = remember(context) { PinStore(context) }
    val backup = remember(context) { MessageBackup(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var authorize by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf<Uri?>(null) }
    fun run(action: suspend () -> String) {
        busy = true
        status = null
        scope.launch {
            try { status = action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { status = failure.message ?: "Backup operation failed." }
            finally { busy = false }
        }
    }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) run { "Backed up ${backup.write(uri)} messages." }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { importing = it }
    fun chooseDestination() { create.launch("DEG-Messages-${java.time.LocalDate.now()}.json") }

    SettingsGroup("Message history") {
        SettingsLink("Create backup", "Export SMS and outgoing photos to a readable file", Icons.Default.FileUpload,
            SettingsDestination.Backup.accent, enabled = !busy) {
            if (store.enabled) authorize = true else chooseDestination()
        }
        SettingsDivider()
        SettingsLink("Restore backup", "Import missing messages from a DEG Messages backup", Icons.Default.FileDownload,
            SettingsDestination.Backup.accent, enabled = !busy) {
            open.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
    }
    if (busy || status != null) Text(
        status ?: "Working…",
        modifier = Modifier.padding(horizontal = 4.dp),
        color = AppColors.TextSecondary,
        style = MaterialTheme.typography.bodyMedium,
    )
    if (authorize) {
        VerifyBackupCredential(store, onCancel = { authorize = false }) {
            authorize = false
            chooseDestination()
        }
    }
    importing?.let { uri ->
        AlertDialog(onDismissRequest = { importing = null }, title = { Text("Restore message history?") },
            text = { Text("Adds missing messages to your existing history.") },
            confirmButton = { TextButton(onClick = {
                importing = null
                run { "Restored ${backup.restore(uri)} messages." }
            }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { importing = null }) { Text("Cancel") } })
    }
}

@Composable
private fun VerifyBackupCredential(store: PinStore, onCancel: () -> Unit, onVerified: () -> Unit) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onCancel, title = { Text("Verify app lock to create a backup") },
        text = {
            Column {
                CredentialField(if (store.password) "Password" else "PIN", input, store.password) { input = it; error = null }
                error?.let { Text(it, color = AppColors.Danger) }
                if (store.fingerprint) FingerprintButton("Verify fingerprint", !busy, onVerified)
            }
        },
        confirmButton = { TextButton(enabled = input.isNotEmpty() && !busy, onClick = {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { store.check(input) }.getOrElse { "Verification unavailable." } }
                busy = false
                if (result == null) onVerified() else error = result
            }
        }) { Text("Verify") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } })
}
