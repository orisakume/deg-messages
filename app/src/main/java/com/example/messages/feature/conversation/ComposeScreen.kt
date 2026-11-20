package com.example.messages.feature.conversation

import android.telephony.SubscriptionManager
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import com.example.messages.feature.schedule.ScheduleSmsDialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.messages.feature.inbox.ContactAvatar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(onBack: () -> Unit, onConversation: (Long, String) -> Unit, forwardedText: String = "", forwardedImage: String? = null, viewModel: ComposeViewModel = hiltViewModel()) {
    var recipient by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf(forwardedText) }
    var image by rememberSaveable { mutableStateOf(forwardedImage) }
    var scheduling by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var selectedSim by rememberSaveable(stateSaver = SimOptionSaver) { mutableStateOf<SimOption?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var contactsEnabled by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    val contactsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        contactsEnabled = granted
        if (granted) viewModel.refreshContacts()
    }

    Scaffold(
        containerColor = AppColors.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("New message") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { SimSelector(selectedSim, viewModel::subscriptions) { selectedSim = it } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background),
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        val compact = maxHeight < 420.dp
        Column(Modifier.fillMaxSize().then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it; viewModel.updateContactQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !sending,
                label = { Text("To · phone number or contact") },
                singleLine = true,
                trailingIcon = {
                    if (!contactsEnabled) TextButton(onClick = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                        Text("Enable contacts")
                    }
                },
            )
            if (recipient.isNotBlank() && contacts.isNotEmpty()) {
                LazyColumn(Modifier.then(if (compact) Modifier.heightIn(max = 144.dp) else Modifier.weight(1f, fill = false))
                    .fillMaxWidth().padding(top = 4.dp)) {
                    items(contacts, key = { it.number }) { contact ->
                        Row(Modifier.fillMaxWidth().clickable {
                            recipient = contact.number
                            viewModel.updateContactQuery("")
                            focusManager.clearFocus()
                        }.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(40.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                ContactAvatar(contact.photoUri, contact.name)
                            }
                            Column {
                                Text(contact.name, fontWeight = FontWeight.Medium)
                                Text(contact.number, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        HorizontalDivider(color = AppColors.Divider)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                modifier = Modifier.fillMaxWidth().then(if (compact) Modifier.heightIn(min = 100.dp) else Modifier.weight(1f)),
                label = { Text("Text message") },
                enabled = !sending,
                minLines = 2,
                maxLines = if (compact) 3 else Int.MAX_VALUE,
                trailingIcon = { QuickReplies(body) { body = appendQuickReply(body, it) } },
            )
            image?.let { AttachedMedia(Uri.parse(it)) { image = null } }
            Row {
                AddAttachmentButton(!sending) { image = it.toString() }
                TextButton(enabled = !sending && image == null && recipient.isNotBlank() && body.isNotBlank(),
                    onClick = { focusManager.clearFocus(); scheduling = true }) { Text("Schedule SMS") }
            }
            error?.let { Text(it, color = AppColors.Danger, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    error = null
                    focusManager.clearFocus()
                    sending = true
                    scope.launch {
                        try {
                            val threadId = viewModel.send(recipient, body, selectedSim?.subscriptionId ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID, image?.let(Uri::parse))
                            onConversation(threadId, recipient.trim())
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = failure.message ?: "Message could not be sent. Check your SIM and try again."
                        } finally { sending = false }
                    }
                },
                enabled = !sending && recipient.isNotBlank() && (body.isNotBlank() || image != null),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (image == null) "Send SMS" else "Send MMS")
            }
        }
        }
    }

    if (scheduling) ScheduleSmsDialog(recipient, body,
        selectedSim?.subscriptionId ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID,
        { scheduling = false }, {
            scheduling = false
            body = ""
            scope.launch { snackbar.showSnackbar("SMS scheduled") }
        }, subscriptions = viewModel::subscriptions)
}