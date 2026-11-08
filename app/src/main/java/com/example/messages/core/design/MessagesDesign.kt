package com.example.messages.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val FixedColors = darkColorScheme(
    primary = AppColors.AccentGreen,
    onPrimary = AppColors.Background,
    secondary = AppColors.AccentGreenDark,
    background = AppColors.Background,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Surface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.SurfaceElevated,
    onSurfaceVariant = AppColors.TextSecondary,
    outline = AppColors.Divider,
    error = AppColors.Danger,
)

@Composable
fun MessagesDesign(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FixedColors, content = content)
}
