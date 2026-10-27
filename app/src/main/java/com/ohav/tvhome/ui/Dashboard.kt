package com.ohav.tvhome.ui

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohav.tvhome.R
import com.ohav.tvhome.data.City
import com.ohav.tvhome.data.ImageLoader
import com.ohav.tvhome.data.Sky
import com.ohav.tvhome.data.WeatherNow
import com.ohav.tvhome.media.Track
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Clock with the date and today's weather beside it — the calm, always-visible part of the dashboard. */
@Composable
fun ClockBlock(weather: WeatherNow?, city: City, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay((60 - Calendar.getInstance().get(Calendar.SECOND)) * 1000L)
        }
    }
    val time = DateFormat.getTimeFormat(context).format(now)
    val date = DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), now).toString()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            time,
            color = Color.White,
            fontSize = 56.sp,
            fontWeight = FontWeight.Light,
            style = TextStyle(letterSpacing = (-1).sp, lineHeight = 60.sp),
        )
        Column(Modifier.padding(start = 20.dp)) {
        Text(
            date,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
        if (weather != null) {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                WeatherGlyph(weather.sky, weather.day, 24.dp)
                Text(
                    "  ${weather.temp}°  ${stringResource(skyName(weather.sky))} · ${city.name}",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 15.sp,
                )
                Text(
                    "   ↑${weather.hi}° ↓${weather.lo}°",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                )
            }
        }
        }
    }
}

private fun skyName(sky: Sky) = when (sky) {
    Sky.Clear -> R.string.sky_clear
    Sky.Partly -> R.string.sky_partly
    Sky.Cloudy -> R.string.sky_cloudy
    Sky.Fog -> R.string.sky_fog
    Sky.Drizzle -> R.string.sky_drizzle
    Sky.Rain -> R.string.sky_rain
    Sky.Snow -> R.string.sky_snow
    Sky.Storm -> R.string.sky_storm
}

/**
 * Focusable glass surface used by the dashboard: lifts and brightens on focus.
 * [content] gets the focus amount (0..1) to restyle itself.
 */
@Composable
fun GlassButton(
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = PanelShape,
    focusScale: Float = 1.06f,
    solidFocus: Boolean = false,
    tilt: Boolean = true,
    content: @Composable BoxScope.(focus: Float) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val f by animateFloatAsState(if (focused) 1f else 0f, spring(1f, Spring.StiffnessMediumLow), label = "glass")
    ParallaxSurface(
        focused = focused,
        pressed = pressed,
        shape = shape,
        focusScale = focusScale,
        shadow = solidFocus,
        tilt = tilt,
        modifier = modifier
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .selectKeys(onPressed = { pressed = it }, onClick = { onClick(bounds) })
            .focusable(),
    ) {
        val top = if (solidFocus) lerp(GlassFill, Color.White, f) else lerp(GlassFill, Color.White.copy(alpha = 0.2f), f)
        Box(
            Modifier
                .fillMaxSize()
                .background(top)
                .border(1.dp, lerp(GlassEdge, Color.White.copy(alpha = 0.45f), f), shape),
        ) { content(f) }
    }
}

@Composable
fun CircleButton(glyph: Glyph, requester: FocusRequester, onFocused: () -> Unit, onClick: (Rect) -> Unit, size: Dp = 40.dp) {
    GlassButton(requester, onFocused, onClick, Modifier.size(size), CircleShape, focusScale = 1.14f, solidFocus = true) { f ->
        GlyphIcon(glyph, lerp(Color.White, Color(0xFF111318), f), size * 0.46f, Modifier.align(Alignment.Center))
    }
}

/** Artwork, title and app of whatever is playing; OK opens the player. */
@Composable
fun NowPlayingCard(track: Track, requester: FocusRequester, onFocused: () -> Unit, onClick: (Rect) -> Unit, height: Dp) {
    val context = LocalContext.current
    val artPx = with(LocalDensity.current) { (height * 1.2f).roundToPx() }
    val art by produceState(track.art ?: track.artUri?.let { ImageLoader.cached(it, artPx) }, track.art, track.artUri) {
        if (track.art == null) track.artUri?.let { value = ImageLoader.load(context, it, artPx) }
    }
    GlassButton(requester, onFocused, onClick, Modifier.size(height * 3.1f, height)) { f ->
        Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(height - 20.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF3B4256), Color(0xFF1C202B)))),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(art, transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) }, label = "art") { img ->
                    if (img != null) Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else GlyphIcon(Glyph.Play, Color.White.copy(alpha = 0.6f), 22.dp)
                }
            }
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (track.playing) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.4f))
                    )
                    Text(
                        "  " + track.app.uppercase() + " · " + stringResource(if (track.playing) R.string.now_playing else R.string.paused),
                        color = Color.White.copy(alpha = 0.6f + 0.2f * f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        style = TextStyle(letterSpacing = 0.8.sp),
                    )
                }
                Text(
                    track.title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
                track.subtitle?.let {
                    Text(it, color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun PlayPauseButton(playing: Boolean, requester: FocusRequester, onFocused: () -> Unit, onClick: () -> Unit) {
    CircleButton(if (playing) Glyph.Pause else Glyph.Play, requester, onFocused, { onClick() }, size = 52.dp)
}

/** Opens the grid of every camera. */
@Composable
fun AllCamerasCard(count: Int, requester: FocusRequester, onFocused: () -> Unit, onClick: (Rect) -> Unit, height: Dp) {
    GlassButton(requester, onFocused, onClick, Modifier.size(height * 1.3f, height)) { f ->
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            GlyphIcon(Glyph.Apps, Color.White.copy(alpha = 0.85f + 0.15f * f), 24.dp)
            Text(
                stringResource(R.string.all_cameras),
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                stringResource(R.string.cameras_count, count),
                color = Color.White.copy(alpha = 0.55f + 0.2f * f), fontSize = 11.sp, maxLines = 1,
            )
        }
    }
}

/** Google Home shortcut — hidden for now; may come back with live device data. */
@Composable
fun SmartHomeCard(requester: FocusRequester, onFocused: () -> Unit, onClick: (Rect) -> Unit, height: Dp) {
    GlassButton(requester, onFocused, onClick, Modifier.size(height * 2.5f, height)) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF7DB2FF), Color(0xFF3B6FE0)))),
                contentAlignment = Alignment.Center,
            ) { GlyphIcon(Glyph.Home, Color.White, 24.dp) }
            Column(Modifier.padding(horizontal = 14.dp)) {
                Text(
                    stringResource(R.string.smart_home),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    "Google Home",
                    color = Color.White.copy(alpha = 0.55f + 0.2f * f),
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}
