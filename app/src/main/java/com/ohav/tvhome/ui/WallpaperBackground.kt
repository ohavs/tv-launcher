package com.ohav.tvhome.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class Wallpaper(val file: String, val title: String, val credit: String)

/** Wikimedia Commons Featured Pictures (via github.com/mihirgupta0900/tv-home), see WALLPAPER-CREDITS.md. */
val WALLPAPERS = listOf(
    Wallpaper("01.jpg", "Lake Louise", "Chensiyuan · CC BY-SA 4.0"),
    Wallpaper("02.jpg", "Austnesfjorden, Lofoten", "Ximonic (Simo Räsänen) · CC BY-SA 3.0"),
    Wallpaper("03.jpg", "Baleia Franca, Brazil", "Ctseelig · CC BY-SA 4.0"),
    Wallpaper("04.jpg", "Lago do Cuniã, Amazon", "RodrigoErse · CC BY-SA 4.0"),
    Wallpaper("05.jpg", "Spullersee, Austria", "Wikimedia Commons · CC BY-SA 3.0 AT"),
    Wallpaper("06.jpg", "Paczków, Poland", "Jacek Halicki · CC BY-SA 3.0"),
    Wallpaper("07.jpg", "Stromboli", "Carsten Steger · CC BY-SA 4.0"),
    Wallpaper("08.jpg", "Flatirons Sunrise", "Tyler Cipriani · CC BY-SA 4.0"),
    Wallpaper("09.jpg", "Manneporte, Étretat", "Jörg Braukmann · CC BY-SA 4.0"),
    Wallpaper("10.jpg", "Kjeungskjær Lighthouse", "Gordon Leggett · CC BY-SA 4.0"),
    Wallpaper("11.jpg", "Eilean Donan Castle", "Diliff · CC BY-SA 3.0"),
    Wallpaper("12.jpg", "Dzukou Valley", "Samudra Bikash Hazarika · CC BY-SA 4.0"),
)

private const val ROTATE_MS = 5 * 60_000L
private const val FADE_MS = 1800

private object WallpaperState {
    var index = (WALLPAPERS.indices).random()
    var shownAt = 0L
}

/**
 * Full-screen photo that crossfades to the next one every few minutes while the home screen
 * is visible. Static between changes, so it costs nothing to draw.
 */
@Composable
fun WallpaperBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var image by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = System.currentTimeMillis()
                if (image != null && now - WallpaperState.shownAt >= ROTATE_MS) {
                    WallpaperState.index = (WallpaperState.index + 1) % WALLPAPERS.size
                }
                if (image == null || now - WallpaperState.shownAt >= ROTATE_MS) {
                    image = decode(context, WALLPAPERS[WallpaperState.index].file) ?: image
                    WallpaperState.shownAt = now
                }
                delay((WallpaperState.shownAt + ROTATE_MS - System.currentTimeMillis()).coerceAtLeast(1000))
            }
        }
    }

    Box(modifier.fillMaxSize().background(Color(0xFF0B0D12))) {
        Crossfade(targetState = image, animationSpec = tween(FADE_MS), label = "wallpaper") { img ->
            if (img != null) Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

private suspend fun decode(context: Context, file: String): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val screenW = context.resources.displayMetrics.widthPixels.coerceAtLeast(1280)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open("wallpapers/$file").use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= screenW) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.assets.open("wallpapers/$file").use { BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()
    }.getOrNull()
}
