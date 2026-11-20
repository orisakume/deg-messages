package com.example.messages.core.design

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActionSheet(title: String, actions: List<SheetAction>, onDismiss: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = remember(context, dark) {
        when {
            Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
            Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
            dark -> darkColorScheme()
            else -> lightColorScheme()
        }
    }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme = colors) {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = colors.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 20.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp, bottom = 10.dp))
                actions.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        row.forEach { action ->
                            val tone = if (action.destructive) ActionTone.Rose else action.tone
                            val iconTint = if (dark) tone.darkIcon else tone.lightIcon
                            val iconBackground = lerp(colors.surfaceContainerHighest, iconTint, if (dark) 0.27f else 0.13f)
                            Column(
                                Modifier.weight(1f).clickable(enabled = !closing, role = Role.Button) {
                                    closing = true
                                    scope.launch {
                                        sheet.hide()
                                        if (!sheet.isVisible) {
                                            onDismiss()
                                            action.invoke()
                                        } else closing = false
                                    }
                                }.padding(horizontal = 2.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(Modifier.size(48.dp).background(
                                    iconBackground, CircleShape,
                                ), contentAlignment = Alignment.Center) {
                                    Icon(action.icon, null, modifier = Modifier.size(23.dp),
                                        tint = iconTint)
                                }
                                Text(action.label, Modifier.padding(top = 6.dp), textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
