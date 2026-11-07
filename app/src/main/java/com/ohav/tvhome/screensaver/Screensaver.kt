package com.ohav.tvhome.screensaver

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ohav.tvhome.data.Prefs
import com.ohav.tvhome.data.WeatherApi
import com.ohav.tvhome.data.cityOf
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

/** Almost clear: a touch of shade at the edges. */
private val Scrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.16f),
    0.2f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.28f),
)

/** One full cycle of the slow drift, in seconds. */
private const val BREATH_S = 90f
private const val WANDER_S = 240f

/**
 * The screensaver: the photo, the big clock behind its foreground, date and weather.
 * Everything drifts slowly (≈12 frames a second is plenty for sub-pixel motion): the land rises and settles
 * against the sky, and the clock wanders a little, so nothing stays burnt into one place.
 */
@Composable
fun ScreensaverScene() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val wp by rememberWallpaper(withMask = true) { prefs.wallpaperMinutes.takeIf { it in 1..5 } ?: 5 }
    val font = remember { ClockFont.of(prefs.clockFont) }
    val city = remember { cityOf(prefs.cityId) }
    var weather by remember { mutableStateOf(prefs.weather?.takeIf { it.cityId == city.id }) }
    val t = remember { mutableFloatStateOf(0f) }
    val fade = remember { mutableFloatStateOf(0f) }

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
            delay(if (s < 1.5f) 16 else 80)
        }
    }
    fun breath() = (1f - cos(2f * PI.toFloat() * t.floatValue / BREATH_S)) / 2f
    fun wanderX() = sin(2f * PI.toFloat() * t.floatValue / WANDER_S)
    fun wanderY() = sin(4f * PI.toFloat() * t.floatValue / WANDER_S)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer {
                alpha = fade.floatValue
                compositingStrategy = if (fade.floatValue < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
    ) {
        DepthBack(wp, false, Modifier.graphicsLayer { translationY = -breath() * 14.dp.toPx() }, Scrim)
        BigClock(font) {
            translationX = wanderX() * 28.dp.toPx()
            translationY = -breath() * 22.dp.toPx() + wanderY() * 8.dp.toPx()
        }
        DepthFront(wp, false, Modifier.graphicsLayer { translationY = -breath() * 44.dp.toPx() }, Scrim)
        ClockInfo(weather, city) {
            translationX = wanderX() * 28.dp.toPx()
            translationY = -breath() * 22.dp.toPx() + wanderY() * 8.dp.toPx()
        }
    }
}
