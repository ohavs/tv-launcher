package com.ohav.tvhome.screensaver

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohav.tvhome.R
import com.ohav.tvhome.data.ImageLoader
import com.ohav.tvhome.data.Prefs
import com.ohav.tvhome.data.Shade
import com.ohav.tvhome.data.WeatherApi
import com.ohav.tvhome.data.cityOf
import com.ohav.tvhome.media.NowPlaying
import com.ohav.tvhome.media.Track
import com.ohav.tvhome.ui.BigClock
import com.ohav.tvhome.ui.ClockFont
import com.ohav.tvhome.ui.ClockInfo
import com.ohav.tvhome.ui.DepthBack
import com.ohav.tvhome.ui.DepthFront
import com.ohav.tvhome.ui.rememberWallpaper
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** System screensaver settings. Writing them needs WRITE_SECURE_SETTINGS / WRITE_SETTINGS, granted over adb. */
object Screensaver {
    private const val COMPONENTS = "screensaver_components"
    private const val GOOGLE = "com.google.android.apps.tv.dreamx/.service.Backdrop"

    fun component(context: Context) = ComponentName(context, ClockDream::class.java).flattenToShortString()

    fun canWrite(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun isOurs(context: Context) =
        Settings.Secure.getString(context.contentResolver, COMPONENTS)?.contains(ClockDream::class.java.simpleName) == true

    /** Makes ours (or the previous one) the system screensaver. False when we may not. */
    fun use(context: Context, ours: Boolean): Boolean = runCatching {
        val cr = context.contentResolver
        val prefs = Prefs(context)
        if (ours) {
            val current = Settings.Secure.getString(cr, COMPONENTS)
            if (current != null && !isOurs(context)) prefs.previousDream = current
            Settings.Secure.putString(cr, COMPONENTS, component(context))
            Settings.Secure.putInt(cr, "screensaver_enabled", 1)
            Settings.Secure.putInt(cr, "screensaver_activate_on_sleep", 1)
        } else {
            Settings.Secure.putString(cr, COMPONENTS, prefs.previousDream ?: GOOGLE)
        }
        prefs.screensaver = ours
        true
    }.getOrDefault(false)

    /** First run with the permission: ours, after 1 minute (the user's choice; Settings can change both). */
    fun setupOnce(context: Context) {
        val prefs = Prefs(context)
        if (prefs.screensaverSetup || !canWrite(context)) return
        if (use(context, true) && setMinutes(context, 1)) prefs.screensaverSetup = true
    }

    /** Minutes without a key press before the screensaver starts (the system's screen-off timeout). */
    fun minutes(context: Context): Int =
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 600_000) / 60_000

    fun setMinutes(context: Context, minutes: Int): Boolean = runCatching {
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, minutes * 60_000)
    }.getOrDefault(false)
}

/** Almost clear: a touch of shade at the edges (baked into the photo). */
private val SaverShade = Shade(
    0f to Color.Black.copy(alpha = 0.16f),
    0.2f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.34f),
)

/** One full cycle of the drift, in seconds. */
private const val BREATH_S = 36f
private const val WANDER_S = 160f

/**
 * How far each layer rises at the top of a breath: sky < clock < land (parallax). The land gains at most
 * 12dp on the clock, which tools/wallpapers/place.py allows for when it limits how much of each digit is hidden.
 */
private val SkyRise = 10.dp
private val ClockRise = 22.dp
private val LandRise = 34.dp
private val Wander = 8.dp

/**
 * The screensaver: the photo, the big clock behind its foreground, date and weather, and what's playing.
 * Everything drifts (20 frames a second is plenty for this slow motion): the land rises and settles against the
 * sky, and the clock wanders a little sideways; with the photo changing every minute nothing burns in.
 */
@Composable
fun ScreensaverScene() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val wp by rememberWallpaper(withMask = true, shade = SaverShade) { prefs.screensaverPhotoSeconds }
    val font = remember { ClockFont.of(prefs.clockFont) }
    val city = remember { cityOf(prefs.cityId) }
    var weather by remember { mutableStateOf(prefs.weather?.takeIf { it.cityId == city.id }) }
    val nowPlaying = remember { NowPlaying(context) }
    val t = remember { mutableFloatStateOf(0f) }
    val fade = remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        nowPlaying.start()
        onDispose { nowPlaying.stop() }
    }
    LaunchedEffect(Unit) {
        val w = weather
        if (w == null || System.currentTimeMillis() - w.at > WeatherApi.MAX_AGE_MS) {
            runCatching { WeatherApi.fetch(city) }.onSuccess { prefs.weather = it; weather = it }
        }
    }
    LaunchedEffect(Unit) {
        val start = SystemClock.uptimeMillis()
        while (true) {
            val s = (SystemClock.uptimeMillis() - start) / 1000f
            t.floatValue = s
            fade.floatValue = (s / 1.5f).coerceAtMost(1f)
            delay(if (s < 1.5f) 16 else 50)
        }
    }
    fun breath() = (1f - cos(2f * PI.toFloat() * t.floatValue / BREATH_S)) / 2f
    fun wander() = sin(2f * PI.toFloat() * t.floatValue / WANDER_S)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer {
                alpha = fade.floatValue
                compositingStrategy = if (fade.floatValue < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
    ) {
        DepthBack(wp, false, Modifier.graphicsLayer { translationY = -breath() * SkyRise.toPx() })
        BigClock(font) {
            translationX = wander() * Wander.toPx()
            translationY = -breath() * ClockRise.toPx()
        }
        DepthFront(wp, false, Modifier.graphicsLayer { translationY = -breath() * LandRise.toPx() })
        ClockInfo(weather, city) {
            translationX = wander() * Wander.toPx()
            translationY = -breath() * ClockRise.toPx()
        }
        val track = nowPlaying.track
        val shown by animateFloatAsState(if (track?.playing == true) 1f else 0f, tween(700), label = "np")
        if (track != null && shown > 0f) {
            SaverNowPlaying(
                track,
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 64.dp, bottom = 26.dp)
                    .graphicsLayer {
                        alpha = shown
                        translationX = wander() * (Wander / 2).toPx()
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    },
            )
        }
    }
}

/** What's playing (Spotify and the like): cover, title and artist on a dark glass card. */
@Composable
private fun SaverNowPlaying(track: Track, modifier: Modifier) {
    val context = LocalContext.current
    val artPx = with(LocalDensity.current) { 84.dp.roundToPx() }
    val art by produceState(track.art ?: track.artUri?.let { ImageLoader.cached(it, artPx) }, track.art, track.artUri) {
        if (track.art == null) track.artUri?.let { value = ImageLoader.load(context, it, artPx) }
    }
    Row(
        modifier
            .widthIn(max = 620.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.42f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1C202B))) {
            art?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Column(Modifier.padding(start = 20.dp, end = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4ADE80)))
                Text(
                    "  " + track.app.uppercase() + " · " + stringResource(R.string.now_playing),
                    color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    style = TextStyle(letterSpacing = 1.sp),
                )
            }
            Text(
                track.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
            )
            track.subtitle?.let {
                Text(it, color = Color.White.copy(alpha = 0.72f), fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
