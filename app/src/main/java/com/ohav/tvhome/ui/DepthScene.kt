package com.ohav.tvhome.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ohav.tvhome.data.Shade
import com.ohav.tvhome.data.WallpaperImages
import com.ohav.tvhome.data.Wallpapers

/*
 * Depth effect: background photo -> clock -> foreground (the land, cut out of the same photo with a mask).
 * Both pictures are baked when decoded (shade included), so each is one plain bitmap draw: the background is opaque
 * (no blending), the foreground only covers the rows from its skyline down. Parallax moves them in the draw phase.
 * The foreground may only move *up* relative to the background: it is solid from its skyline down, so it always
 * covers the background's copy of the land (sideways or downward offsets would show a double edge).
 */

/** Clock size and place the wallpapers were composed for (make.py: 560 px Oswald 600, centre + 20 px at 1080p). */
val ClockFontSize = 280.dp
val ClockCenterOffset = 10.dp

private const val FADE_MS = 1800

/** Full width, and the photo's height at that width: the screen plus room to move up. */
fun Modifier.depthLayer() = fillMaxWidth()
    .wrapContentHeight(Alignment.Top, unbounded = true)
    .aspectRatio(Wallpapers.SRC_W.toFloat() / Wallpapers.LAYER_H)

/** A light shade, in layer coordinates, baked into both pictures: header legibility on top, a little depth below. */
val DepthShade = Shade(
    0f to Color.Black.copy(alpha = 0.34f),
    0.16f to Color.Black.copy(alpha = 0.06f),
    0.55f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.32f),
)

@Composable
fun DepthBack(images: WallpaperImages?, quiet: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.depthLayer().background(Color(0xFF0B0D12))) {
        Crossfade(targetState = images, animationSpec = tween(if (quiet) 0 else FADE_MS), label = "bg") { img ->
            if (img != null) Image(img.bg, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun DepthFront(images: WallpaperImages?, quiet: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.depthLayer()) {
        Crossfade(targetState = images, animationSpec = tween(if (quiet) 0 else FADE_MS), label = "fg") { img ->
            val fg = img?.fg ?: return@Crossfade
            Box(Modifier.fillMaxSize()) {
                Image(
                    fg, null, contentScale = ContentScale.FillBounds,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().aspectRatio(fg.width.toFloat() / fg.height),
                )
            }
        }
    }
}

/** Condensed, heavy faces for the big clock (subset to digits). Sized so the digits are as tall as Oswald's. */
enum class ClockFont(val file: String, val axes: String?) {
    Oswald("Oswald.ttf", "'wght' 600"),
    Bebas("Bebas.ttf", null),
    Anton("Anton.ttf", null),
    Shoulders("BigShoulders.ttf", "'wght' 800");

    fun typeface(context: Context): Typeface = faces.getOrPut(this) {
        runCatching {
            Typeface.Builder(context.assets, "fonts/$file").apply { if (axes != null) setFontVariationSettings(axes) }.build()
        }.getOrDefault(Typeface.DEFAULT_BOLD)
    }

    /** Height of the big clock's digits. */
    fun digitHeight(context: Context, density: androidx.compose.ui.unit.Density) =
        with(density) { (ClockFontSize.toPx() * Oswald.digitRatio(context)).toDp() }

    /** Digit height per unit of text size. */
    fun digitRatio(context: Context): Float = ratios.getOrPut(this) {
        val r = android.graphics.Rect()
        Paint().apply { typeface = typeface(context); textSize = 1000f }.getTextBounds("0123456789", 0, 10, r)
        r.height() / 1000f
    }

    companion object {
        private val faces = HashMap<ClockFont, Typeface>()
        private val ratios = HashMap<ClockFont, Float>()
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: Oswald
    }
}

/**
 * The big clock: digits drawn natively (a soft shadow keeps them readable on pale skies), in a box exactly
 * around them, so its centre is the digits' centre. Cache it with an offscreen layer; it only redraws each minute.
 */
@Composable
fun DepthClock(
    time: String,
    font: ClockFont,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    layer: GraphicsLayerScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val paint = remember(font, density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font.typeface(context)
            val target = with(density) { ClockFontSize.toPx() } * ClockFont.Oswald.digitRatio(context)
            textSize = target / font.digitRatio(context)
            textAlign = Paint.Align.CENTER
            setShadowLayer(with(density) { 14.dp.toPx() }, 0f, with(density) { 2.dp.toPx() }, 0x55000000)
        }
    }
    paint.color = color.toArgb()
    val digits = remember(paint) { android.graphics.Rect().also { paint.getTextBounds("0123456789", 0, 10, it) } }
    val pad = with(density) { 28.dp.toPx() }
    val w = paint.measureText(time) + 2 * pad
    val h = digits.height() + 2 * pad
    Canvas(
        modifier
            .size(with(density) { w.toDp() }, with(density) { h.toDp() })
            .graphicsLayer {
                layer()
                compositingStrategy = CompositingStrategy.Offscreen
            }
    ) {
        drawIntoCanvas { it.nativeCanvas.drawText(time, size.width / 2f, pad - digits.top, paint) }
    }
}
