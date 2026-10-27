package com.ohav.tvhome.ui

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohav.tvhome.data.AppEntry
import com.ohav.tvhome.data.TileStyle

/** Banner: 16:9 card. Round: a circle a bit narrower than the slot, leaving room for the glow. */
fun tileHeight(style: TileStyle, width: Dp): Dp = if (style == TileStyle.Banner) width * 9 / 16 else width * 0.74f

fun tileWidth(style: TileStyle, width: Dp): Dp = if (style == TileStyle.Banner) width else width * 0.74f

fun tileShape(style: TileStyle): Shape = if (style == TileStyle.Banner) TileShape else CircleShape

@Composable
fun AppTile(
    app: AppEntry,
    style: TileStyle,
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
    val reveal by animateFloatAsState(
        if (focused || moving) 1f else 0f,
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
        label = "reveal",
    )

    // The focused tile grows over its neighbours.
    Column(modifier.zIndex(if (focused) 1f else 0f).width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        val round = style == TileStyle.Round
        ParallaxSurface(
            focused = focused,
            pressed = pressed,
            jiggle = moving,
            shape = tileShape(style),
            focusScale = if (round) 1.16f else 1.13f,
            modifier = Modifier
                .then(if (round) Modifier.brandGlow(app.brand) { reveal } else Modifier)
                .size(tileWidth(style, width), tileHeight(style, width))
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
            TileArt(app, style) { reveal }
        }
        Text(
            app.label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .graphicsLayer {
                    // Round tiles always name the app; banners only on focus (the banner already says it).
                    alpha = if (moving) 0f else if (round) 0.62f + 0.38f * reveal else reveal
                    translationY = if (round) reveal * 4.dp.toPx() else (1f - reveal) * -6.dp.toPx()
                },
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Soft halo in the app's colour behind a round tile, fading in with focus. */
private fun Modifier.brandGlow(color: Color, amount: () -> Float) = drawBehind {
    val amount = amount()
    if (amount <= 0.01f) return@drawBehind
    val r = size.minDimension * 0.86f
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = 0.55f * amount), Color.Transparent), center, r),
        radius = r,
    )
}

/**
 * Tile artwork. Round style: the app's logo on a frosted-glass disc that fills with the app's own
 * colour when focused. Banner style: the classic Android TV banner.
 */
@Composable
fun TileArt(app: AppEntry, style: TileStyle, reveal: () -> Float = { 0f }) {
    val banner = app.banner
    if (style == TileStyle.Banner && banner != null || app.icon == null && banner != null) {
        Image(banner!!, contentDescription = app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        return
    }
    val fill = app.brand
    Box(
        Modifier
            .fillMaxSize()
            .background(GlassFill)
            .border(1.dp, GlassEdge, tileShape(style)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = reveal() }
                .background(Brush.linearGradient(listOf(lerp(fill, Color.White, 0.10f), lerp(fill, Color.Black, 0.16f))))
        )
        val glyph = app.glyph
        when {
            glyph != null -> Image(
                glyph,
                contentDescription = app.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(0.64f).fillMaxHeight(0.42f),
            )
            app.icon != null -> Image(
                app.icon,
                contentDescription = app.label,
                modifier = Modifier.fillMaxHeight(0.62f).aspectRatio(1f).clip(CircleShape),
            )
        }
    }
}
