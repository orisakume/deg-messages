package com.example.messages.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun OverflowIcon(contentDescription: String, tint: Color = LocalContentColor.current) {
    Canvas(Modifier.size(24.dp).semantics { this.contentDescription = contentDescription }) {
        val unit = size.minDimension / 24f
        val radius = 2f * unit
        val spacing = 7f * unit
        drawCircle(Color(0xFFFF4545), radius, Offset(center.x, center.y - spacing))
        drawCircle(tint, radius, center)
        drawCircle(tint, radius, Offset(center.x, center.y + spacing))
    }
}
