package com.ohav.tvhome.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * A depth wallpaper (built by tools/wallpapers/make.py): a background 1920x[LAYER_H] (the screen plus room for
 * parallax) and a mask, from row [fgTop] down, of the land that stands in front of the clock.
 */
data class Wallpaper(val id: String, val title: String, val credit: String, val fgTop: Int)

/** Decoded pictures; the foreground is cut out of [bg] with [mask] when drawn. */
class WallpaperImages(val wallpaper: Wallpaper, val bg: ImageBitmap, val mask: ImageBitmap?)

object Wallpapers {
    const val SRC_W = 1920
    const val LAYER_H = 1360

    private var cache: List<Wallpaper>? = null

    fun all(context: Context): List<Wallpaper> = cache ?: runCatching {
        val a = JSONArray(context.assets.open("wallpapers/index.json").bufferedReader().readText())
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Wallpaper(o.getString("id"), o.getString("title"), o.getString("credit"), o.getInt("fgTop"))
        }
    }.getOrDefault(emptyList()).also { cache = it }

    /** Shared by the home screen and the screensaver, so both show the same photo. */
    var index = -1
    var shownAt = 0L
    /** Bumped by "next wallpaper" in the control center. */
    var skips by mutableIntStateOf(0)
    var handledSkips = 0

    fun next() {
        skips++
    }

    /** Hardware bitmaps: one GPU copy each, nothing on the heap. */
    suspend fun decode(context: Context, w: Wallpaper, withMask: Boolean): WallpaperImages? = withContext(Dispatchers.IO) {
        runCatching {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.HARDWARE }
            val bg = context.assets.open("wallpapers/${w.id}.webp").use { BitmapFactory.decodeStream(it, null, opts) }!!
            val mask = if (!withMask) null else
                context.assets.open("wallpapers/${w.id}_fg.webp").use { BitmapFactory.decodeStream(it, null, opts) }
            WallpaperImages(w, bg.asImageBitmap(), mask?.asImageBitmap())
        }.getOrNull()
    }
}
