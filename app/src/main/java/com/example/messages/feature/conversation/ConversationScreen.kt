package com.example.messages.feature.conversation

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.os.PersistableBundle
import android.provider.Telephony
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import com.example.messages.feature.inbox.ContactAvatar
import android.net.Uri
import android.telephony.SubscriptionManager
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.PlayArrow
import com.example.messages.core.design.ActionSheet
import com.example.messages.core.design.ActionDropdown
import com.example.messages.data.sms.HighlightKind
import com.example.messages.data.sms.MessageHighlight
import com.example.messages.core.design.ActionTone
import com.example.messages.core.design.SheetAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.messages.feature.schedule.ScheduleSmsDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.messages.core.design.AppColors
import com.example.messages.core.design.OverflowIcon
import com.example.messages.core.database.OtpRecordEntity
import com.example.messages.data.sms.SimOption
import com.example.messages.data.sms.SmsRecord
import com.example.messages.data.sms.canReplyToSmsAddress
import com.example.messages.data.sms.smsSenderBrand
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    onBack: () -> Unit,
    onForward: (String, String?) -> Unit,
    onMessagesDeleted: (Long, Long?) -> Unit,
    viewModel: ConversationViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val messages = history.messages
    val pinnedIds by viewModel.pinnedMessageIds.collectAsStateWithLifecycle()
    val conversationMetadata by viewModel.conversationMetadata.collectAsStateWithLifecycle()
    val contactDetails by viewModel.contactDetails.collectAsStateWithLifecycle()
    val senderBrand = remember(viewModel.address) { smsSenderBrand(viewModel.address) }
    val savedTextScale by viewModel.textScale.collectAsStateWithLifecycle()
    var textScale by remember(viewModel.threadId) { mutableFloatStateOf(1f) }
    var scaleSaveJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(savedTextScale) { textScale = savedTextScale }
    val selected = remember { mutableStateListOf<Long>() }
    var text by rememberSaveable { mutableStateOf("") }
    var image by rememberSaveable { mutableStateOf<String?>(null) }
    var scheduleDraft by remember { mutableStateOf<String?>(null) }
    var scheduleComposer by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<SmsRecord?>(null) }
    var textToSelect by remember { mutableStateOf<String?>(null) }
    var selectionMenu by remember { mutableStateOf(false) }
    var saveSource by rememberSaveable { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<SmsRecord?>(null) }
    val selectedMessages = if (selected.isEmpty()) emptyList() else messages.filter { it.id in selected }
    var selectedSim by rememberSaveable(stateSaver = SimOptionSaver) { mutableStateOf<SimOption?>(null) }
    var menu by remember { mutableStateOf(false) }
    var deleteTargets by remember { mutableStateOf<List<SmsRecord>?>(null) }
    var deletingMessages by remember { mutableStateOf(false) }
    var deleteConversation by remember { mutableStateOf(false) }
    var expiredOtpsToMove by remember { mutableStateOf<List<OtpRecordEntity>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var highlightAction by remember { mutableStateOf<MessageHighlight?>(null) }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    fun shareHighlight(highlight: MessageHighlight) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, highlight.value)
        context.startActivity(Intent.createChooser(send, null))
    }
    fun copyHighlight(highlight: MessageHighlight) {
        val clip = ClipData.newPlainText(highlight.kind.label, highlight.value)
        if (highlight.kind == HighlightKind.Otp) {
            clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        }
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar("${highlight.kind.label} copied", actionLabel = "Share") == SnackbarResult.ActionPerformed) {
                shareHighlight(highlight)
            }
        }
    }
    BackHandler(enabled = selected.isNotEmpty() && !selectionMenu) { selected.clear() }
    BackHandler(enabled = deletingMessages) { }
    val saveAttachment = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { destination ->
        val source = saveSource
        saveSource = null
        if (destination != null && source != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    checkNotNull(resolver.openInputStream(Uri.parse(source))).use { input ->
                        checkNotNull(resolver.openOutputStream(destination)).use { output -> input.copyTo(output) }
                    }
                }
                snackbar.showSnackbar("Attachment saved")
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { snackbar.showSnackbar("Could not save the attachment") }
        }
    }

    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty() && !listState.canScrollBackward) listState.scrollToItem(messages.lastIndex)
        else if (messages.isNotEmpty() && listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index == messages.lastIndex - 1) listState.animateScrollToItem(messages.lastIndex)
    }

    val targets = actionMessage?.let(::listOf) ?: selectedMessages
    val messageActions = if (targets.isNotEmpty()) {
        val single = targets.singleOrNull()
        val body = targets.map { it.body }.filter(String::isNotBlank).joinToString("\n")
        val media = single?.attachmentUri
        val canTransfer = media != null || (body.isNotBlank() && targets.none { it.attachmentUri != null })
        buildList {
            if (body.isNotBlank()) add(SheetAction("Copy", Icons.Default.ContentCopy, tone = ActionTone.Blue) {
                clipboard.setText(AnnotatedString(body))
            })
            if (canTransfer) {
                add(SheetAction("Forward", Icons.AutoMirrored.Filled.Forward, tone = ActionTone.Teal) { onForward(body, media); selected.clear() })
                add(SheetAction("Share", Icons.Default.Share, tone = ActionTone.Lilac) {
                    scope.launch {
                        try {
                            val share = if (media == null) Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body)
                            else mediaIntent(context, Uri.parse(media), single?.attachmentMime ?: "application/octet-stream", body)
                            context.startActivity(Intent.createChooser(share, null))
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (_: Exception) { snackbar.showSnackbar("Could not share this message") }
                    }
                })
            }
            add(SheetAction(if (single != null) "Delete message" else "Delete messages", Icons.Default.DeleteOutline, destructive = true) { deleteTargets = targets })
            actionMessage?.let { message ->
                add(SheetAction("Select", Icons.Default.SelectAll, tone = ActionTone.Blue) { selected.add(message.id) })
            }
            if (single != null && body.isNotBlank()) {
                add(SheetAction("Select text", Icons.Default.TextFields, tone = ActionTone.Teal) { textToSelect = body })
            }
            if (selectionMenu) add(SheetAction(if (selected.size == messages.size) "Deselect all" else "Select all", Icons.Default.SelectAll, tone = ActionTone.Blue) {
                val allSelected = selected.size == messages.size
                selected.clear()
                if (!allSelected) selected.addAll(messages.map(SmsRecord::id))
            })
            if (single != null) {
                add(SheetAction("Details", Icons.Default.Info, tone = ActionTone.Blue) { details = single })
                if (media == null && body.isNotBlank() && canReplyToSmsAddress(viewModel.address)) {
                    add(SheetAction("Schedule", Icons.Default.Schedule, tone = ActionTone.Amber) {
                        scheduleComposer = false
                        scheduleDraft = body
                    })
                }
                if (media != null || body.isNotBlank()) add(SheetAction("Save", Icons.Default.BookmarkBorder, tone = ActionTone.Sage) {
                    if (media != null) {
                        val extension = when (single.attachmentMime) {
                            "image/jpeg" -> "jpg"
                            "video/3gpp" -> "3gp"
                            else -> single.attachmentMime.orEmpty().substringAfter('/').filter(Char::isLetterOrDigit).ifBlank { "dat" }
                        }
                        saveSource = media
                        saveAttachment.launch("DEG-${single.date}.$extension")
                    } else {
                        val result = saveMessageAsQuickReply(context, body)
                        scope.launch { snackbar.showSnackbar(result) }
                    }
                })
            }
            val pin = targets.any { it.id !in pinnedIds }
            add(SheetAction(if (pin) "Pin" else "Unpin", Icons.Default.PushPin, tone = ActionTone.Amber) {
                targets.forEach { viewModel.setMessagePinned(it.id, pin) }
            })
        }
    } else emptyList()

    Scaffold(
        containerColor = AppColors.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val contact = contactDetails
                        val title = contact?.displayName.orEmpty().ifBlank { senderBrand?.name ?: viewModel.address }
                        if (senderBrand != null || contact?.photoUri?.isNotBlank() == true) {
                            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                ContactAvatar(contact?.photoUri.orEmpty(), title, senderBrand)
                            }
                        }
                        Column {
                            Text(title.ifBlank { "Conversation" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (title.isNotBlank() && title != viewModel.address) {
                                Text(viewModel.address, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                            }
                            if (selected.isNotEmpty()) Text("${selected.size} selected", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                        }
                    }
                },
                navigationIcon = { IconButton(enabled = !deletingMessages, onClick = if (selected.isNotEmpty()) ({ selected.clear() }) else onBack) { Icon(if (selected.isNotEmpty()) Icons.Default.Close else Icons.Default.ArrowBack, contentDescription = if (selected.isNotEmpty()) "Cancel selection" else "Back") } },
                actions = {
                    if (selected.isEmpty()) {
                        if (canReplyToSmsAddress(viewModel.address)) {
                            SimSelector(selectedSim, viewModel::subscriptions) { selectedSim = it }
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { OverflowIcon("Conversation options") }
                            if (menu) ActionSheet("Conversation actions", buildList {
                                if (canReplyToSmsAddress(viewModel.address) && text.isNotBlank() && image == null && !sending) {
                                    add(SheetAction("Schedule", Icons.Default.Schedule, tone = ActionTone.Amber) {
                                        scheduleComposer = true
                                        scheduleDraft = text
                                    })
                                }
                                add(SheetAction(if (conversationMetadata.pinned) "Unpin" else "Pin", Icons.Default.PushPin, tone = ActionTone.Amber) {
                                    scope.launch { viewModel.setPinned(!conversationMetadata.pinned) }
                                })
                                add(SheetAction(if (conversationMetadata.archived) "Unarchive" else "Archive", Icons.Default.Archive, tone = ActionTone.Teal) {
                                    scope.launch {
                                        viewModel.setArchived(!conversationMetadata.archived)
                                        if (!conversationMetadata.archived) onBack()
                                    }
                                })
                                add(SheetAction(if (conversationMetadata.spam) "Not spam" else "Spam", Icons.Default.Report, tone = ActionTone.Rose) {
                                    scope.launch {
                                        viewModel.setSpam(!conversationMetadata.spam)
                                        if (!conversationMetadata.spam) onBack()
                                    }
                                })
                                add(SheetAction("Block", Icons.Default.Block, tone = ActionTone.Rose) { scope.launch { viewModel.blockSender(); onBack() } })
                                if (viewModel.recycleBinEnabled) {
                                    add(SheetAction("Move expired OTPs", Icons.Default.TimerOff, tone = ActionTone.Lilac) {
                                        scope.launch {
                                            try {
                                                val expired = viewModel.expiredOtps()
                                                if (expired.isEmpty()) snackbar.showSnackbar("No expired OTP messages found in this chat.")
                                                else expiredOtpsToMove = expired
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (_: Exception) {
                                                snackbar.showSnackbar("Couldn’t check this chat for expired OTPs.")
                                            }
                                        }
                                    })
                                }
                                add(SheetAction("Delete chat", Icons.Default.DeleteOutline, destructive = true) { deleteConversation = true })
                            }) { menu = false }
                        }
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background),
            )
        },
        bottomBar = if (canReplyToSmsAddress(viewModel.address) || selected.isNotEmpty()) ({
            Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                error?.let { Text(it, color = AppColors.Danger, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) }
                if (selected.isNotEmpty()) {
                    Box(Modifier.align(Alignment.CenterHorizontally)) {
                        Button(onClick = { selectionMenu = true }) { Text("Message actions") }
                        if (selectionMenu) ActionDropdown(messageActions) { selectionMenu = false }
                    }
                }
                if (canReplyToSmsAddress(viewModel.address) && selected.isEmpty()) image?.let {
                    AttachedMedia(android.net.Uri.parse(it)) { image = null }
                }
                if (canReplyToSmsAddress(viewModel.address) && selected.isEmpty()) Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AddAttachmentButton(!sending) { image = it.toString() }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it; error = null },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Text message") },
                        minLines = 1,
                        maxLines = 4,
                        enabled = !sending,
                        trailingIcon = { QuickReplies(text) { text = appendQuickReply(text, it) } },
                    )
                    FilledIconButton(
                        onClick = {
                            val message = text
                            sending = true
                            scope.launch {
                                try {
                                    viewModel.send(message, selectedSim?.subscriptionId ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                                        image?.let(android.net.Uri::parse))
                                    text = ""
                                    image = null
                                    error = null
                                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    error = failure.message ?: "Message could not be sent. Check your SIM and try again."
                                } finally { sending = false }
                            }
                        },
                        enabled = !sending && (text.isNotBlank() || image != null),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = AppColors.AccentGreen, contentColor = AppColors.Background),
                    ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = if (image == null) "Send SMS" else "Send MMS") }
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
                if (history.hasOlder) item(key = "older") {
                    TextButton(onClick = viewModel::loadOlderMessages) { Text("Load older messages") }
                }
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
                        onLongClick = {
                            if (selected.isEmpty()) actionMessage = message
                            else if (message.id in selected) selected.remove(message.id) else selected.add(message.id)
                        },
                        onPlay = { uri, mime ->
                            scope.launch {
                                try { context.startActivity(mediaIntent(context, uri, mime, "", view = true)) }
                                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                catch (_: Exception) { snackbar.showSnackbar("Could not open this video") }
                            }
                        },
                        onRetry = {
                            scope.launch {
                                try { viewModel.retry(message); error = null }
                                catch (failure: Exception) { error = failure.message ?: "Retry failed. Check your SIM and try again." }
                            }
                        },
                        selectionActive = selected.isNotEmpty(),
                        onHighlightClick = { highlightAction = it },
                        onHighlightCopy = ::copyHighlight,
                    )
                }
            }
        }
    }


    if (actionMessage != null && messageActions.isNotEmpty()) {
        ActionSheet("Message actions", messageActions) { actionMessage = null }
    }

    textToSelect?.let { body ->
        AlertDialog(
            onDismissRequest = { textToSelect = null },
            title = { Text("Select text") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Press and hold a word, then drag the handles to adjust your selection.")
                    SelectionContainer {
                        Text(
                            body,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppColors.TextPrimary,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { textToSelect = null }) { Text("Done") } },
            containerColor = AppColors.SurfaceElevated,
        )
    }

    scheduleDraft?.let { draft -> ScheduleSmsDialog(viewModel.address, draft,
        selectedSim?.subscriptionId ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID,
        { scheduleDraft = null }, {
            scheduleDraft = null
            if (scheduleComposer) text = ""
            scope.launch { snackbar.showSnackbar("SMS scheduled") }
        }, subscriptions = viewModel::subscriptions) }

    details?.let { message ->
        val simLabel by produceState<String?>(null, message.subscriptionId) {
            value = viewModel.subscriptions().firstOrNull { it.subscriptionId == message.subscriptionId }
                ?.let { "SIM ${it.slotIndex + 1} · ${it.displayName}" }
        }
        AlertDialog(onDismissRequest = { details = null }, title = { Text("Message details") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (message.type == Telephony.Sms.MESSAGE_TYPE_INBOX) "From ${message.address}" else "To ${message.address}")
                Text(if (message.attachmentUri == null) "SMS" else "MMS")
                Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(message.date)))
                Text(simLabel ?: if (message.subscriptionId >= 0) "SIM subscription ${message.subscriptionId}" else "SIM unavailable")
                message.sendError?.let { Text(it, color = AppColors.Danger) }
                Text(when (message.type) {
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> "Received"
                    Telephony.Sms.MESSAGE_TYPE_FAILED -> "Failed"
                    Telephony.Sms.MESSAGE_TYPE_SENT -> if (message.status == Telephony.Sms.STATUS_COMPLETE) "Delivered" else "Sent"
                    else -> "Pending"
                })
            }
        }, confirmButton = { TextButton(onClick = { details = null }) { Text("Close") } })
    }

    deleteTargets?.let { targets -> AlertDialog(
        onDismissRequest = { deleteTargets = null },
        title = { Text(if (targets.size == 1) "Delete this message?" else "Delete ${targets.size} messages?") },
        text = { Text(when {
            targets.size == 1 && targets[0].attachmentUri == null && viewModel.recycleBinEnabled -> "Only the selected SMS will move to the Recycle Bin."
            targets.size == 1 -> "Only the selected message will be permanently deleted."
            !viewModel.recycleBinEnabled -> "These messages will be permanently deleted."
            targets.all { it.attachmentUri != null } -> "These MMS messages will be permanently deleted."
            targets.any { it.attachmentUri != null } -> "SMS goes to the Recycle Bin. MMS attachments will be permanently deleted."
            else -> "These messages will be moved to the Recycle Bin for recovery."
        }) },
        confirmButton = {
            TextButton(onClick = {
                deleteTargets = null
                selected.clear()
                deletingMessages = true
                scope.launch {
                    val feedback = try {
                        val deleted = viewModel.deleteMessages(targets.map(SmsRecord::id))
                        if (deleted > 0) onMessagesDeleted(viewModel.threadId, viewModel.latestMessageDate())
                        when {
                            deleted == 0 -> "Couldn’t delete the selected messages."
                            targets.size == 1 -> "Message deleted"
                            deleted != targets.size -> "Deleted $deleted of ${targets.size} selected messages."
                            else -> "$deleted messages deleted"
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        "Couldn’t finish deleting the selected messages."
                    } finally {
                        deletingMessages = false
                    }
                    snackbar.showSnackbar(feedback)
                }
            }) { Text("Delete", color = AppColors.Danger) }
        },
        dismissButton = { TextButton(onClick = { deleteTargets = null }) { Text("Cancel") } },
        containerColor = AppColors.SurfaceElevated,
    )
    }
    expiredOtpsToMove?.let { messages ->
        AlertDialog(
            onDismissRequest = { expiredOtpsToMove = null },
            title = { Text("Move expired OTPs?") },
            text = { Text("Move ${messages.size} detected OTP ${if (messages.size == 1) "message" else "messages"} older than 24 hours from this chat to the Recycle Bin.") },
            confirmButton = {
                TextButton(onClick = {
                    expiredOtpsToMove = null
                    scope.launch {
                        try {
                            val moved = viewModel.moveExpiredOtps(messages)
                            snackbar.showSnackbar("Moved $moved of ${messages.size} OTP messages to the Recycle Bin.")
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            snackbar.showSnackbar("Couldn’t finish moving the expired OTP messages.")
                        }
                    }
                }) { Text("Move") }
            },
            dismissButton = { TextButton(onClick = { expiredOtpsToMove = null }) { Text("Cancel") } },
            containerColor = AppColors.SurfaceElevated,
        )
    }
    if (deleteConversation) AlertDialog(
        onDismissRequest = { deleteConversation = false },
        title = { Text("Delete conversation?") },
        text = { Text(if (viewModel.recycleBinEnabled) "SMS goes to the Recycle Bin. Any MMS messages will be permanently deleted." else "This conversation will be permanently deleted because the Recycle Bin is off.") },
        confirmButton = { TextButton(onClick = { scope.launch { viewModel.deleteConversation(); deleteConversation = false; onBack() } }) { Text("Delete", color = AppColors.Danger) } },
        dismissButton = { TextButton(onClick = { deleteConversation = false }) { Text("Cancel") } },
        containerColor = AppColors.SurfaceElevated,
    )
    highlightAction?.let { highlight ->
        val link = when {
            highlight.value.startsWith("www.", ignoreCase = true) -> Uri.parse("https://" + highlight.value)
            else -> Uri.parse(highlight.value)
        }.takeIf { it.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") && !it.host.isNullOrBlank() }
        val target = when (highlight.kind) {
            HighlightKind.Phone -> Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", highlight.value.filter { it.isDigit() || it == '+' }, null))
            HighlightKind.Link -> link?.let { Intent(Intent.ACTION_VIEW, it) }
            HighlightKind.Email -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", highlight.value, null))
            HighlightKind.Otp -> null
        }
        AlertDialog(
            onDismissRequest = { highlightAction = null },
            title = { Text(highlight.kind.label) },
            text = { Text(highlight.value, style = MaterialTheme.typography.titleMedium) },
            confirmButton = {
                TextButton(onClick = { highlightAction = null; copyHighlight(highlight) }) { Text("Copy") }
            },
            dismissButton = {
                Row {
                    if (target != null) TextButton(onClick = {
                        highlightAction = null
                        try { context.startActivity(target) } catch (_: android.content.ActivityNotFoundException) {
                            scope.launch { snackbar.showSnackbar("No app can open this ${highlight.kind.label.lowercase(Locale.ROOT)}") }
                        }
                    }) {
                        Text(when (highlight.kind) {
                            HighlightKind.Phone -> "Dial"
                            HighlightKind.Email -> "Email"
                            else -> "Open link"
                        })
                    }
                    TextButton(onClick = { highlightAction = null; shareHighlight(highlight) }) { Text("Share") }
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
    onPlay: (Uri, String) -> Unit,
    selectionActive: Boolean,
    onHighlightClick: (MessageHighlight) -> Unit,
    onHighlightCopy: (MessageHighlight) -> Unit,
) {
    val outgoing = message.type != android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX
    val bubbleColor by animateColorAsState(
        if (selected) AppColors.GreenTint else if (outgoing) AppColors.AccentGreenDark else AppColors.SurfaceElevated,
        animationSpec = tween(130), label = "Message selection",
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start) {
            Surface(
                color = bubbleColor,
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
                    message.attachmentUri?.let {
                        val uri = Uri.parse(it)
                        val mime = message.attachmentMime.orEmpty()
                        val video = mime.startsWith("video/")
                        MessageImage(uri, Modifier.widthIn(max = 250.dp).heightIn(max = 240.dp),
                            if (video) "Video message" else "Picture message", video = video)
                        if (video) TextButton(onClick = { onPlay(uri, mime) }) {
                            Icon(Icons.Default.PlayArrow, null)
                            Text("Play video")
                        }
                    }
                    if (message.body.isNotBlank()) HighlightedMessageText(
                        body = message.body,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize * textScale),
                        selectionActive = selectionActive,
                        onMessageClick = onClick,
                        onMessageLongClick = onLongClick,
                        onHighlightClick = onHighlightClick,
                        onHighlightCopy = onHighlightCopy,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.date)), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                if (message.type == android.provider.Telephony.Sms.MESSAGE_TYPE_FAILED) {
                    Text(message.sendError ?: "Failed", modifier = Modifier.weight(1f, fill = false), color = AppColors.Danger, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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

private fun sameDay(first: Long, second: Long): Boolean =
    android.text.format.DateUtils.isToday(first) == android.text.format.DateUtils.isToday(second) &&
        java.text.SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date(first)) == java.text.SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date(second))
