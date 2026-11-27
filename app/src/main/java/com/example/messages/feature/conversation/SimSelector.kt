package com.example.messages.feature.conversation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import com.example.messages.data.sms.SimOption
import com.example.messages.core.design.AppColors
import kotlinx.coroutines.launch

internal val SimOptionSaver = listSaver<SimOption?, Any>(
    save = { it?.let { option -> listOf(option.subscriptionId, option.displayName, option.slotIndex) }.orEmpty() },
    restore = { SimOption(it[0] as Int, it[1] as String, it[2] as Int) },
)

@Composable
internal fun ConversationSimIndicator(selection: ConversationSimSelection, enabled: Boolean, onClick: () -> Unit) {
    val sim = selection.selected
    val needsPermission = selection.permissionGranted == false
    val label = when {
        sim != null -> sim.displayName
        needsPermission -> "Allow SIM"
        selection.permissionGranted == null -> "SIM"
        selection.rememberedId != null -> "SIM unavailable"
        selection.active.isNotEmpty() -> "Choose SIM"
        else -> "No SIM"
    }
    val action = when {
        needsPermission -> "Allow phone access"
        selection.canSwitch -> "Switch SIM"
        else -> null
    }
    val description = if (sim == null) label else "SIM ${sim.slotIndex + 1}, $label"
    val interaction = if (action != null) Modifier.clickable(enabled = enabled, onClickLabel = action, role = Role.Button, onClick = onClick) else Modifier
    Column(
        Modifier.width(66.dp).heightIn(min = 48.dp).then(interaction)
            .semantics(mergeDescendants = true) { contentDescription = description }.padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.SimCard, contentDescription = null, tint = AppColors.AccentGreen, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SimSelector(
    selected: SimOption?,
    loadSubscriptions: suspend () -> List<SimOption>,
    autoSelect: Boolean = true,
    onSelected: (SimOption) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var choices by remember { mutableStateOf<List<SimOption>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    fun load() {
        scope.launch {
            val active = loadSubscriptions()
            if (active.isEmpty()) message = "No active SIM was found. Check the device SIM settings."
            else if (active.size == 1) onSelected(active.first())
            else choices = active
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) load() else message = "SIM selection needs Phone permission. Android's default SIM will be used if one is set."
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val active = loadSubscriptions()
            val defaultId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                SubscriptionManager.getDefaultSmsSubscriptionId()
            } else {
                SubscriptionManager.INVALID_SUBSCRIPTION_ID
            }
            val current = active.firstOrNull { it.subscriptionId == defaultId } ?: active.singleOrNull()
            if (autoSelect && current != null && active.none { it.subscriptionId == selected?.subscriptionId }) onSelected(current)
        }
    }

    TextButton(onClick = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) load()
        else permissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
    }) {
        Icon(Icons.Default.SimCard, contentDescription = null)
        Text(selected?.displayName ?: "Choose SIM", modifier = Modifier.widthIn(max = 110.dp),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }

    choices?.let { active ->
        AlertDialog(
            onDismissRequest = { choices = null },
            title = { Text("Choose SIM") },
            text = {
                androidx.compose.foundation.layout.Column {
                    active.forEach { sim ->
                        DropdownMenuItem(
                            text = { Text("${sim.displayName} · SIM ${sim.slotIndex + 1}") },
                            onClick = { onSelected(sim); choices = null },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choices = null }) { Text("Cancel") } },
        )
    }
    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("SIM selection") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
}
