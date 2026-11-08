package com.example.messages.feature.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.messages.core.design.AppColors
import com.example.messages.data.recyclebin.RecycleBinRepository

@Composable
fun SettingsScreen(onRecycleBin: () -> Unit, onSpam: () -> Unit, onAbout: () -> Unit) {
    val context = LocalContext.current
    val recyclePrefs = remember { context.getSharedPreferences(RecycleBinRepository.PREFS, android.content.Context.MODE_PRIVATE) }
    val privacyPrefs = remember { context.getSharedPreferences("notification_privacy", android.content.Context.MODE_PRIVATE) }
    var recycleEnabled by remember { mutableStateOf(recyclePrefs.getBoolean(RecycleBinRepository.KEY_ENABLED, true)) }
    var retention by remember { mutableIntStateOf(recyclePrefs.getInt(RecycleBinRepository.KEY_DAYS, 30)) }
    var showMessageContent by remember { mutableStateOf(!privacyPrefs.getBoolean("hide_preview", false)) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium) }
        item { Text("RECYCLE BIN", color = AppColors.AccentGreen, style = MaterialTheme.typography.labelLarge) }
        item {
            SettingToggle("Keep deleted messages", "Save a private recovery copy before removing messages from Android storage.", recycleEnabled) { enabled ->
                recycleEnabled = enabled; recyclePrefs.edit().putBoolean(RecycleBinRepository.KEY_ENABLED, enabled).apply()
            }
        }
        if (recycleEnabled) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Permanently delete after", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 15, 30).forEach { days ->
                        FilterChip(selected = retention == days, onClick = { retention = days; recyclePrefs.edit().putInt(RecycleBinRepository.KEY_DAYS, days).apply() }, label = { Text("$days days") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AppColors.AccentGreen, selectedLabelColor = AppColors.Background))
                    }
                }
            }
        }
        item { SettingLink("Open Recycle Bin", "Restore or permanently delete saved messages", Icons.Default.DeleteOutline, onRecycleBin) }
        item { Text("OTP EXPIRY", color = AppColors.AccentGreen, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp)) }
        item {
            Text(
                "OTP cards disappear after 24 hours or after four copies. The original SMS remains in its conversation.",
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        item { Text("PRIVACY", color = AppColors.AccentGreen, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp)) }
        item {
            SettingToggle("Show Message Content in Notifications", "When off, notifications show the sender and a generic new-message label.", showMessageContent) { enabled ->
                showMessageContent = enabled; privacyPrefs.edit().putBoolean("hide_preview", !enabled).apply()
            }
        }
        item { SettingLink("Notifications", "Android controls channel sounds and lock-screen behavior", Icons.Default.Notifications) {
            val intent = if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
            context.startActivity(intent)
        } }
        item { SettingLink("Spam & blocked numbers", "Local filtering only; no carrier blocking guarantee", Icons.Default.Security, onSpam) }
        item { Text("GENERAL", color = AppColors.AccentGreen, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp)) }
        item { SettingLink("About", "Version, release notes, included features and limitations", Icons.Default.Info, onAbout) }
    }
}

@Composable
private fun SettingToggle(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(summary, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = AppColors.Background, checkedTrackColor = AppColors.AccentGreen))
    }
}

@Composable
private fun SettingLink(title: String, summary: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
        Icon(icon, contentDescription = null, tint = AppColors.TextSecondary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) { Text(title, color = AppColors.TextPrimary); Text(summary, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.TextSecondary)
    }
}
