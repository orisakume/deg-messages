package com.example.messages.feature.inbox

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import com.example.messages.core.design.ActionAppearance
import com.example.messages.core.design.ActionTone

internal enum class ConversationAction(val appearance: ActionAppearance) {
    Pin(ActionAppearance("Pin", Icons.Default.PushPin, ActionTone.Amber)),
    Archive(ActionAppearance("Archive", Icons.Default.Archive, ActionTone.Teal)),
    Mute(ActionAppearance("Mute", Icons.Default.NotificationsOff, ActionTone.Amber)),
    Read(ActionAppearance("Read", Icons.Default.MarkEmailRead, ActionTone.Sage)),
    Unread(ActionAppearance("Unread", Icons.Default.MarkEmailUnread, ActionTone.Lilac)),
    Delete(ActionAppearance("Delete", Icons.Default.DeleteOutline, destructive = true));

    companion object {
        const val ORDER_KEY = "conversation_action_order"
    }
}
