package com.ohav.tvhome.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.DisplayMetrics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(
    val id: String,
    val packageName: String,
    val component: ComponentName,
    val category: String,
    val label: String,
    val banner: ImageBitmap?,
    /** Whole icon (adaptive layers composited), cropped to the visible square. */
    val icon: ImageBitmap?,
    /** Adaptive foreground alone, when it reads well on dark glass. */
    val glyph: ImageBitmap?,
    /** Android 13 themed-icon layer (tinted white for the monochrome style). */
    val mono: ImageBitmap?,
    /** Icon background colour: the tile fills with it on focus. */
    val brand: Color,
    val accent: Color,
) {
    fun launchIntent(): Intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        .setComponent(component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}

class AppRepository(private val context: Context) {
    private val pm = context.packageManager

    /** Stock launchers we never show as tiles. */
    private val excluded = setOf(
        "com.google.android.apps.tv.launcherx",
        "com.google.android.tvlauncher",
        "com.google.android.leanbacklauncher",
    )

    suspend fun load(): List<AppEntry> = withContext(Dispatchers.IO) {
        val out = ArrayList<AppEntry>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val tvPackages = out.map { it.packageName }.toSet()
            for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName in excluded) continue
                // Phone-style entry points only for packages without a TV one.
                if (category == Intent.CATEGORY_LAUNCHER && ai.packageName in tvPackages) continue
                out += runCatching { entryFor(ai, category) }.getOrNull() ?: continue
            }
        }
        out.distinctBy { it.id }
    }

    private fun entryFor(ai: ActivityInfo, category: String): AppEntry {
        val cn = ComponentName(ai.packageName, ai.name)
        val label = ai.loadLabel(pm).toString().trim()
        val banner = ai.loadBanner(pm)?.let { renderBanner(it) }
        val icons = iconsFor(ai)
        val accent = accentOf(banner ?: icons.full)
        return AppEntry(
            id = cn.flattenToShortString(),
            packageName = ai.packageName,
            component = cn,
            category = category,
            label = label,
            banner = banner?.asImageBitmap(),
            icon = icons.full?.asImageBitmap(),
            glyph = icons.glyph?.asImageBitmap(),
            mono = icons.mono?.asImageBitmap(),
            brand = icons.brand ?: accent,
            accent = accent,
        )
    }

    private class Icons(val full: Bitmap?, val glyph: Bitmap?, val mono: Bitmap?, val brand: Color?)

    /** High-density icon, split into adaptive layers when it has them. */
    private fun iconsFor(ai: ActivityInfo): Icons {
        val d: Drawable = runCatching {
            pm.getResourcesForApplication(ai.applicationInfo)
                .getDrawableForDensity(ai.iconResource, DisplayMetrics.DENSITY_XXXHIGH, null)
        }.getOrNull() ?: ai.loadIcon(pm) ?: return Icons(null, null, null, null)
        if (d !is AdaptiveIconDrawable) {
            val full = d.toBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
            val keyed = keyOut(full)
            return Icons(full, keyed?.first?.takeIf { readsOnDark(it) }?.let { trim(it) }, null, keyed?.second)
        }
        val bg = layer(d.background)
        val fg = layer(d.foreground)
        val mono = if (Build.VERSION.SDK_INT >= 33) layer(d.monochrome) else null
        val full = createBitmap(ICON_PX, ICON_PX).also { b ->
            val c = Canvas(b)
            bg?.let { c.drawBitmap(it, 0f, 0f, null) }
            fg?.let { c.drawBitmap(it, 0f, 0f, null) }
        }
        val glyph = fg?.takeIf { readsOnDark(it) } ?: keyOut(full)?.first?.takeIf { readsOnDark(it) }
        return Icons(full, glyph?.let { trim(it) }, mono?.let { trim(it) }, bg?.let { averageColor(it) })
    }

    /** Renders an adaptive layer and crops it to the 72/108 visible area. */
    private fun layer(d: Drawable?): Bitmap? {
        d ?: return null
        val full = createBitmap(LAYER_PX, LAYER_PX)
        d.setBounds(0, 0, LAYER_PX, LAYER_PX)
        d.draw(Canvas(full))
        val inset = (LAYER_PX - ICON_PX) / 2
        return Bitmap.createBitmap(full, inset, inset, ICON_PX, ICON_PX)
    }

    /** Crops to the visible logo so every logo can be drawn at the same optical size. */
    private fun trim(b: Bitmap): Bitmap {
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        var l = w; var t = h; var r = -1; var bt = -1
        for (y in 0 until h) for (x in 0 until w) {
            if (px[y * w + x] ushr 24 > 24) {
                if (x < l) l = x
                if (x > r) r = x
                if (y < t) t = y
                if (y > bt) bt = y
            }
        }
        if (r < l || bt < t) return b
        return Bitmap.createBitmap(b, l, t, r - l + 1, bt - t + 1)
    }

    /** A logo works on dark glass when it has enough visible pixels that aren't too dark. */
    private fun readsOnDark(fg: Bitmap): Boolean {
        val small = fg.scale(32, 32, true)
        var lum = 0.0
        var n = 0
        for (x in 0 until 32) for (y in 0 until 32) {
            val c = small.getPixel(x, y)
            if (android.graphics.Color.alpha(c) < 100) continue
            lum += ColorUtils.calculateLuminance(c)
            n++
        }
        return n >= 12 && lum / n > 0.10
    }

    /**
     * For icons whose logo sits on a flat plate (e.g. red logo on white): makes the plate
     * transparent so the logo alone sits on the glass. Returns the logo and the plate colour.
     */
    private fun keyOut(full: Bitmap): Pair<Bitmap, Color>? {
        val w = full.width
        val px = IntArray(w * w)
        full.getPixels(px, 0, w, 0, 0, w, w)
        val inset = w / 8
        val samples = buildList {
            for (i in inset until w - inset step w / 12) {
                add(px[inset * w + i]); add(px[(w - 1 - inset) * w + i])
                add(px[i * w + inset]); add(px[i * w + w - 1 - inset])
            }
        }
        if (samples.any { android.graphics.Color.alpha(it) < 250 }) return null
        val r = samples.sumOf { android.graphics.Color.red(it) } / samples.size
        val g = samples.sumOf { android.graphics.Color.green(it) } / samples.size
        val b = samples.sumOf { android.graphics.Color.blue(it) } / samples.size
        fun dist(c: Int) = maxOf(
            kotlin.math.abs(android.graphics.Color.red(c) - r),
            kotlin.math.abs(android.graphics.Color.green(c) - g),
            kotlin.math.abs(android.graphics.Color.blue(c) - b),
        )
        // A plate is one flat colour; gradients and photos are left alone.
        if (samples.count { dist(it) > 28 } > samples.size / 8) return null
        for (i in px.indices) {
            val c = px[i]
            val k = ((dist(c) - 26) / 40f).coerceIn(0f, 1f)
            px[i] = (c and 0x00FFFFFF) or ((android.graphics.Color.alpha(c) * k).toInt() shl 24)
        }
        return Bitmap.createBitmap(px, w, w, Bitmap.Config.ARGB_8888) to Color(r, g, b)
    }

    private fun averageColor(bmp: Bitmap): Color {
        val small = bmp.scale(8, 8, true)
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (x in 0 until 8) for (y in 0 until 8) {
            val c = small.getPixel(x, y)
            if (android.graphics.Color.alpha(c) < 128) continue
            r += android.graphics.Color.red(c); g += android.graphics.Color.green(c); b += android.graphics.Color.blue(c); n++
        }
        return if (n == 0L) Color(0xFF2A2F3A) else Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    private fun renderBanner(d: Drawable): Bitmap = d.toBitmap(BANNER_W, BANNER_H, Bitmap.Config.ARGB_8888)

    /** Average colour of the artwork, nudged towards a rich, mid-bright tone for glows. */
    private fun accentOf(bmp: Bitmap?): Color {
        if (bmp == null) return Color(0xFF3A4A6B)
        val small = bmp.scale(12, 12, true)
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (x in 0 until small.width) for (y in 0 until small.height) {
            val c = small.getPixel(x, y)
            if (android.graphics.Color.alpha(c) < 128) continue
            r += android.graphics.Color.red(c); g += android.graphics.Color.green(c); b += android.graphics.Color.blue(c); n++
        }
        if (n == 0L) return Color(0xFF3A4A6B)
        val hsl = FloatArray(3)
        ColorUtils.RGBToHSL((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsl)
        hsl[1] = (hsl[1] * 1.25f).coerceIn(0.25f, 0.85f)
        hsl[2] = hsl[2].coerceIn(0.30f, 0.55f)
        return Color(ColorUtils.HSLToColor(hsl))
    }

    /** Returns true when installed packages changed since [sequence]; updates the stored number. */
    fun packagesChanged(prefs: Prefs): Boolean {
        val changed = pm.getChangedPackages(prefs.packageSequence) ?: return false
        prefs.packageSequence = changed.sequenceNumber
        return true
    }

    fun isInstalled(pkg: String): Boolean = try {
        pm.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    companion object {
        const val BANNER_W = 480
        const val BANNER_H = 270
        private const val LAYER_PX = 216
        private const val ICON_PX = 144
    }
}
