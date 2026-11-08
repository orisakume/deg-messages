package com.example.messages.feature.spam

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.messages.core.design.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpamScreen(onBack: () -> Unit, viewModel: SpamViewModel = hiltViewModel()) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val blocked by viewModel.blocked.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var number by remember { mutableStateOf("") }
    Scaffold(containerColor = AppColors.Background, topBar = {
        TopAppBar(title = { Text("Spam & Blocked") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background))
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Text("Spam filtering and blocking happen on this device. Carrier/system blocking is not guaranteed.", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            TabRow(selectedTabIndex = tab, containerColor = AppColors.Background) {
                listOf("Spam", "Blocked").forEachIndexed { index, title -> Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) }) }
            }
            if (tab == 0) {
                val spam = conversations.filter { it.spam }
                if (spam.isEmpty()) Text("Spam folder is empty", color = AppColors.TextSecondary, modifier = Modifier.padding(16.dp))
                LazyColumn { items(spam, key = { it.threadId }) { row ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text(row.displayName, style = MaterialTheme.typography.titleMedium); Text(row.preview, color = AppColors.TextSecondary, maxLines = 2)
                        Row { TextButton(onClick = { viewModel.restore(row.threadId) }) { Text("Not spam", color = AppColors.AccentGreen) }; TextButton(onClick = { viewModel.delete(row.threadId) }) { Text("Delete", color = AppColors.Danger) } }
                    }; HorizontalDivider(color = AppColors.Divider)
                } }
            } else {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(value = number, onValueChange = { number = it }, modifier = Modifier.weight(1f), label = { Text("Phone number") }, singleLine = true)
                    TextButton(onClick = { if (number.isNotBlank()) { viewModel.block(number); number = "" } }) { Text("Block", color = AppColors.AccentGreen) }
                }
                LazyColumn { items(blocked, key = { it.number }) { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column { Text(row.number); Text("Messages will be silenced and sent to Spam.", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
                        TextButton(onClick = { viewModel.unblock(row.number) }) { Text("Unblock") }
                    }; HorizontalDivider(color = AppColors.Divider)
                } }
            }
        }
    }
}
