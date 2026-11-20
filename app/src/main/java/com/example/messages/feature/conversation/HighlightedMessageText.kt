package com.example.messages.feature.conversation

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.example.messages.core.design.AppColors
import com.example.messages.data.sms.HighlightKind
import com.example.messages.data.sms.MessageHighlight
import com.example.messages.data.sms.messageHighlights

@Composable
internal fun HighlightedMessageText(
    body: String,
    style: TextStyle,
    selectionActive: Boolean,
    onMessageClick: () -> Unit,
    onMessageLongClick: () -> Unit,
    onHighlightClick: (MessageHighlight) -> Unit,
    onHighlightCopy: (MessageHighlight) -> Unit,
) {
    val highlights = remember(body) { messageHighlights(body) }
    val annotated = remember(body, highlights) {
        buildAnnotatedString {
            append(body)
            highlights.forEach { span ->
                val ink = when (span.kind) {
                    HighlightKind.Link -> Color(0xFF9AFFBF)
                    HighlightKind.Email -> Color(0xFFDDC7FF)
                    HighlightKind.Phone -> Color(0xFFC3E6FF)
                    HighlightKind.Otp -> Color(0xFFFFDC9C)
                }
                addStyle(
                    SpanStyle(color = ink, background = Color(0xFF21342E),
                        fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline),
                    span.range.first, span.range.last + 1,
                )
            }
        }
    }
    var layout by remember(body) { mutableStateOf<TextLayoutResult?>(null) }
    val messageClick by rememberUpdatedState(onMessageClick)
    val messageLongClick by rememberUpdatedState(onMessageLongClick)
    val highlightClick by rememberUpdatedState(onHighlightClick)
    val highlightCopy by rememberUpdatedState(onHighlightCopy)
    val gestures = if (highlights.isEmpty() || selectionActive) Modifier else Modifier
        .semantics {
            customActions = highlights.distinctBy { it.kind to it.value }.map { span ->
                CustomAccessibilityAction("${span.kind.label} actions: ${span.value}") {
                    highlightClick(span)
                    true
                }
            }
        }
        .pointerInput(highlights) {
            detectTapGestures(
                onTap = { position ->
                    val highlight = layout?.findHighlight(position, highlights)
                    if (highlight == null) messageClick() else highlightClick(highlight)
                },
                onLongPress = { position ->
                    val highlight = layout?.findHighlight(position, highlights)
                    if (highlight == null) messageLongClick() else highlightCopy(highlight)
                },
            )
        }
    Text(annotated, modifier = gestures, color = AppColors.TextPrimary, style = style, onTextLayout = { layout = it })
}

private fun TextLayoutResult.findHighlight(position: Offset, highlights: List<MessageHighlight>): MessageHighlight? {
    if (position.y < 0 || position.y >= size.height || position.x < 0 || position.x >= size.width) return null
    // Compose returns an insertion offset. Check both adjacent glyphs so either half of the last
    // character belongs to the highlight without treating nearby whitespace as part of it.
    val offset = getOffsetForPosition(position)
    return sequenceOf(offset, offset - 1).mapNotNull { candidate ->
        highlights.firstOrNull { candidate in it.range }?.takeIf {
            getBoundingBox(candidate).contains(position)
        }
    }.firstOrNull()
}
