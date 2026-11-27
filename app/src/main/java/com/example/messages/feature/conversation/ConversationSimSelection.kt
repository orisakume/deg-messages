package com.example.messages.feature.conversation

import com.example.messages.data.sms.SimOption

internal data class ConversationSimSelection(
    val active: List<SimOption> = emptyList(),
    val rememberedId: Int? = null,
    val permissionGranted: Boolean? = null,
) {
    val selected: SimOption? get() = active.firstOrNull { it.subscriptionId == rememberedId }
    val canSwitch: Boolean get() = if (selected == null) active.isNotEmpty() else active.size > 1

    fun withActiveSubscriptions(options: List<SimOption>, defaultId: Int, hasPermission: Boolean) = copy(
        active = options,
        rememberedId = rememberedId ?: options.firstOrNull { it.subscriptionId == defaultId }?.subscriptionId
            ?: options.singleOrNull()?.subscriptionId,
        permissionGranted = hasPermission,
    )

    fun next(): SimOption? {
        if (!canSwitch) return null
        val index = active.indexOfFirst { it.subscriptionId == rememberedId }
        return active[(index + 1) % active.size]
    }
}
