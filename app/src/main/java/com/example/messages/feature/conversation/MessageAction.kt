package com.example.messages.feature.conversation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import com.example.messages.core.design.ActionAppearance
import com.example.messages.core.design.ActionTone

internal enum class MessageAction(val appearance: ActionAppearance) {
    Copy(ActionAppearance("Copy", Icons.Default.ContentCopy)),
    Forward(ActionAppearance("Forward", Icons.AutoMirrored.Filled.Forward, ActionTone.Teal)),
    Share(ActionAppearance("Share", Icons.Default.Share, ActionTone.Lilac)),
    Delete(ActionAppearance("Delete message", Icons.Default.DeleteOutline, destructive = true)),
    Select(ActionAppearance("Select", Icons.Default.SelectAll)),
    SelectText(ActionAppearance("Select text", Icons.Default.TextFields, ActionTone.Teal)),
    SelectAll(ActionAppearance("Select all", Icons.Default.SelectAll)),
    Details(ActionAppearance("Details", Icons.Default.Info)),
    Schedule(ActionAppearance("Schedule", Icons.Default.Schedule, ActionTone.Amber)),
    Save(ActionAppearance("Save", Icons.Default.BookmarkBorder, ActionTone.Sage)),
    Pin(ActionAppearance("Pin", Icons.Default.PushPin, ActionTone.Amber));

    companion object {
        const val ORDER_KEY = "message_action_order"
    }
}
