package com.example.messages.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.messages.core.design.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "1.0.0"
    }

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("About DEG Messages") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("DEG Messages", style = MaterialTheme.typography.headlineMedium)
                Text("Version $versionName · Version 1 · SMS edition", color = AppColors.AccentGreen, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Simple · Private · Fast. A native Android SMS app with a fixed black, white and green interface.", color = AppColors.TextSecondary)
            }
            item {
                AboutSection("INCLUDED IN VERSION 1")
                AboutBullets(
                    "Send and receive SMS through Android's default SMS app role.",
                    "Inbox filters, search, saved contact names and photos, archive, pin, mute, read status, and multi-select.",
                    "Conversation history, message selection, copy, pin, text-size gesture, and send failure retry.",
                    "On-device OTP detection, search, code correction, and copy. OTP cards expire after 24 hours or four copies; the source SMS stays in chat.",
                    "Phone numbers in conversations are highlighted; tap to copy or open the system dialer.",
                    "New-message notifications with configurable message previews and context-aware actions.",
                    "Recycle Bin with restore and configurable retention when enabled.",
                    "Local spam folder and blocked-number rules. Blocking is app-local, not carrier-level.",
                    "Optional contacts and SIM selection. No account, ads, analytics, or cloud service.",
                )
            }
            item {
                AboutSection("NOT INCLUDED IN VERSION 1")
                AboutBullets(
                    "MMS, photo sending or receiving, and all attachment messaging. These are deferred to Version 2.",
                    "Cloud sync, online backup, or account-based restore.",
                    "Carrier-level spam blocking. Delivery reports are shown only when supported and returned by the carrier.",
                )
            }
            item {
                AboutSection("VERSION 1.0.0 RELEASE NOTES")
                AboutBullets(
                    "SMS-focused release with Android default-handler integration.",
                    "OTP and cleanup controls are grouped under Settings.",
                    "Message selection actions use named controls for easier recognition.",
                    "Inbox swipes have one consistent meaning: swipe right to mark read; swipe left to archive.",
                    "Manual Chats, OTPs, and Settings navigation; the OTP tab shows a count badge when codes are detected.",
                    "Read and archive swipes now settle consistently, with a clear empty state when no unread chats remain.",
                    "Photo and attachment controls are omitted while MMS is deferred.",
                )
            }
            item {
                AboutSection("PRIVACY AND STORAGE")
                Text(
                    "Messages are handled locally by Android and this app. No Internet permission, message upload, advertising SDK, analytics, or account is included. Android requires this app to be selected as the default SMS handler for SMS access. Contact access is optional; phone-state access is used for SIM selection. Recycle Bin copies are private to the app but are not encrypted separately, and uninstalling can remove them.",
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                Text("Version 1 is SMS-only. Use another messaging app for photo or MMS messages until Version 2.", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AboutSection(title: String) {
    Text(title, color = AppColors.AccentGreen, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun AboutBullets(vararg entries: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        entries.forEach { entry ->
            Text("•  $entry", color = AppColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
