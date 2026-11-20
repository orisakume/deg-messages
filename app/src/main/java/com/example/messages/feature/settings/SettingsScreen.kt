package com.example.messages.feature.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.example.messages.core.design.AppColors
import com.example.messages.data.mms.ImageResolution
import com.example.messages.data.recyclebin.RecycleBinRepository

internal enum class SettingsDestination(
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val accent: Color,
    val route: String,
    val hasSettingsPage: Boolean = true,
) {
    Media("Photos in MMS", "Image resolution and carrier limits", Icons.Default.Image, Color(0xFF91BEFF), "settings/media"),
    Scheduled("Scheduled SMS", "Manage messages queued to send", Icons.Default.Schedule, Color(0xFF8ADCD0), "scheduled", false),
    Notifications("Notifications", "Message previews, sounds and lock screen", Icons.Default.Notifications, Color(0xFFFFC780), "settings/notifications"),
    AppLock("App lock", "PIN, password and fingerprint", Icons.Default.Lock, Color(0xFFCDB2FF), "settings/app-lock"),
    Spam("Spam protection", "Automatic filtering and blocked senders", Icons.Default.Shield, Color(0xFFFFA4AF), "settings/spam"),
    Otp("OTP expiry", "When verification cards disappear", Icons.Default.VerifiedUser, Color(0xFF9CDF9E), "settings/otp"),
    Backup("Backup and restore", "Export or import message history", Icons.Default.Backup, Color(0xFF83DAFC), "settings/backup"),
    Recycle("Recycle Bin", "Recover deleted SMS and set retention", Icons.Default.DeleteOutline, Color(0xFFFFB39E), "settings/recycle"),
    Appearance("Appearance", "Bottom navigation bar style", Icons.Default.Palette, Color(0xFFB7BCF7), "settings/appearance"),
    About("About DEG Messages", "Version and release notes", Icons.Default.Info, Color(0xFFC0C6DD), "about", false),
}

private val settingsCard = Color(0xFF17191E)
private val iconTile = RoundedCornerShape(13.dp)

@Composable
internal fun SettingsScreen(onOpen: (SettingsDestination) -> Unit, bottomBarSpace: Dp = 0.dp) {
    SettingsPage("Settings", bottomBarSpace = bottomBarSpace) {
        SettingsGroup("Messaging") {
            DestinationLinks(onOpen, SettingsDestination.Media, SettingsDestination.Scheduled, SettingsDestination.Notifications)
        }
        SettingsGroup("Privacy and safety") {
            DestinationLinks(onOpen, SettingsDestination.AppLock, SettingsDestination.Spam, SettingsDestination.Otp)
        }
        SettingsGroup("Data and storage") {
            DestinationLinks(onOpen, SettingsDestination.Backup, SettingsDestination.Recycle)
        }
        SettingsGroup("App") { DestinationLinks(onOpen, SettingsDestination.Appearance, SettingsDestination.About) }
    }
}

@Composable
internal fun SettingsDetailScreen(
    destination: SettingsDestination,
    onBack: () -> Unit,
    onOpenRecycleBin: () -> Unit,
    onOpenSpam: () -> Unit,
    transparentNavigationBar: Boolean,
    onTransparentNavigationBarChange: (Boolean) -> Unit,
) {
    SettingsPage(destination.title, onBack) {
        when (destination) {
            SettingsDestination.Media -> MediaSettings()
            SettingsDestination.Notifications -> NotificationSettings()
            SettingsDestination.AppLock -> SettingsGroup("Verification") { PinLockSetting() }
            SettingsDestination.Spam -> SpamSettings(onOpenSpam)
            SettingsDestination.Otp -> SettingsGroup("Verification cards") {
                SettingsNote("OTP cards disappear after 24 hours or four copies. The original SMS stays in the conversation.")
            }
            SettingsDestination.Backup -> BackupSettings()
            SettingsDestination.Recycle -> RecycleSettings(onOpenRecycleBin)
            SettingsDestination.Appearance -> SettingsGroup("Navigation") {
                SettingsToggle("Transparent bottom bar", "Let content show through the Chats, OTPs and Settings bar",
                    SettingsDestination.Appearance, transparentNavigationBar, onTransparentNavigationBarChange)
            }
            SettingsDestination.Scheduled, SettingsDestination.About -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPage(title: String, onBack: (() -> Unit)? = null, bottomBarSpace: Dp = 0.dp, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxHeight().widthIn(max = 680.dp).fillMaxWidth()) {
            if (onBack != null) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background),
                )
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)
                    .padding(start = 18.dp, end = 18.dp, top = if (onBack == null) 18.dp else 8.dp, bottom = 24.dp + bottomBarSpace),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (onBack == null) Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 3.dp, bottom = 4.dp))
                content()
            }
        }
    }
}

