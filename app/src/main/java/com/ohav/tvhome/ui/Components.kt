package com.ohav.tvhome.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val TileShape = RoundedCornerShape(16.dp)
val CardShape = RoundedCornerShape(14.dp)
val PanelShape = RoundedCornerShape(22.dp)

/** Frosted-glass look without a real blur (blur is too costly on this CPU). */
val GlassFill = Color.White.copy(alpha = 0.09f)
val GlassEdge = Color.White.copy(alpha = 0.11f)

val LegibleShadow = Shadow(Color.Black.copy(alpha = 0.55f), androidx.compose.ui.geometry.Offset(0f, 2f), 10f)

fun KeyEvent.isSelect() = key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

/**
 * Select-key handling with long press: [onClick] on release, [onLongPress] once the key is held.
 * Returns the modifier plus the current pressed state via [onPressed].
 */
private class SelectState {
    var down = false
    var longFired = false
}

fun Modifier.selectKeys(
    enabled: Boolean = true,
    onPressed: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
): Modifier = composed {
    val s = remember { SelectState() }
    onPreviewKeyEvent { ev ->
        if (!enabled || !ev.isSelect()) return@onPreviewKeyEvent false
        when (ev.type) {
            KeyEventType.KeyDown -> {
                val native = ev.nativeKeyEvent
                if (native.repeatCount == 0) {
                    s.down = true; s.longFired = false; onPressed(true)
                } else if (s.down && !s.longFired && onLongPress != null &&
                    ((native.flags and AndroidKeyEvent.FLAG_LONG_PRESS) != 0 || native.repeatCount >= 8)
                ) {
                    s.longFired = true; onPressed(false); onLongPress()
                }
            }
            KeyEventType.KeyUp -> {
                val fire = s.down && !s.longFired
                s.down = false; s.longFired = false; onPressed(false)
                if (fire) onClick()
            }
        }
        true
    }
}

/** tvOS-style list row: turns white with dark text when focused. */
@Composable
fun MenuRow(
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 1.0f else if (focused) 1.035f else 1f, spring(0.6f, 500f), label = "rowScale")
    val bg by animateColorAsState(if (focused) Color.White else Color.White.copy(alpha = 0.08f), label = "rowBg")
    val fg by animateColorAsState(if (focused) Color(0xFF111111) else Color.White, label = "rowFg")
    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale; shadowElevation = if (focused) 16f else 0f; shape = RoundedCornerShape(14.dp); clip = true }
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .selectKeys(onPressed = { pressed = it }, onClick = onClick)
            .focusable()
            .padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, color = fg, fontSize = 19.sp, fontWeight = FontWeight.Medium)
        if (value != null) Text(value, color = fg.copy(alpha = 0.6f), fontSize = 17.sp)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 22.dp, bottom = 10.dp, start = 6.dp),
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        style = TextStyle(letterSpacing = 0.6.sp),
    )
}

@Composable
fun MenuColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}
