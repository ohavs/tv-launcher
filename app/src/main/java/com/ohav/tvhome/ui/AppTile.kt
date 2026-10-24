package com.ohav.tvhome.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohav.tvhome.data.AppEntry

@Composable
fun AppTile(
    app: AppEntry,
    width: Dp,
    focusRequester: FocusRequester,
    moving: Boolean,
    onFocused: () -> Unit,
    onClick: (Rect) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val label by animateFloatAsState(
        if (focused && !moving) 1f else 0f,
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
        label = "label",
    )

    Column(modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        ParallaxSurface(
            focused = focused,
            pressed = pressed,
            jiggle = moving,
            shape = TileShape,
            modifier = Modifier
                .size(width, width * 9 / 16)
                .onGloballyPositioned { bounds = it.boundsInRoot() }
                .focusRequester(focusRequester)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .selectKeys(
                    enabled = !moving,
                    onPressed = { pressed = it },
                    onClick = { onClick(bounds) },
                    onLongPress = onLongPress,
                )
                .focusable(),
        ) {
            TileArt(app)
        }
        Text(
            app.label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 17.dp)
                .graphicsLayer {
                    alpha = label
                    translationY = (1f - label) * -8.dp.toPx()
                },
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(shadow = LegibleShadow),
        )
    }
}

@Composable
fun TileArt(app: AppEntry) {
    val banner = app.banner
    if (banner != null) {
        Image(banner, contentDescription = app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        return
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(lerp(app.accent, Color.White, 0.18f), lerp(app.accent, Color.Black, 0.35f)))),
        contentAlignment = Alignment.Center,
    ) {
        app.icon?.let { Image(it, contentDescription = app.label, modifier = Modifier.size(54.dp)) }
    }
}
