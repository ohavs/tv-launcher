package com.ohav.tvhome.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohav.tvhome.data.City
import com.ohav.tvhome.data.WeatherNow
import java.util.Locale
import kotlin.math.pow

/** Smooth 0..1 ramp of [x] between [a] and [b]. */
internal fun ramp(x: Float, a: Float, b: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Size of the hero digits; the header clock is 56sp with the same line-height ratio, so the two line up. */
private const val HERO_SP = 280f
private const val HEADER_SP = 56f
private const val HEADER_LINE_SP = 60f
/** Page progress at which the big clock reaches the header, and from which the header clock takes over. */
private const val LAND = 0.82f
internal const val HANDOVER = 0.84f

/**
 * First page of the clock layout: a huge clock over the photo. As [page] goes 0 → 1 (down to the apps) the time
 * flies into the header clock at [target] and shrinks into it, while the date and weather drift up and fade.
 * Everything moves in the draw phase: scrolling never recomposes. [enter] is the entrance animation (0..1).
 */
@Composable
fun HeroClock(
    weather: WeatherNow?,
    city: City,
    page: () -> Float,
    enter: () -> Float,
    target: () -> Rect?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val now by rememberNow()
    val time = DateFormat.getTimeFormat(context).format(now)
    val date = DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), now).toString()
    var bounds by remember { mutableStateOf<Rect?>(null) }

    Box(modifier.fillMaxSize()) {
        // A soft shade behind the digits keeps thin white strokes readable on any photo.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = (1f - ramp(page(), 0f, 0.6f)) * enter()
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .drawWithCache {
                    val c = Offset(size.width / 2f, size.height * 0.46f)
                    val r = size.height * 0.6f
                    val brush = Brush.radialGradient(
                        0f to Color.Black.copy(alpha = 0.40f),
                        0.5f to Color.Black.copy(alpha = 0.22f),
                        1f to Color.Transparent,
                        center = c,
                        radius = r,
                    )
                    onDrawBehind { scale(1.75f, 1f, c) { drawCircle(brush, r, c) } }
                }
        )

        Column(Modifier.align(Alignment.Center).offset(y = (-6).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                time,
                modifier = Modifier
                    .onGloballyPositioned { bounds = it.boundsInRoot() }
                    .graphicsLayer {
                        val p = page()
                        val e = enter()
                        val b = bounds
                        val t = target()
                        if (b != null && t != null && b.height > 0f) {
                            // Lands a little early, so the hand-over to the header clock happens in place.
                            // Geometric scale feels even all the way down; the centre travels in a straight line.
                            val g = (p / LAND).coerceAtMost(1f)
                            val s = (t.height / b.height).pow(g)
                            scaleX = s
                            scaleY = s
                            translationX = (t.center.x - b.center.x) * g
                            translationY = (t.center.y - b.center.y) * g
                        }
                        translationY += (1f - e) * 26.dp.toPx()
                        alpha = (1f - ramp(p, HANDOVER, 1f)) * e
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    },
                color = Color.White,
                fontSize = HERO_SP.sp,
                fontWeight = FontWeight.ExtraLight,
                style = TextStyle(letterSpacing = (-7).sp, lineHeight = (HERO_SP * HEADER_LINE_SP / HEADER_SP).sp),
            )
            Column(
                Modifier
                    .offset(y = (-30).dp)
                    .graphicsLayer {
                        val p = page()
                        val e = enter()
                        alpha = (1f - ramp(p, 0f, 0.35f)) * ramp(e, 0.25f, 1f)
                        translationY = -p * 140.dp.toPx() + (1f - e) * 34.dp.toPx()
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(date, color = Color.White.copy(alpha = 0.95f), fontSize = 30.sp, fontWeight = FontWeight.Normal)
                if (weather != null) {
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        WeatherGlyph(weather.sky, weather.day, 30.dp)
                        Text(
                            "  ${weather.temp}°  ${stringResource(skyName(weather.sky))} · ${city.name}",
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 21.sp,
                        )
                        Text(
                            "   ↑${weather.hi}° ↓${weather.lo}°",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 19.sp,
                        )
                    }
                }
            }
        }

        // "More below": a quiet chevron that leaves as soon as the page moves.
        Canvas(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 30.dp)
                .size(34.dp, 14.dp)
                .graphicsLayer {
                    alpha = 0.6f * (1f - ramp(page(), 0f, 0.2f)) * ramp(enter(), 0.5f, 1f)
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
        ) {
            val w = 2.dp.toPx()
            drawLine(Color.White, Offset(w, w), Offset(size.width / 2f, size.height - w), w, StrokeCap.Round)
            drawLine(Color.White, Offset(size.width - w, w), Offset(size.width / 2f, size.height - w), w, StrokeCap.Round)
        }
    }
}
