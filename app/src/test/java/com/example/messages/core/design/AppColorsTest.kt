package com.example.messages.core.design

import org.junit.Assert.assertEquals
import androidx.compose.ui.graphics.Color
import org.junit.Test

class AppColorsTest {
    @Test
    fun fixedPaletteMatchesProductSpecification() {
        assertEquals(Color(0xFF000000), AppColors.Background)
        assertEquals(Color(0xFFFFFFFF), AppColors.TextPrimary)
        assertEquals(Color(0xFFBDBDBD), AppColors.TextSecondary)
        assertEquals(Color(0xFF00C853), AppColors.AccentGreen)
        assertEquals(Color(0xFF008F3D), AppColors.AccentGreenDark)
        assertEquals(Color(0xFF002B16), AppColors.GreenTint)
        assertEquals(Color(0xFFFF5252), AppColors.Danger)
        assertEquals(Color(0xFFFFC107), AppColors.Warning)
    }
}
