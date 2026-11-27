package com.example.messages.feature.conversation

import com.example.messages.data.sms.SimOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSimSelectionTest {
    private val jio = SimOption(42, "Jio", 0)
    private val airtel = SimOption(108, "Airtel", 1)

    @Test
    fun cyclesThroughSubscriptionIdsAndWraps() {
        val selection = ConversationSimSelection().withActiveSubscriptions(listOf(jio, airtel), jio.subscriptionId, true)
        assertEquals(jio, selection.selected)
        assertEquals(airtel, selection.next())
        assertEquals(jio, selection.copy(rememberedId = airtel.subscriptionId).next())
    }

    @Test
    fun rememberedSubscriptionSurvivesDefaultAndSlotChanges() {
        val movedAirtel = airtel.copy(slotIndex = 0)
        val movedJio = jio.copy(slotIndex = 1)
        val selection = ConversationSimSelection(rememberedId = airtel.subscriptionId)
            .withActiveSubscriptions(listOf(movedAirtel, movedJio), movedJio.subscriptionId, true)
        assertEquals(movedAirtel, selection.selected)
        assertEquals(movedJio, selection.next())
    }

    @Test
    fun removedSubscriptionNeedsAnExplicitReplacementEvenWithOnlyOneActiveSim() {
        val selection = ConversationSimSelection(rememberedId = airtel.subscriptionId)
            .withActiveSubscriptions(listOf(jio), jio.subscriptionId, true)
        assertNull(selection.selected)
        assertTrue(selection.canSwitch)
        assertEquals(jio, selection.next())
        assertEquals(airtel, selection.withActiveSubscriptions(listOf(jio, airtel), jio.subscriptionId, true).selected)
    }

    @Test
    fun oneSimIsShownWithoutSwitchingAndNoDefaultDualSimNeedsAChoice() {
        val selection = ConversationSimSelection().withActiveSubscriptions(listOf(jio), -1, true)
        assertEquals(jio, selection.selected)
        assertFalse(selection.canSwitch)
        assertNull(selection.next())

        val noDefault = ConversationSimSelection().withActiveSubscriptions(listOf(jio, airtel), -1, true)
        assertNull(noDefault.selected)
        assertEquals(jio, noDefault.next())
        assertNull(noDefault.withActiveSubscriptions(emptyList(), -1, true).next())
    }
}
