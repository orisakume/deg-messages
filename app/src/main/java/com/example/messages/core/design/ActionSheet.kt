package com.example.messages.core.design

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal enum class ActionTone(val lightIcon: Color, val darkIcon: Color) {
    Blue(Color(0xFF075FBB), Color(0xFF79BAFF)),
    Teal(Color(0xFF006D5D), Color(0xFF50D9C1)),
    Lilac(Color(0xFF6D3BB7), Color(0xFFC5A3FF)),
    Amber(Color(0xFF8A4B00), Color(0xFFFFBB5E)),
    Rose(Color(0xFFAB2448), Color(0xFFFF94AE)),
    Sage(Color(0xFF336923), Color(0xFF9DDB78)),
}

@Composable
internal fun ActionDropdown(actions: List<SheetAction>, onDismiss: () -> Unit) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        actions.forEach { action ->
            val color = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            DropdownMenuItem(
                text = { Text(action.label, color = color) },
                leadingIcon = { Icon(action.icon, null, tint = color) },
                onClick = {
                    onDismiss()
                    action.invoke()
                },
            )
        }
    }
}

internal data class SheetAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val tone: ActionTone = ActionTone.Blue,
    val invoke: () -> Unit,
)

internal data class ActionAppearance(
    val label: String,
    val icon: ImageVector,
    val tone: ActionTone = ActionTone.Blue,
    val destructive: Boolean = false,
) {
    fun action(label: String = this.label, invoke: () -> Unit) =
        SheetAction(label, icon, destructive, tone, invoke)
}

internal fun <T : Enum<T>> restoreActionOrder(saved: String?, defaults: List<T>): List<T> {
    val byName = defaults.associateBy { it.name }
    return (saved.orEmpty().split(',').mapNotNull(byName::get) + defaults).distinct()
}

@Composable
private fun actionSheetColors(): ColorScheme {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    return remember(context, dark) {
        when {
            Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
            Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
            dark -> darkColorScheme()
            else -> lightColorScheme()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActionSheet(title: String, actions: List<SheetAction>, onDismiss: () -> Unit) {
    val colors = actionSheetColors()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme = colors) {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = colors.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 20.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp, bottom = 10.dp))
                ActionIconGrid(actions, enabled = !closing) { index ->
                    closing = true
                    scope.launch {
                        sheet.hide()
                        if (!sheet.isVisible) {
                            onDismiss()
                            actions[index].invoke()
                        } else closing = false
                    }
                }
            }
        }
    }
}

@Composable
internal fun ActionIconGrid(
    actions: List<SheetAction>,
    selectedIndex: Int = -1,
    enabled: Boolean = true,
    showPreviewSurface: Boolean = false,
    onClick: (Int) -> Unit,
) {
    val colors = actionSheetColors()
    val dark = isSystemInDarkTheme()
    Column(
        if (showPreviewSurface) {
            Modifier.fillMaxWidth().background(colors.surfaceContainerHigh, RoundedCornerShape(18.dp))
        } else Modifier,
    ) {
        actions.chunked(4).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                row.forEachIndexed { columnIndex, action ->
                    val index = rowIndex * 4 + columnIndex
                    val active = index == selectedIndex
                    val tone = if (action.destructive) ActionTone.Rose else action.tone
                    val iconTint = if (dark) tone.darkIcon else tone.lightIcon
                    val iconBackground = lerp(colors.surfaceContainerHighest, iconTint, if (dark) 0.27f else 0.13f)
                    Column(
                        Modifier.weight(1f).clickable(enabled = enabled, role = Role.Button) { onClick(index) }
                            .then(if (active) Modifier.semantics { selected = true } else Modifier)
                            .padding(horizontal = 2.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(48.dp).background(
                            iconBackground, CircleShape,
                        ).then(if (active) Modifier.border(2.dp, iconTint, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center) {
                            Icon(action.icon, null, modifier = Modifier.size(23.dp), tint = iconTint)
                        }
                        Text(action.label, Modifier.padding(top = 6.dp), textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium, color = if (active) iconTint else colors.onSurface)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
