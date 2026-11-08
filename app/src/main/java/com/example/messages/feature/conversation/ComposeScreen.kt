package com.example.messages.feature.conversation

import android.telephony.SubscriptionManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.messages.core.design.AppColors
import com.example.messages.data.sms.SimOption
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(onBack: () -> Unit, onConversation: (Long, String) -> Unit, viewModel: ComposeViewModel = hiltViewModel()) {
    var recipient by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var selectedSim by remember { mutableStateOf<SimOption?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var contactsEnabled by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    val contactsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        contactsEnabled = granted
        if (granted) viewModel.refreshContacts()
    }

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("New message") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                actions = { SimSelector(selectedSim, viewModel::subscriptions) { selectedSim = it } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it; viewModel.updateContactQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("To · phone number or contact") },
                singleLine = true,
                trailingIcon = {
                    if (!contactsEnabled) TextButton(onClick = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                        Text("Enable contacts")
                    }
                },
            )
            if (recipient.isNotBlank() && contacts.isNotEmpty()) {
                LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth().padding(top = 4.dp)) {
                    items(contacts, key = { it.number }) { contact ->
                        Column(Modifier.fillMaxWidth().clickable {
                            recipient = contact.number
                            viewModel.updateContactQuery("")
                            focusManager.clearFocus()
                        }.padding(12.dp)) {
                            Text(contact.name, fontWeight = FontWeight.Medium)
                            Text(contact.number, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider(color = AppColors.Divider)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
                label = { Text("Text message") },
                minLines = 4,
            )
            error?.let { Text(it, color = AppColors.Danger, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    error = null
                    focusManager.clearFocus()
                    scope.launch {
                        try {
                            val threadId = viewModel.send(recipient, body, selectedSim?.subscriptionId ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                            onConversation(threadId, recipient.trim())
                        } catch (failure: Exception) {
                            error = failure.message ?: "Message could not be sent. Check your SIM and try again."
                        }
                    }
                },
                enabled = recipient.isNotBlank() && body.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Send SMS")
            }
        }
    }
}
