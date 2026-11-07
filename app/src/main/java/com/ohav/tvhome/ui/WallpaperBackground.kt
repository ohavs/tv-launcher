package com.ohav.tvhome.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ohav.tvhome.data.Prefs
import com.ohav.tvhome.data.WallpaperImages
import com.ohav.tvhome.data.Wallpapers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

private const val FADE_MS = 1800

/** Shows the next photo now (the home screen / screensaver picks it up when visible). */
fun nextWallpaper() = Wallpapers.next()

/**
 * The current photo, moving on every few minutes (user setting) while the screen is started. [withMask] also
 * decodes the foreground mask (depth layouts only).
 */
@Composable
fun rememberWallpaper(withMask: Boolean, minutes: () -> Int): State<WallpaperImages?> {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val image = remember { mutableStateOf<WallpaperImages?>(null) }
    LaunchedEffect(lifecycle, Wallpapers.skips, withMask) {
        val all = Wallpapers.all(context)
        if (all.isEmpty()) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val rotateMs = minutes() * 60_000L
                val now = System.currentTimeMillis()
                val skip = Wallpapers.skips != Wallpapers.handledSkips
                val due = Wallpapers.index < 0 || skip || (rotateMs > 0 && now - Wallpapers.shownAt >= rotateMs)
                if (Wallpapers.index < 0) Wallpapers.index = all.indices.random()
                else if (due) Wallpapers.index = (Wallpapers.index + 1) % all.size
                val current = image.value
                val want = all[Wallpapers.index]
                if (due || current == null || current.wallpaper != want || (withMask && current.mask == null)) {
                    image.value = Wallpapers.decode(context, want, withMask) ?: current
                    if (due) Wallpapers.shownAt = now
                    Wallpapers.handledSkips = Wallpapers.skips
                }
                if (rotateMs <= 0) awaitCancellation()
                delay((Wallpapers.shownAt + rotateMs - System.currentTimeMillis()).coerceAtLeast(1000))
            }
        }
    }
    return image
}

/** Full-screen photo (classic layout), crossfading on change. Static between changes, so it costs nothing to draw. */
@Composable
fun WallpaperBackground(modifier: Modifier = Modifier, quiet: Boolean = false) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val image = rememberWallpaper(withMask = false) { prefs.wallpaperMinutes }.value
    Box(modifier.fillMaxSize().background(Color(0xFF0B0D12))) {
        Crossfade(targetState = image, animationSpec = tween(if (quiet) 0 else FADE_MS), label = "wallpaper") { img ->
            if (img != null) Image(img.bg, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
