package com.example.messages.feature.otp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.messages.core.database.OtpRecordEntity
import com.example.messages.core.design.AppColors
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OtpScreen(bottomBarSpace: Dp = 0.dp, viewModel: OtpViewModel = hiltViewModel()) {
    val records = viewModel.records.collectAsStateWithLifecycle().value
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<OtpRecordEntity?>(null) }
    var messageToDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteFailed by remember { mutableStateOf(false) }
    var correctedCode by remember { mutableStateOf("") }
    val filtered = records.filter { it.sender.contains(query, true) || it.code.contains(query, true) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("OTPs", style = MaterialTheme.typography.headlineMedium)
        Text("Detected on this device. Codes are not uploaded.", color = AppColors.TextSecondary)
        if (deleteFailed) Text("Couldn’t delete the selected SMS. It may have already been removed.", color = AppColors.Danger)
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Search codes or senders") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        if (filtered.isEmpty()) Text(if (query.isBlank()) "No verification codes detected yet." else "No matching codes.", color = AppColors.TextSecondary)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = bottomBarSpace)) {
            items(filtered, key = { it.messageId }) { otp ->
                Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("OTP detected", color = AppColors.AccentGreen, style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(otp.code, style = MaterialTheme.typography.headlineMedium)
                            FilledTonalButton(
                                onClick = { viewModel.copy(otp.messageId, otp.code) },
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = AppColors.GreenTint, contentColor = AppColors.AccentGreen),
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Copy")
                            }
                        }
                        TextButton(onClick = { editing = otp; correctedCode = otp.code }) { Icon(Icons.Default.Edit, contentDescription = null); Text("Correct code") }
                        Text(otp.sender, color = AppColors.TextSecondary)
                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(otp.detectedAt)), color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { viewModel.dismiss(otp.messageId) }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Not an OTP? Hide")
                            }
                            TextButton(
                                onClick = { messageToDelete = otp.messageId },
                                colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Danger),
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }
    records.firstOrNull { it.messageId == messageToDelete }?.let { otp ->
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = { Text("Delete this SMS?") },
            text = {
                Text(if (viewModel.recycleBinEnabled) "Only this SMS from ${otp.sender} will move to the Recycle Bin."
                    else "Only this SMS from ${otp.sender} will be permanently deleted because the Recycle Bin is off.")
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteFailed = false
                    viewModel.deleteMessage(otp) { deleteFailed = true }
                    messageToDelete = null
                }) {
                    Text("Delete", color = AppColors.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { messageToDelete = null }) { Text("Cancel") } },
            containerColor = AppColors.SurfaceElevated,
        )
    }
    editing?.let { otp ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Correct detected code") },
            text = { OutlinedTextField(value = correctedCode, onValueChange = { correctedCode = it.take(10) }, label = { Text("Code") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { viewModel.correct(otp.messageId, correctedCode); editing = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
            containerColor = AppColors.SurfaceElevated,
        )
    }
}
