package com.ohav.tvhome.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohav.tvhome.data.City
import com.ohav.tvhome.data.WeatherNow
import java.util.Locale

/** Smooth 0..1 ramp of [x] between [a] and [b]. */
internal fun ramp(x: Float, a: Float, b: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** The big clock, centred on the screen: drawn between the photo and its foreground. */
@Composable
fun BigClock(font: ClockFont, modifier: Modifier = Modifier, layer: GraphicsLayerScope.() -> Unit = {}) {
    val context = LocalContext.current
    val now by rememberNow()
    Box(modifier.fillMaxSize()) {
        DepthClock(
            DateFormat.getTimeFormat(context).format(now),
            font,
            Modifier.align(Alignment.Center).offset(y = ClockCenterOffset),
            layer = layer,
        )
    }
}

/** Date and weather in one line just above the big clock (in front of the photo, never hidden). */
@Composable
fun ClockInfo(weather: WeatherNow?, city: City, modifier: Modifier = Modifier, layer: GraphicsLayerScope.() -> Unit = {}) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val now by rememberNow()
    val date = DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), now).toString()
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val above = screenH / 2 + ClockCenterOffset - ClockFont.Oswald.digitHeight(context, density) / 2 - 22.dp
    Box(modifier.fillMaxWidth().height(above)) {
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    layer()
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(date, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
            if (weather != null) {
                Text("   ·   ", color = Color.White.copy(alpha = 0.6f), fontSize = 26.sp)
                WeatherGlyph(weather.sky, weather.day, 28.dp)
                Text(
                    "  ${weather.temp}°  ${stringResource(skyName(weather.sky))} · ${city.name}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 22.sp,
                )
            }
        }
    }
}

/** "More below": a quiet chevron at the bottom of the clock page. */
@Composable
fun MoreBelow(modifier: Modifier = Modifier, alpha: () -> Float) {
    Box(modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 30.dp)
                .size(34.dp, 14.dp)
                .graphicsLayer {
                    this.alpha = 0.7f * alpha()
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
        ) {
            val w = 2.dp.toPx()
            drawLine(Color.White, Offset(w, w), Offset(size.width / 2f, size.height - w), w, StrokeCap.Round)
            drawLine(Color.White, Offset(size.width - w, w), Offset(size.width / 2f, size.height - w), w, StrokeCap.Round)
        }
    }
}
