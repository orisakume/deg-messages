package com.example.messages.feature.otp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.messages.core.design.AppColors
import java.text.DateFormat
import java.util.Date

@Composable
fun OtpScreen(viewModel: OtpViewModel = hiltViewModel()) {
    val records = viewModel.records.collectAsStateWithLifecycle().value
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<com.example.messages.core.database.OtpRecordEntity?>(null) }
    var correctedCode by remember { mutableStateOf("") }
    val filtered = records.filter { it.sender.contains(query, true) || it.code.contains(query, true) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("OTPs", style = MaterialTheme.typography.headlineMedium)
        Text("Detected on this device. Codes are not uploaded.", color = AppColors.TextSecondary)
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Search codes or senders") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        if (filtered.isEmpty()) Text(if (query.isBlank()) "No verification codes detected yet." else "No matching codes.", color = AppColors.TextSecondary)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        TextButton(onClick = { viewModel.dismiss(otp.messageId) }) { Icon(Icons.Default.Close, contentDescription = null); Text("Not an OTP? Hide") }
                    }
                }
            }
        }
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
