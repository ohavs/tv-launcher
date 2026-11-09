package com.ohav.tvhome.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Shader
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * A depth wallpaper (built by tools/wallpapers/make.py): a background 1920x[LAYER_H] (the screen plus room for
 * parallax) and a mask, from row [fgTop] down, of the land that stands in front of the clock.
 */
data class Wallpaper(val id: String, val title: String, val credit: String, val fgTop: Int)

/** A vertical shade over the whole layer (stops from its top to its bottom). */
class Shade(vararg val stops: Pair<Float, Color>) {
    val brush = Brush.verticalGradient(*stops)

    fun paint(height: Int) = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            IntArray(stops.size) { stops[it].second.toArgb() }, FloatArray(stops.size) { stops[it].first },
            Shader.TileMode.CLAMP,
        )
    }
}

/**
 * Ready-to-draw pictures: [bg] is opaque with the shade baked in (drawn without blending), [fg] is the land in front
 * of the clock, already cut out and shaded, [Wallpaper.fgTop] rows down. One plain bitmap draw each, no layers.
 */
class WallpaperImages(val wallpaper: Wallpaper, val bg: ImageBitmap, val fg: ImageBitmap?)

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

    /**
     * Bakes the [shade] into the photo and cuts the foreground out of it with its mask, then moves both to
     * hardware bitmaps (one GPU copy each, nothing left on the heap).
     */
    suspend fun decode(context: Context, w: Wallpaper, withMask: Boolean, shade: Shade?): WallpaperImages? = withContext(Dispatchers.IO) {
        runCatching {
            val soft = BitmapFactory.Options().apply { inMutable = true }
            val bg = context.assets.open("wallpapers/${w.id}.webp").use { BitmapFactory.decodeStream(it, null, soft) }!!
            if (shade != null) Canvas(bg).drawRect(0f, 0f, bg.width.toFloat(), bg.height.toFloat(), shade.paint(bg.height))
            val fg = if (!withMask) null else {
                val mask = context.assets.open("wallpapers/${w.id}_fg.webp").use { BitmapFactory.decodeStream(it) }!!
                val top = w.fgTop.coerceIn(0, bg.height - 1)
                val out = Bitmap.createBitmap(bg.width, bg.height - top, Bitmap.Config.ARGB_8888)
                val dst = Rect(0, 0, out.width, out.height)
                Canvas(out).apply {
                    drawBitmap(bg, Rect(0, top, bg.width, bg.height), dst, null)
                    drawBitmap(mask, null, dst, Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
                }
                mask.recycle()
                out.copy(Bitmap.Config.HARDWARE, false).also { out.recycle() }
            }
            val hw = bg.copy(Bitmap.Config.HARDWARE, false)
            bg.recycle()
            WallpaperImages(w, hw.asImageBitmap(), fg?.asImageBitmap())
        }.getOrNull()
    }
}
