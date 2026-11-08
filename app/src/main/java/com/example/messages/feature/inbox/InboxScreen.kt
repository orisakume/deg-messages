package com.example.messages.feature.inbox

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.messages.core.design.AppColors
import com.example.messages.data.sms.SmsConversation
import java.text.DateFormat
import java.util.Date

private enum class InboxFilter(val label: String) { All("All"), Unread("Unread"), Personal("Personal") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(onOpenConversation: (Long, String) -> Unit, onCompose: () -> Unit, onOpenRecycleBin: () -> Unit, onOpenSpam: () -> Unit, viewModel: InboxViewModel = hiltViewModel()) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val selected = remember { mutableStateListOf<Long>() }
    var filter by remember { mutableStateOf(InboxFilter.All) }
    var searchOpen by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<List<Long>?>(null) }

    val visible = conversations.filter { conversation ->
        !conversation.spam && (conversation.archived == showArchived) &&
            (filter != InboxFilter.Unread || conversation.unreadCount > 0) &&
            (filter != InboxFilter.Personal || conversation.address.any(Char::isDigit)) &&
            (search.isBlank() || conversation.searchContent.contains(search, ignoreCase = true) || conversation.displayName.contains(search, true))
    }

    Scaffold(
        containerColor = AppColors.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (selected.isEmpty() && !showArchived) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallFloatingActionButton(onClick = onOpenRecycleBin, containerColor = AppColors.SurfaceElevated, contentColor = AppColors.TextPrimary) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Open Recycle Bin")
                    }
                    FloatingActionButton(onClick = onCompose, containerColor = AppColors.AccentGreen, contentColor = AppColors.Background) {
                        Icon(Icons.Default.Sms, contentDescription = "New message")
                    }
                }
            }
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                Surface(color = AppColors.Surface) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        SelectionAction(if (selected.size == visible.size) "Deselect all" else "Select all", Icons.Default.SelectAll, Modifier.weight(1f)) {
                            if (selected.size == visible.size) selected.clear() else { selected.clear(); selected.addAll(visible.map { it.threadId }) }
                        }
                        SelectionAction("Read", Icons.Default.MarkEmailRead, Modifier.weight(1f)) { viewModel.markThreadsRead(selected.toList(), true); selected.clear() }
                        SelectionAction("Unread", Icons.Default.MarkEmailUnread, Modifier.weight(1f)) { viewModel.markThreadsRead(selected.toList(), false); selected.clear() }
                        SelectionAction(if (showArchived) "Restore" else "Archive", Icons.Default.Archive, Modifier.weight(1f)) { selected.toList().forEach { viewModel.setArchived(it, !showArchived) }; selected.clear() }
                        SelectionAction("Delete", Icons.Default.DeleteOutline, Modifier.weight(1f), AppColors.Danger) { pendingDelete = selected.toList() }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TopAppBar(
                title = {
                    if (selected.isNotEmpty()) Text("${selected.size} selected", maxLines = 1, softWrap = false, style = MaterialTheme.typography.titleMedium)
                    else if (showArchived) Text("Archived")
                    else Text("Messages")
                },
                navigationIcon = {
                    if (selected.isNotEmpty()) TextButton(onClick = { selected.clear() }) { Text("Done") }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                    } else {
                        IconButton(onClick = { searchOpen = !searchOpen; search = "" }) {
                            Icon(Icons.Default.Search, contentDescription = "Search messages")
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (showArchived) "Back to inbox" else "Archived conversations") },
                                    onClick = { showArchived = !showArchived; showMenu = false; selected.clear() },
                                )
                                DropdownMenuItem(text = { Text("Spam & blocked") }, onClick = { showMenu = false; onOpenSpam() })
                                DropdownMenuItem(text = { Text("Recycle Bin") }, onClick = { showMenu = false; onOpenRecycleBin() })
                            }
                        }
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background),
            )
            if (searchOpen && selected.isEmpty()) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    singleLine = true,
                    placeholder = { Text("Search messages or people") },
                    trailingIcon = { TextButton(onClick = { search = "" }) { Text("Clear") } },
                )
            }
            if (!showArchived && selected.isEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InboxFilter.entries.forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text(option.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.AccentGreen,
                                selectedLabelColor = AppColors.Background,
                            ),
                        )
                    }
                }
            }
            if (visible.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when {
                            search.isNotBlank() -> "No matching messages"
                            showArchived -> "No archived conversations"
                            filter == InboxFilter.Unread -> "No unread conversations"
                            else -> "No messages yet"
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (filter == InboxFilter.Unread && search.isBlank()) "You’re all caught up. Read messages remain in All." else "SMS conversations from this device will appear here.",
                        color = AppColors.TextSecondary,
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(visible, key = SmsConversation::threadId) { conversation ->
                        val selectedNow = conversation.threadId in selected
                        val swipeState = rememberSwipeToDismissBoxState()
                        LaunchedEffect(swipeState.currentValue) {
                            if (!selectedNow) when (swipeState.currentValue) {
                                SwipeToDismissBoxValue.StartToEnd -> {
                                    if (conversation.unreadCount > 0) viewModel.markThreadsRead(listOf(conversation.threadId), true).join()
                                    swipeState.reset()
                                }
                                SwipeToDismissBoxValue.EndToStart -> {
                                    if (!showArchived) viewModel.setArchived(conversation.threadId, true).join()
                                }
                                SwipeToDismissBoxValue.Settled -> Unit
                            }
                        }
                        SwipeToDismissBox(
                            state = swipeState,
                            backgroundContent = {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.MarkEmailRead, contentDescription = null, tint = AppColors.AccentGreen)
                                        Text(" Read", color = AppColors.AccentGreen)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Archive ", color = AppColors.TextSecondary)
                                        Icon(Icons.Default.Archive, contentDescription = null, tint = AppColors.TextSecondary)
                                    }
                                }
                            },
                            enableDismissFromStartToEnd = !selectedNow,
                            enableDismissFromEndToStart = !selectedNow && !showArchived,
                        ) {
                            Surface(modifier = Modifier.fillMaxWidth(), color = if (selectedNow) AppColors.GreenTint else AppColors.Background) {
                                ConversationRow(
                                    conversation = conversation,
                                    selected = selectedNow,
                                    onClick = {
                                        if (selected.isNotEmpty()) {
                                            if (conversation.threadId in selected) selected.remove(conversation.threadId) else selected.add(conversation.threadId)
                                        } else onOpenConversation(conversation.threadId, conversation.address)
                                    },
                                    onLongClick = {
                                        if (conversation.threadId in selected) selected.remove(conversation.threadId) else selected.add(conversation.threadId)
                                    },
                                    onPin = { viewModel.setPinned(conversation.threadId, !conversation.pinned) },
                                    onArchive = { viewModel.setArchived(conversation.threadId, !conversation.archived) },
                                    onMute = { viewModel.setMuted(conversation.threadId, !conversation.muted) },
                                    onRead = { viewModel.markThreadsRead(listOf(conversation.threadId), conversation.unreadCount > 0) },
                                    onDelete = { pendingDelete = listOf(conversation.threadId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { ids ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete conversation${if (ids.size == 1) "" else "s"}?") },
            text = { Text(if (viewModel.recycleBinEnabled) "${ids.size} conversation${if (ids.size == 1) "" else "s"} will be moved to the Recycle Bin for recovery." else "${ids.size} conversation${if (ids.size == 1) "" else "s"} will be permanently deleted because the Recycle Bin is off.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteThreads(ids); selected.clear(); pendingDelete = null }) {
                    Text("Delete", color = AppColors.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
            containerColor = AppColors.SurfaceElevated,
        )
    }
}

@Composable
private fun SelectionAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = AppColors.TextPrimary,
    onClick: () -> Unit,
) {
    Column(modifier.clickable(onClick = onClick).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, color = tint, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun ConversationRow(
    conversation: SmsConversation,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onMute: () -> Unit,
    onRead: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(18.dp))
                .then(if (selected) Modifier else Modifier)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = if (selected) AppColors.GreenTint else AppColors.SurfaceElevated,
                border = if (selected) BorderStroke(1.dp, AppColors.AccentGreen) else null,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = AppColors.AccentGreen)
                    else ContactAvatar(conversation.contactPhotoUri, conversation.displayName)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(conversation.displayName, fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (conversation.pinned) Icon(Icons.Default.PushPin, contentDescription = "Pinned", tint = AppColors.AccentGreen, modifier = Modifier.size(16.dp))
                    Text(formatConversationTime(conversation.date), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(conversation.preview.ifBlank { "SMS message" }, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (conversation.unreadCount > 0) AppColors.TextPrimary else AppColors.TextSecondary, modifier = Modifier.weight(1f))
                    if (conversation.unreadCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = CircleShape, color = AppColors.AccentGreen) {
                            Text(conversation.unreadCount.toString(), color = AppColors.Background, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.MoreVert, contentDescription = "Conversation actions", tint = AppColors.TextSecondary) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(if (conversation.pinned) "Unpin" else "Pin") }, onClick = { onPin(); menu = false })
                            DropdownMenuItem(text = { Text(if (conversation.archived) "Restore to inbox" else "Archive") }, onClick = { onArchive(); menu = false })
                            DropdownMenuItem(text = { Text(if (conversation.muted) "Unmute" else "Mute notifications") }, onClick = { onMute(); menu = false })
                            DropdownMenuItem(text = { Text(if (conversation.unreadCount > 0) "Mark as read" else "Mark as unread") }, onClick = { onRead(); menu = false })
                            DropdownMenuItem(text = { Text("Delete", color = AppColors.Danger) }, onClick = { onDelete(); menu = false })
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = AppColors.Divider, modifier = Modifier.padding(start = 72.dp, end = 16.dp))
    }
}

@Composable
private fun ContactAvatar(photoUri: String, displayName: String) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, photoUri) {
        value = if (photoUri.isBlank()) null else withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) {
                null
            }
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "$displayName profile photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(CircleShape),
        )
    } else {
        Text(displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?", fontWeight = FontWeight.Bold)
    }
}

internal fun formatConversationTime(timestamp: Long): String {
    val date = Date(timestamp)
    val now = System.currentTimeMillis()
    return when {
        android.text.format.DateUtils.isToday(timestamp) -> DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
        now - timestamp < 7L * 24 * 60 * 60 * 1000 -> DateFormat.getDateInstance(DateFormat.SHORT).format(date)
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(date)
    }
}