@Composable
internal fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, color = AppColors.TextSecondary, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 8.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = settingsCard, contentColor = AppColors.TextPrimary) {
            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp), content = content)
        }
    }
}

@Composable
private fun DestinationLinks(onOpen: (SettingsDestination) -> Unit, vararg destinations: SettingsDestination) {
    destinations.forEachIndexed { index, destination ->
        if (index > 0) SettingsDivider()
        SettingsLink(destination.title, destination.summary, destination.icon, destination.accent) { onOpen(destination) }
    }
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = AppColors.Divider)
}

@Composable
internal fun SettingsLink(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    SettingsRow(
        title, subtitle, icon, accent,
        modifier = Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().then(modifier).heightIn(min = 72.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).background(accent, iconTile), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color(0xFF141820), modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
        }
        trailing()
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    destination: SettingsDestination,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SettingsRow(
        title, subtitle, destination.icon, destination.accent,
        modifier = Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Switch(checked, onCheckedChange = null, colors = SwitchDefaults.colors(
            checkedThumbColor = AppColors.Background,
            checkedTrackColor = AppColors.AccentGreen,
        ))
    }
}

@Composable
private fun <T> SettingsChoices(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.selectableGroup()) {
        options.forEachIndexed { index, option ->
            if (index > 0) SettingsDivider()
            Row(
                Modifier.fillMaxWidth().selectable(selected == option, role = Role.RadioButton, onClick = { onSelect(option) })
                    .heightIn(min = 54.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label(option), style = MaterialTheme.typography.bodyLarge)
                RadioButton(selected == option, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = AppColors.AccentGreen))
            }
        }
    }
}

@Composable
private fun SettingsNote(text: String) {
    Text(text, modifier = Modifier.padding(16.dp), color = AppColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun MediaSettings() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("mms_settings", Context.MODE_PRIVATE) }
    var resolution by remember(prefs) { mutableStateOf(ImageResolution.preferred(context)) }
    SettingsGroup("Image resolution") {
        SettingsChoices(ImageResolution.entries, resolution, ImageResolution::label) { option ->
            resolution = option
            prefs.edit().putString("resolution", option.name).apply()
        }
        SettingsDivider()
        SettingsNote("Auto fits each SIM’s carrier limits. Original keeps the image file when it fits.")
    }
}

@Composable
private fun NotificationSettings() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("notification_privacy", Context.MODE_PRIVATE) }
    var showContent by remember(prefs) { mutableStateOf(!prefs.getBoolean("hide_preview", false)) }
    SettingsGroup("Message alerts") {
        SettingsToggle("Show message previews", "Include message text in notifications", SettingsDestination.Notifications, showContent) {
            showContent = it
            prefs.edit().putBoolean("hide_preview", !it).apply()
        }
        SettingsDivider()
        SettingsLink("Android notification settings", "Manage sound and lock-screen behavior",
            Icons.Default.Tune, SettingsDestination.Notifications.accent) {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
    }
}

@Composable
private fun SpamSettings(onOpenSpam: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("spam_protection", Context.MODE_PRIVATE) }
    var automatic by remember(prefs) { mutableStateOf(prefs.getBoolean("automatic", true)) }
    SettingsGroup("Filtering") {
        SettingsToggle("Automatic spam protection", "Silence suspected phishing from unknown senders", SettingsDestination.Spam, automatic) {
            automatic = it
            prefs.edit().putBoolean("automatic", it).apply()
        }
        SettingsDivider()
        SettingsLink("Spam and blocked numbers", "Review flagged messages and manage blocked senders",
            Icons.Default.Block, SettingsDestination.Spam.accent, onClick = onOpenSpam)
    }
}

@Composable
private fun RecycleSettings(onOpenRecycleBin: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(RecycleBinRepository.PREFS, Context.MODE_PRIVATE) }
    var enabled by remember(prefs) { mutableStateOf(prefs.getBoolean(RecycleBinRepository.KEY_ENABLED, true)) }
    var retention by remember(prefs) { mutableIntStateOf(prefs.getInt(RecycleBinRepository.KEY_DAYS, 30)) }
    SettingsGroup("Deleted SMS") {
        SettingsLink("Open Recycle Bin", "Restore or permanently delete saved messages", Icons.Default.DeleteOutline,
            SettingsDestination.Recycle.accent, onClick = onOpenRecycleBin)
        SettingsDivider()
        SettingsToggle("Keep deleted messages", "Save a recovery copy when deleting SMS", SettingsDestination.Recycle, enabled) {
            enabled = it
            prefs.edit().putBoolean(RecycleBinRepository.KEY_ENABLED, it).apply()
        }
    }
    if (enabled) SettingsGroup("Permanently delete after") {
        SettingsChoices(listOf(7, 15, 30), retention, { "$it days" }) { days ->
            retention = days
            prefs.edit().putInt(RecycleBinRepository.KEY_DAYS, days).apply()
        }
    }
}
