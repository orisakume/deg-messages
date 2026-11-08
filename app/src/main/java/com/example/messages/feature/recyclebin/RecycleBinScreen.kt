package com.example.messages.feature.recyclebin

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.messages.core.design.AppColors
import com.example.messages.core.database.DeletedItemEntity
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(onBack: () -> Unit, viewModel: RecycleBinViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val selected = remember { mutableStateListOf<Long>() }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmPermanent by remember { mutableStateOf(false) }
    Scaffold(containerColor = AppColors.Background, topBar = {
        TopAppBar(
            title = { Text(if (selected.isEmpty()) "Recycle Bin" else "${selected.size} selected") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
            actions = {
                if (selected.isNotEmpty()) {
                    IconButton(onClick = { if (selected.size == items.size) selected.clear() else { selected.clear(); selected.addAll(items.map { it.id }) } }) { Icon(Icons.Default.SelectAll, contentDescription = "Select all deleted messages") }
                    IconButton(onClick = { viewModel.restore(selected.toList()); selected.clear() }) { Icon(Icons.Default.Restore, contentDescription = "Restore selected", tint = AppColors.AccentGreen) }
                    IconButton(onClick = { confirmPermanent = true }) { Icon(Icons.Default.DeleteOutline, contentDescription = "Permanently delete selected", tint = AppColors.Danger) }
                } else if (items.isNotEmpty()) TextButton(onClick = { confirmEmpty = true }) { Text("Empty", color = AppColors.Danger) }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background),
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text("Deleted messages are kept for up to 30 days in app-private storage. Uninstalling may remove recovery data.", color = AppColors.TextSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
            if (items.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Recycle Bin is empty", color = AppColors.TextSecondary) }
            LazyColumn {
                items(items, key = DeletedItemEntity::id) { item ->
                    val active = item.id in selected
                    Column(Modifier.fillMaxWidth().combinedClickable(onClick = { if (selected.isNotEmpty()) { if (active) selected.remove(item.id) else selected.add(item.id) } }, onLongClick = { if (!active) selected.add(item.id) }).padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(item.address, style = MaterialTheme.typography.titleMedium)
                        Text(item.body, maxLines = 2, color = AppColors.TextSecondary)
                        val days = ((item.expiresAt - System.currentTimeMillis()).coerceAtLeast(0) / 86_400_000L)
                        Text("Deleted ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.deletedAt))} · $days days remaining", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { viewModel.restore(listOf(item.id)) }) { Text("Restore", color = AppColors.AccentGreen) }
                            TextButton(onClick = { selected.clear(); selected.add(item.id); confirmPermanent = true }) { Text("Delete forever", color = AppColors.Danger) }
                        }
                    }
                    HorizontalDivider(color = AppColors.Divider)
                }
            }
        }
    }
    if (confirmPermanent) AlertDialog(onDismissRequest = { confirmPermanent = false }, title = { Text("Permanently delete selected?") }, text = { Text("This cannot be undone.") }, confirmButton = { TextButton(onClick = { viewModel.delete(selected.toList()); selected.clear(); confirmPermanent = false }) { Text("Delete", color = AppColors.Danger) } }, dismissButton = { TextButton(onClick = { confirmPermanent = false }) { Text("Cancel") } }, containerColor = AppColors.SurfaceElevated)
    if (confirmEmpty) AlertDialog(onDismissRequest = { confirmEmpty = false }, title = { Text("Empty Recycle Bin?") }, text = { Text("All saved messages will be permanently deleted.") }, confirmButton = { TextButton(onClick = { viewModel.empty(); confirmEmpty = false }) { Text("Empty", color = AppColors.Danger) } }, dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } }, containerColor = AppColors.SurfaceElevated)
}
