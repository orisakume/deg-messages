package com.example.messages.feature.schedule

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.SharedPreferences
import android.telephony.SubscriptionManager
import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.messages.feature.conversation.ComposeViewModel
import com.example.messages.core.design.AppColors
import com.example.messages.data.schedule.*
import com.example.messages.data.sms.SimOption
import com.example.messages.feature.conversation.SimSelector
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledSmsScreen(viewModel: ComposeViewModel = hiltViewModel(), onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ScheduledSmsStore(context) }
    var messages by remember { mutableStateOf(store.list()) }
    var editing by remember { mutableStateOf<ScheduledSms?>(null) }
    var cancelling by remember { mutableStateOf<ScheduledSms?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> messages = store.list() }
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        messages = store.list()
        onDispose { store.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun change(action: () -> Unit) {
        scope.launch {
            error = withContext(Dispatchers.IO) { runCatching(action).exceptionOrNull()?.message }
        }
    }
    Scaffold(containerColor = AppColors.Background, topBar = {
        TopAppBar(
            title = { Text("Scheduled SMS") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background),
        )
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let { message -> item { Text(message, color = AppColors.Danger) } }
            if (messages.isEmpty()) item { Text("No scheduled messages. Schedule an SMS from a composer.", color = AppColors.TextSecondary) }
            items(messages, key = { it.id }) { item ->
                Column {
                    Text(item.address, style = MaterialTheme.typography.titleMedium)
                    Text(item.body)
                    Text("${displayTime(context, item.time)} · ${when (item.state) {
                        SendState.Queued -> "Scheduled"
                        SendState.Sending -> "Sending"
                        SendState.Failed -> "Failed"
                        SendState.Unconfirmed -> "Send status unknown"
                    }}", color = AppColors.TextSecondary)
                    Row {
                        if (item.state == SendState.Queued || item.state == SendState.Failed)
                            TextButton(onClick = { editing = item }) { Text("Edit") }
                        if (item.state == SendState.Failed) TextButton(onClick = { change { store.retry(item) } }) { Text("Retry") }
                        if (item.state != SendState.Sending)
                            TextButton(onClick = { cancelling = item }) { Text(if (item.state == SendState.Queued) "Cancel" else "Remove") }
                    }
                    HorizontalDivider(color = AppColors.Divider)
                }
            }
        }
    }
    editing?.let { item ->
        ScheduleSmsDialog(item.address, item.body, item.subscription, { editing = null }, { editing = null }, item, viewModel::subscriptions)
    }
    cancelling?.let { item ->
        AlertDialog(onDismissRequest = { cancelling = null },
            title = { Text(if (item.state == SendState.Queued) "Cancel scheduled SMS?" else "Remove scheduled SMS?") },
            confirmButton = { TextButton(onClick = { cancelling = null; change { store.cancel(item) } }) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = { cancelling = null }) { Text("Keep") } })
    }
}

@Composable
internal fun ScheduleSmsDialog(
    address: String,
    body: String,
    subscription: Int,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    previous: ScheduledSms? = null,
    subscriptions: (suspend () -> List<SimOption>)? = null,
) {
    val context = LocalContext.current
    val store = remember { ScheduledSmsStore(context) }
    val scope = rememberCoroutineScope()
    var destination by remember { mutableStateOf(address) }
    var message by remember { mutableStateOf(body) }
    var time by remember { mutableLongStateOf(previous?.time?.takeIf { it > System.currentTimeMillis() }
        ?: (System.currentTimeMillis() + 3_600_000)) }
    var sim by remember { mutableIntStateOf(subscription) }
    var selected by remember { mutableStateOf<SimOption?>(null) }
    LaunchedEffect(subscription) {
        if (subscription >= 0) selected = subscriptions?.invoke()?.firstOrNull { it.subscriptionId == subscription }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("Schedule SMS") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(destination, { destination = it }, label = { Text("Phone number") }, singleLine = true)
                OutlinedTextField(message, { message = it }, label = { Text("Message") }, maxLines = 3)
                TextButton(onClick = {
                    val calendar = Calendar.getInstance().apply { timeInMillis = time }
                    DatePickerDialog(context, { _, year, month, day ->
                        TimePickerDialog(context, { _, hour, minute ->
                            calendar.set(year, month, day, hour, minute, 0)
                            calendar.set(Calendar.MILLISECOND, 0)
                            time = calendar.timeInMillis
                        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), DateFormat.is24HourFormat(context)).show()
                    }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).apply {
                        datePicker.minDate = System.currentTimeMillis()
                    }.show()
                }) { Text(displayTime(context, time)) }
                if (subscriptions != null) {
                    SimSelector(selected, subscriptions, autoSelect = subscription == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        selected = it
                        sim = it.subscriptionId
                    }
                }
                Text("Android may delay sending while the device is idle.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = AppColors.Danger) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    error = withContext(Dispatchers.IO) {
                        runCatching { store.save(destination, message, time, sim, previous) }.exceptionOrNull()?.let { it.message ?: "Could not schedule this SMS." }
                    }
                    busy = false
                    if (error == null) onSaved()
                }
            }) { Text(if (previous == null) "Schedule" else "Save changes") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text("Close") } },
    )
}

private fun displayTime(context: android.content.Context, time: Long): String =
    DateFormat.getMediumDateFormat(context).format(Date(time)) + " · " + DateFormat.getTimeFormat(context).format(Date(time))
