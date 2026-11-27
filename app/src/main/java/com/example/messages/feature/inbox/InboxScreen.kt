package com.example.messages.feature.inbox

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import com.example.messages.data.sms.MessageSearch
import com.example.messages.data.sms.SearchField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ScrollState
import com.example.messages.core.design.ActionSheet
import com.example.messages.core.design.ActionTone
import com.example.messages.core.design.OverflowIcon
import com.example.messages.core.design.SheetAction
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import android.net.Uri
import android.database.ContentObserver
import android.os.Handler
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.messages.core.design.AppColors
import com.example.messages.data.sms.SmsConversation
import com.example.messages.data.sms.ConversationMapper
import com.example.messages.data.sms.SmsSenderBrand
import com.example.messages.data.sms.canReplyToSmsAddress
import com.example.messages.data.sms.smsSenderBrand
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private enum class InboxFilter(val label: String, val slot: Int? = null) {
    All("All"), Unread("Unread"), Sim1("SIM 1", 0), Sim2("SIM 2", 1), Personal("Personal"),
    Transactions("Transactions"), Promotions("Promotions"), OTP("OTP"), Spam("Spam")
}

private val searchPhoneNumber = Regex("""\+?[\p{Nd}().\p{Zs}\p{Pd}]+""")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InboxScreen(
    onOpenConversation: (Long, String) -> Unit,
    onCompose: () -> Unit,
    onComposeToNumber: (String) -> Unit,
    onOpenRecycleBin: () -> Unit,
    onOpenSpam: () -> Unit,
    actionOrder: List<ConversationAction>,
    showCategories: Boolean = true,
    bottomBarSpace: Dp = 0.dp,
    editedThread: Pair<Long, Long>? = null,
    onEditedThreadShown: () -> Unit = {},
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val loadedConversations by viewModel.conversations.collectAsStateWithLifecycle()
    val conversations = loadedConversations.orEmpty()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshSubscriptions() }
    val selected = remember { mutableStateListOf<Long>() }
    var selectedFilter by rememberSaveable { mutableStateOf(InboxFilter.All) }
    val filter = if (showCategories) selectedFilter else InboxFilter.All
    val filteredSubscriptionId = filter.slot?.let { slot -> subscriptions.firstOrNull { it.slotIndex == slot }?.subscriptionId }
    val categoryScroll = rememberScrollState()
    LaunchedEffect(showCategories, filter) {
        if (!showCategories) selectedFilter = InboxFilter.All
        if (filter == InboxFilter.All) categoryScroll.scrollTo(0)
    }
    var searchOpen by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(MessageSearch()) }
    var showAdvanced by remember { mutableStateOf(false) }
    val matches by viewModel.matches.collectAsStateWithLifecycle()
    val criteria = advanced.copy(text = search.trim())
    val context = LocalContext.current
    val phoneCountry = remember(context) {
        context.getSystemService(TelephonyManager::class.java)?.simCountryIso.orEmpty()
            .ifBlank { Locale.getDefault().country }
            .uppercase(Locale.ROOT)
    }
    val newRecipient = criteria.text.takeIf {
        searchOpen && searchPhoneNumber.matches(it) && it.count(Char::isDigit) in 7..15
    }?.let(PhoneNumberUtils::normalizeNumber)?.takeIf(::canReplyToSmsAddress)
    LaunchedEffect(criteria, searchOpen, filteredSubscriptionId) {
        viewModel.search(if (searchOpen) criteria else MessageSearch(), filteredSubscriptionId)
    }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var selectionMenu by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<List<Long>?>(null) }

    fun closeSearch() {
        searchOpen = false
        search = ""
        advanced = MessageSearch()
    }

    BackHandler(enabled = selected.isNotEmpty() || searchOpen || showArchived || filter != InboxFilter.All) {
        when {
            selected.isNotEmpty() -> selected.clear()
            searchOpen -> closeSearch()
            showArchived -> showArchived = false
            else -> selectedFilter = InboxFilter.All
        }
    }

    val advancedSpam = searchOpen && criteria.spamOnly
    val filterBySearch = searchOpen && criteria.hasMessageFilters
    val visible = remember(conversations, filter, filteredSubscriptionId, advancedSpam, showArchived, filterBySearch, matches) {
        val matching = conversations.filter { conversation ->
            (conversation.spam == (advancedSpam || filter == InboxFilter.Spam)) &&
                (advancedSpam || filter == InboxFilter.Spam || conversation.archived == showArchived) &&
                (filter != InboxFilter.Unread || conversation.unreadCount > 0) &&
                (advancedSpam || filter.slot != null || filter == InboxFilter.All || filter == InboxFilter.Unread ||
                    filter == InboxFilter.Spam || conversation.category.name == filter.name) &&
                (!filterBySearch || matches?.contains(conversation.threadId) == true)
        }
        when {
            filter.slot == null -> matching
            filteredSubscriptionId == null -> emptyList()
            else -> matching.mapNotNull { it.receivedOn(filteredSubscriptionId) }.sortedWith(ConversationMapper.inboxOrder)
        }
    }
    val canComposeToNumber = remember(newRecipient, visible, phoneCountry) {
        if (newRecipient == null) return@remember false
        val internationalNumber = PhoneNumberUtils.formatNumberToE164(newRecipient, phoneCountry)
        visible.none { conversation ->
            val address = conversation.address.trim()
            searchPhoneNumber.matches(address) &&
                (PhoneNumberUtils.normalizeNumber(address) == newRecipient ||
                    (internationalNumber != null && PhoneNumberUtils.formatNumberToE164(address, phoneCountry) == internationalNumber))
        }
    }
    val showCategoryRow = showCategories && !showArchived && selected.isEmpty()
    val avatarCache = rememberInboxAvatars()
    val configuration = LocalConfiguration.current
    val timeFormat = remember(configuration) { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val dateFormat = remember(configuration) { DateFormat.getDateInstance(DateFormat.SHORT) }
    fun selectAllVisible() {
        val allSelected = visible.all { it.threadId in selected }
        selected.clear()
        if (!allSelected) selected.addAll(visible.map { it.threadId })
    }

    fun actionsFor(threads: List<SmsConversation>): List<SheetAction> {
        val ids = threads.map { it.threadId }
        val pinned = threads.all { it.pinned }
        val archived = threads.all { it.archived }
        val muted = threads.all { it.muted }
        val pinLabel = when {
            !pinned -> "Pin"
            ids.size > 1 -> "Unpin all"
            else -> "Unpin"
        }
        val actions = mapOf(
            ConversationAction.Pin to ConversationAction.Pin.appearance.action(pinLabel) {
                ids.forEach { viewModel.setPinned(it, !pinned) }
            },
            ConversationAction.Archive to ConversationAction.Archive.appearance.action(if (archived) "Restore" else "Archive") {
                ids.forEach { viewModel.setArchived(it, !archived) }
                selected.removeAll(ids.toSet())
            },
            ConversationAction.Mute to ConversationAction.Mute.appearance.let { appearance ->
                if (muted) appearance.copy(label = "Unmute", icon = Icons.Default.Notifications) else appearance
            }.action {
                ids.forEach { viewModel.setMuted(it, !muted) }
            },
            ConversationAction.Read to ConversationAction.Read.appearance.action {
                viewModel.markThreadsRead(ids, true)
                selected.removeAll(ids.toSet())
            },
            ConversationAction.Unread to ConversationAction.Unread.appearance.action {
                viewModel.markThreadsRead(ids, false)
                selected.removeAll(ids.toSet())
            },
            ConversationAction.Delete to ConversationAction.Delete.appearance.action { pendingDelete = ids },
        )
        return actionOrder.map(actions::getValue)
    }

    val hasCategoryHeader = showCategoryRow && filter == InboxFilter.All && visible.isNotEmpty()
    val listState = key(filter, showArchived) { rememberLazyListState() }
    val editedConversation = editedThread?.let { (id, date) -> conversations.firstOrNull { it.threadId == id && it.date == date } }
    val editedRow = editedConversation?.let { edited -> visible.indexOfFirst { it.threadId == edited.threadId } }
    LaunchedEffect(editedConversation, editedRow, hasCategoryHeader, listState) {
        val conversation = editedConversation ?: return@LaunchedEffect
        if (editedRow != null && editedRow >= 0) {
            listState.scrollToItem(editedRow + if (hasCategoryHeader) 1 else 0)
            onEditedThreadShown()
        } else if (conversation.archived == showArchived && !conversation.spam && (filter != InboxFilter.All || searchOpen)) {
            selectedFilter = InboxFilter.All
            closeSearch()
        } else {
            onEditedThreadShown()
        }
    }

    Scaffold(
        containerColor = AppColors.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (selected.isEmpty() && !showArchived) {
                Column(Modifier.padding(bottom = bottomBarSpace), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                Surface(modifier = Modifier.padding(bottom = bottomBarSpace), color = AppColors.Surface) {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        Button(onClick = { selectionMenu = true }) { Text("Conversation actions") }
                        val selectedThreads = conversations.filter { it.threadId in selected }
                        if (selectionMenu && selectedThreads.isNotEmpty()) {
                            ActionSheet("Conversation actions", actionsFor(selectedThreads)) { selectionMenu = false }
                        }
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
                    else if (showArchived) IconButton(onClick = {
                        closeSearch()
                        showArchived = false
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = ::selectAllVisible) {
                            Icon(Icons.Default.SelectAll, contentDescription = if (visible.all { it.threadId in selected }) "Deselect all" else "Select all")
                        }
                    } else {
                        IconButton(onClick = { if (searchOpen) closeSearch() else searchOpen = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search messages")
                        }
                        IconButton(onClick = { showMenu = true }) { OverflowIcon("More options") }
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background),
            )
            if (searchOpen && selected.isEmpty()) {
                val searchFocus = remember { FocusRequester() }
                val keyboard = LocalSoftwareKeyboardController.current
                LaunchedEffect(Unit) {
                    searchFocus.requestFocus()
                    keyboard?.show()
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .focusRequester(searchFocus),
                    singleLine = true,
                    placeholder = { Text(when (advanced.field) { SearchField.Any -> "Search messages or people"; SearchField.Contact -> "Contact name or number"; SearchField.Message -> "Search message text" }) },
                    trailingIcon = { TextButton(onClick = { search = "" }) { Text("Clear") } },
                )
                if (canComposeToNumber && newRecipient != null) {
                    val contactName by key(newRecipient) {
                        produceState("") { value = viewModel.contactName(newRecipient) }
                    }
                    FilledTonalButton(
                        onClick = { onComposeToNumber(newRecipient) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("New message to ${contactName.ifBlank { newRecipient }}")
                            if (contactName.isNotBlank() && contactName != newRecipient) {
                                Text(newRecipient, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                TextButton(onClick = { showAdvanced = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text(if (advanced.active || advanced.field != SearchField.Any) "Search filters · On" else "Search filters")
                }
            }
            if (showCategoryRow && (filter != InboxFilter.All || visible.isEmpty())) {
                InboxCategories(filter, categoryScroll) { selectedFilter = it }
            }
            if (loadedConversations == null) {
                Spacer(Modifier.fillMaxSize())
            } else if (visible.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when {
                            searchOpen && criteria.active -> "No matching messages"
                            showArchived -> "No archived conversations"
                            filter == InboxFilter.Unread -> "No unread conversations"
                            filter.slot != null -> "No messages received on ${filter.label}"
                            else -> "No messages yet"
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            filter.slot != null && filteredSubscriptionId == null -> "${filter.label} is currently unavailable. See your full history in All."
                            filter.slot != null -> "Incoming messages for this SIM will appear here."
                            filter == InboxFilter.Unread && search.isBlank() -> "You’re all caught up. Read messages remain in All."
                            else -> "SMS conversations from this device will appear here."
                        },
                        color = AppColors.TextSecondary,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 96.dp + if (selected.isEmpty()) bottomBarSpace else 0.dp),
                ) {
                    if (hasCategoryHeader) item(key = "inbox_categories") {
                        InboxCategories(filter, categoryScroll) { selectedFilter = it }
                    }
                    items(visible, key = SmsConversation::threadId, contentType = { "conversation" }) { conversation ->
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
                                    timestamp = if (android.text.format.DateUtils.isToday(conversation.date)) {
                                        timeFormat.format(Date(conversation.date))
                                    } else {
                                        dateFormat.format(Date(conversation.date))
                                    },
                                    avatarCache = avatarCache,
                                    onClick = {
                                        if (selected.isNotEmpty()) {
                                            if (conversation.threadId in selected) selected.remove(conversation.threadId) else selected.add(conversation.threadId)
                                        } else onOpenConversation(conversation.threadId, conversation.address)
                                    },
                                    onLongClick = {
                                        if (conversation.threadId in selected) selected.remove(conversation.threadId) else selected.add(conversation.threadId)
                                    },
                                    actions = { actionsFor(listOf(conversation)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMenu) ActionSheet("Inbox options", listOf(
        SheetAction(if (showArchived) "Inbox" else "Archived", Icons.Default.Archive, tone = ActionTone.Teal) {
            closeSearch()
            selectedFilter = InboxFilter.All
            showArchived = !showArchived
            selected.clear()
        },
        SheetAction("Spam", Icons.Default.Report, tone = ActionTone.Rose, invoke = onOpenSpam),
        SheetAction("Recycle Bin", Icons.Default.DeleteOutline, tone = ActionTone.Amber, invoke = onOpenRecycleBin),
    )) { showMenu = false }

    if (showAdvanced) SearchFilters(advanced, { showAdvanced = false }) {
        advanced = it
        showAdvanced = false
    }
    pendingDelete?.let { ids ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete conversation${if (ids.size == 1) "" else "s"}?") },
            text = { Text(if (viewModel.recycleBinEnabled) "${ids.size} conversation${if (ids.size == 1) "" else "s"} will be deleted. SMS goes to the Recycle Bin. Any MMS messages will be permanently deleted." else "${ids.size} conversation${if (ids.size == 1) "" else "s"} will be permanently deleted because the Recycle Bin is off.") },
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
private fun InboxCategories(filter: InboxFilter, scrollState: ScrollState, onSelect: (InboxFilter) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scrollState).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InboxFilter.entries.forEach { option ->
            FilterChip(
                selected = filter == option,
                onClick = { onSelect(option) },
                label = { Text(option.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AppColors.AccentGreen,
                    selectedLabelColor = AppColors.Background,
                ),
            )
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: SmsConversation,
    selected: Boolean,
    timestamp: String,
    avatarCache: LruCache<String, ImageBitmap>?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    actions: () -> List<SheetAction>,
) {
    var menu by remember { mutableStateOf(false) }
    val brand = remember(conversation.address) { smsSenderBrand(conversation.address) }
    val title = if (conversation.displayName == conversation.address) brand?.name ?: conversation.displayName else conversation.displayName
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(18.dp))
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
                    else ContactAvatar(conversation.contactPhotoUri, title, brand, avatarCache)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (conversation.pinned) Icon(Icons.Default.PushPin, contentDescription = "Pinned", tint = AppColors.AccentGreen, modifier = Modifier.size(16.dp))
                    Text(timestamp, color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
                if (brand != null && title != conversation.address) {
                    Text(conversation.address, color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(30.dp)) {
                            OverflowIcon("Conversation actions", tint = AppColors.TextSecondary)
                        }
                        if (menu) ActionSheet("Conversation actions", actions()) { menu = false }
                    }
                }
            }
        }
        HorizontalDivider(color = AppColors.Divider, modifier = Modifier.padding(start = 72.dp, end = 16.dp))
    }
}

@Composable
internal fun ContactAvatar(
    photoUri: String,
    displayName: String,
    brand: SmsSenderBrand? = null,
    cache: LruCache<String, ImageBitmap>? = null,
) {
    val context = LocalContext.current
    val bitmap by key(photoUri, cache) {
        produceState<ImageBitmap?>(cache?.get(photoUri)) {
            if (photoUri.isBlank() || value != null) return@produceState
            value = withContext(Dispatchers.IO) {
                try {
                    android.graphics.ImageDecoder.decodeBitmap(
                        android.graphics.ImageDecoder.createSource(context.contentResolver, Uri.parse(photoUri)),
                    ) { decoder, info, _ ->
                        val width = info.size.width
                        val height = info.size.height
                        val scale = minOf(1.0, 144.0 / maxOf(width, height))
                        decoder.setTargetSize((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
                    }.asImageBitmap()
                } catch (_: java.io.IOException) {
                    null
                } catch (_: SecurityException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
            value?.let { cache?.put(photoUri, it) }
        }
    }
    val photo = bitmap
    if (photo != null) {
        Image(
            bitmap = photo,
            contentDescription = "$displayName profile photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(CircleShape),
        )
    } else if (brand != null) {
        Image(
            painter = painterResource(brand.logo),
            contentDescription = "${brand.name} logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().clip(CircleShape).background(Color.White).padding(5.dp),
        )
    } else {
        Text(displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun rememberInboxAvatars(): LruCache<String, ImageBitmap>? {
    val context = LocalContext.current
    var cache by remember(context) { mutableStateOf<LruCache<String, ImageBitmap>?>(null) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(context.mainLooper)) {
            override fun onChange(selfChange: Boolean) {
                cache = LruCache(64)
            }
        }
        val registered = try {
            resolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
            cache = LruCache(64)
            true
        } catch (_: SecurityException) {
            false
        }
        onDispose { if (registered) resolver.unregisterContentObserver(observer) }
    }
    return cache
}
