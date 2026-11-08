package com.example.messages.feature.conversation

import android.content.Intent
import android.net.Uri
import android.telephony.SubscriptionManager
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.messages.core.design.AppColors
import com.example.messages.data.sms.SimOption
import com.example.messages.data.sms.SmsRecord
import com.example.messages.data.sms.canReplyToSmsAddress
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(onBack: () -> Unit, viewModel: ConversationViewModel = hiltViewModel()) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val pinnedIds by viewModel.pinnedMessageIds.collectAsStateWithLifecycle()
    val conversationMetadata by viewModel.conversationMetadata.collectAsStateWithLifecycle()
    val contactDetails by viewModel.contactDetails.collectAsStateWithLifecycle()
    val savedTextScale by viewModel.textScale.collectAsStateWithLifecycle()
    var textScale by remember(viewModel.threadId) { mutableFloatStateOf(1f) }
    var scaleSaveJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(savedTextScale) { textScale = savedTextScale }
    val selected = remember { mutableStateListOf<Long>() }
    var text by remember { mutableStateOf("") }
    var selectedSim by remember { mutableStateOf<SimOption?>(null) }
    var menu by remember { mutableStateOf(false) }
    var deleteMessages by remember { mutableStateOf(false) }
    var deleteConversation by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var phoneNumberAction by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val handlePhoneNumberClick: (String) -> Unit = remember { { number -> phoneNumberAction = number } }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val title = contactDetails?.displayName?.ifBlank { viewModel.address } ?: viewModel.address
                        Text(title.ifBlank { "Conversation" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (title.isNotBlank() && title != viewModel.address) {
                            Text(viewModel.address, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                        }
                        if (selected.isNotEmpty()) Text("${selected.size} selected", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                    }
                },
                navigationIcon = { IconButton(onClick = if (selected.isNotEmpty()) ({ selected.clear() }) else onBack) { Icon(if (selected.isNotEmpty()) Icons.Default.Close else Icons.Default.ArrowBack, contentDescription = if (selected.isNotEmpty()) "Cancel selection" else "Back") } },
                actions = {
                    if (selected.isEmpty()) {
                        SimSelector(selectedSim, viewModel::subscriptions) { selectedSim = it }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Conversation options") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (conversationMetadata.pinned) "Unpin conversation" else "Pin conversation") },
                                    onClick = { scope.launch { viewModel.setPinned(!conversationMetadata.pinned); menu = false } },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (conversationMetadata.archived) "Unarchive conversation" else "Archive conversation") },
                                    onClick = {
                                        scope.launch {
                                            viewModel.setArchived(!conversationMetadata.archived)
                                            menu = false
                                            if (!conversationMetadata.archived) onBack()
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (conversationMetadata.spam) "Not spam" else "Move to spam") },
                                    onClick = {
                                        scope.launch {
                                            viewModel.setSpam(!conversationMetadata.spam)
                                            menu = false
                                            if (!conversationMetadata.spam) onBack()
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Block sender") },
                                    onClick = { scope.launch { viewModel.blockSender(); menu = false; onBack() } },
                                )
                                DropdownMenuItem(text = { Text("Delete conversation", color = AppColors.Danger) }, onClick = { menu = false; deleteConversation = true })
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background),
            )
        },
        bottomBar = if (canReplyToSmsAddress(viewModel.address)) ({
            Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                error?.let { Text(it, color = AppColors.Danger, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) }
                if (selected.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            if (selected.size == messages.size) selected.clear() else { selected.clear(); selected.addAll(messages.map(SmsRecord::id)) }
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = null); Spacer(Modifier.width(4.dp)); Text(if (selected.size == messages.size) "Deselect all" else "Select all", maxLines = 1, softWrap = false)
                        }
                        TextButton(onClick = {
                            val selectedText = messages.filter { it.id in selected }.joinToString("\n") { it.body }
                            clipboard.setText(AnnotatedString(selectedText)); selected.clear()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Copy")
                        }
                        TextButton(onClick = {
                            val pin = selected.any { it !in pinnedIds }
                            selected.forEach { viewModel.setMessagePinned(it, pin) }
                        }) {
                            Icon(Icons.Default.PushPin, contentDescription = null); Spacer(Modifier.width(4.dp)); Text(if (selected.any { it !in pinnedIds }) "Pin" else "Unpin")
                        }
                        TextButton(onClick = { deleteMessages = true }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = AppColors.Danger); Spacer(Modifier.width(4.dp)); Text("Delete", color = AppColors.Danger)
                        }
                    }
                    HorizontalDivider(color = AppColors.Divider)
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it; error = null },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Text message") },
                        minLines = 1,
                        maxLines = 4,
                    )
                    FilledIconButton(
                        onClick = {
                            val message = text
                            scope.launch {
                                try {
                                    viewModel.send(message, selectedSim?.subscriptionId ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                                    text = ""
                                    error = null
                                } catch (failure: Exception) {
                                    error = failure.message ?: "Message could not be sent. Check your SIM and try again."
                                }
                            }
                        },
                        enabled = text.isNotBlank(),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = AppColors.AccentGreen, contentColor = AppColors.Background),
                    ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send SMS") }
                }
            }
        }) else ({}),
    ) { padding ->
        if (messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    if (canReplyToSmsAddress(viewModel.address)) "Send a message to start this conversation." else "No messages in this conversation.",
                    color = AppColors.TextSecondary,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding)
                    .pointerInput(viewModel.threadId) {
                        detectTransformGestures { _, _, zoom, _ ->
                            if (zoom != 1f) {
                                textScale = (textScale * zoom).coerceIn(0.8f, 1.6f)
                                scaleSaveJob?.cancel()
                                scaleSaveJob = scope.launch { delay(180); viewModel.setTextScale(textScale) }
                            }
                        }
                    }
                    .pointerInput(viewModel.threadId) {
                        detectTapGestures(onDoubleTap = {
                            textScale = 1f; scaleSaveJob?.cancel(); viewModel.setTextScale(1f)
                        })
                    },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                    if (index == 0 || !sameDay(messages[index - 1].date, message.date)) DateSeparator(message.date)
                    MessageBubble(
                        message = message,
                        pinned = message.id in pinnedIds,
                        textScale = textScale,
                        selected = message.id in selected,
                        onClick = {
                            if (selected.isNotEmpty()) {
                                if (message.id in selected) selected.remove(message.id) else selected.add(message.id)
                            }
                        },
                        onLongClick = { if (message.id in selected) selected.remove(message.id) else selected.add(message.id) },
                        onRetry = {
                            scope.launch {
                                try { viewModel.retry(message.id); error = null }
                                catch (failure: Exception) { error = failure.message ?: "Retry failed. Check your SIM and try again." }
                            }
                        },
                        onPhoneNumberClick = handlePhoneNumberClick,
                    )
                }
            }
        }
    }

    if (deleteMessages) AlertDialog(
        onDismissRequest = { deleteMessages = false },
        title = { Text("Delete messages?") },
        text = { Text(if (viewModel.recycleBinEnabled) "These messages will be moved to the Recycle Bin for recovery." else "These messages will be permanently deleted because the Recycle Bin is off.") },
        confirmButton = { TextButton(onClick = { viewModel.deleteMessages(selected.toList()); selected.clear(); deleteMessages = false }) { Text("Delete", color = AppColors.Danger) } },
        dismissButton = { TextButton(onClick = { deleteMessages = false }) { Text("Cancel") } },
        containerColor = AppColors.SurfaceElevated,
    )
    if (deleteConversation) AlertDialog(
        onDismissRequest = { deleteConversation = false },
        title = { Text("Delete conversation?") },
        text = { Text(if (viewModel.recycleBinEnabled) "This conversation will be moved to the Recycle Bin for recovery." else "This conversation will be permanently deleted because the Recycle Bin is off.") },
        confirmButton = { TextButton(onClick = { scope.launch { viewModel.deleteConversation(); deleteConversation = false; onBack() } }) { Text("Delete", color = AppColors.Danger) } },
        dismissButton = { TextButton(onClick = { deleteConversation = false }) { Text("Cancel") } },
        containerColor = AppColors.SurfaceElevated,
    )
    phoneNumberAction?.let { number ->
        AlertDialog(
            onDismissRequest = { phoneNumberAction = null },
            title = { Text("Phone number") },
            text = { Text(number, style = MaterialTheme.typography.titleMedium) },
            confirmButton = {
                TextButton(onClick = {
                    val dialable = number.filter { it.isDigit() || it == '+' }
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", dialable, null)))
                    phoneNumberAction = null
                }) { Text("Dial") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(number)); phoneNumberAction = null }) { Text("Copy") }
                    TextButton(onClick = { phoneNumberAction = null }) { Text("Close") }
                }
            },
            containerColor = AppColors.SurfaceElevated,
        )
    }
}

