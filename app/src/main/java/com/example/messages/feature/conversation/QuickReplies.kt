package com.example.messages.feature.conversation

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.messages.core.design.AppColors

@Composable
internal fun QuickReplies(draft: String, onInsert: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("message_templates", Context.MODE_PRIVATE) }
    var replies by remember { mutableStateOf(prefs.getStringSet("items", emptySet()).orEmpty().sorted()) }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { preferences, _ ->
            replies = preferences.getStringSet("items", emptySet()).orEmpty().sorted()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var menu by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun persist(values: List<String>) {
        replies = values.sorted()
        prefs.edit().putStringSet("items", values.toSet()).apply()
    }
    fun edit(reply: String?, initial: String) {
        original = reply
        input = initial
        error = null
        menu = false
        editor = true
    }
    IconButton(onClick = { menu = true }) { Icon(Icons.Default.BookmarkBorder, "Quick replies") }
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.heightIn(max = 420.dp)) {
        DropdownMenuItem(text = { Text("New quick reply") }, onClick = { edit(null, draft.trim()) })
        replies.forEach { reply ->
            DropdownMenuItem(
                text = { Text(reply, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                onClick = { onInsert(reply); menu = false },
                trailingIcon = {
                    Row {
                        IconButton(onClick = { edit(reply, reply) }) { Icon(Icons.Default.Edit, "Edit quick reply") }
                        IconButton(onClick = { persist(replies - reply) }) { Icon(Icons.Default.DeleteOutline, "Remove quick reply") }
                    }
                },
            )
        }
    }
    if (editor) AlertDialog(
        onDismissRequest = { editor = false },
        title = { Text(if (original == null) "New quick reply" else "Edit quick reply") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(input, { input = it; error = null }, label = { Text("Reply text") }, maxLines = 5)
                error?.let { Text(it, color = AppColors.Danger) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val reply = input.trim()
                val others = replies - listOfNotNull(original).toSet()
                error = when {
                    reply.isBlank() -> "Enter reply text."
                    others.any { it.equals(reply, ignoreCase = true) } -> "This reply already exists."
                    others.size >= 50 -> "You can save up to 50 replies."
                    else -> null
                }
                if (error == null) { persist(others + reply); editor = false }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { editor = false }) { Text("Cancel") } },
    )
}

internal fun saveMessageAsQuickReply(context: Context, body: String): String {
    val prefs = context.getSharedPreferences("message_templates", Context.MODE_PRIVATE)
    val replies = prefs.getStringSet("items", emptySet()).orEmpty()
    val reply = body.trim()
    return when {
        replies.any { it.equals(reply, ignoreCase = true) } -> "Already saved in Quick replies"
        replies.size >= 50 -> "Quick replies is full"
        else -> {
            prefs.edit().putStringSet("items", replies + reply).apply()
            "Saved to Quick replies"
        }
    }
}

internal fun appendQuickReply(draft: String, reply: String): String =
    if (draft.isBlank()) reply else draft.trimEnd() + "\n" + reply