@Composable
private fun DateSeparator(timestamp: Long) {
    val formatter = remember { SimpleDateFormat("EEE, d MMM", Locale.getDefault()) }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
        Surface(color = AppColors.SurfaceElevated, shape = RoundedCornerShape(50)) {
            Text(formatter.format(Date(timestamp)), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun MessageBubble(
    message: SmsRecord,
    pinned: Boolean,
    textScale: Float,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRetry: () -> Unit,
    onPhoneNumberClick: (String) -> Unit,
) {
    val outgoing = message.type != android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start) {
            Surface(
                color = if (selected) AppColors.GreenTint else if (outgoing) AppColors.AccentGreenDark else AppColors.SurfaceElevated,
                shape = RoundedCornerShape(20.dp),
                border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, AppColors.AccentGreen) else null,
                modifier = Modifier.widthIn(max = 320.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    if (pinned) Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PushPin, contentDescription = "Pinned message", tint = AppColors.AccentGreen, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pinned", style = MaterialTheme.typography.labelSmall, color = AppColors.AccentGreen)
                    }
                    val linkedBody = remember(message.body, onPhoneNumberClick) {
                        buildAnnotatedString {
                            var cursor = 0
                            PHONE_NUMBER_CANDIDATE.findAll(message.body).forEach { match ->
                                val digits = match.value.filter(Char::isDigit)
                                if (digits.length in 7..15) {
                                    append(message.body.substring(cursor, match.range.first))
                                    val candidate = match.value.trim().trimEnd('-', '.', ' ')
                                    withLink(
                                        LinkAnnotation.Clickable(
                                            tag = digits,
                                            styles = TextLinkStyles(SpanStyle(color = AppColors.AccentGreen, textDecoration = TextDecoration.Underline)),
                                            linkInteractionListener = { onPhoneNumberClick(candidate) },
                                        ),
                                    ) { append(candidate) }
                                    cursor = match.range.last + 1
                                }
                            }
                            append(message.body.substring(cursor))
                        }
                    }
                    Text(linkedBody, color = AppColors.TextPrimary, style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize * textScale))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.date)), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                if (message.type == android.provider.Telephony.Sms.MESSAGE_TYPE_FAILED) {
                    Text("Failed", color = AppColors.Danger, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Retry", color = AppColors.AccentGreen) }
                } else if (outgoing && message.type == android.provider.Telephony.Sms.MESSAGE_TYPE_OUTBOX) {
                    Text("Sending", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                } else if (outgoing && message.status == android.provider.Telephony.Sms.STATUS_COMPLETE) {
                    Text("Delivered", color = AppColors.AccentGreen, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private val PHONE_NUMBER_CANDIDATE = Regex("(?<![A-Za-z0-9])\\+?(?:\\d[\\s().-]*){7,15}(?![A-Za-z0-9])")

private fun sameDay(first: Long, second: Long): Boolean =
    android.text.format.DateUtils.isToday(first) == android.text.format.DateUtils.isToday(second) &&
        java.text.SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date(first)) == java.text.SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date(second))
