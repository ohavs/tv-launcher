package com.ohav.tvhome.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import com.ohav.tvhome.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(
    val id: String,
    val packageName: String,
    val component: ComponentName,
    val category: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    val accent: Color,
    /** Set for system shortcuts that open by action rather than as a launcher entry. */
    val action: String? = null,
) {
    fun launchIntent(): Intent = (if (action != null) Intent(action) else Intent(Intent.ACTION_MAIN).addCategory(category))
        .setComponent(component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}

/** System screens that only the stock launcher used to link to. */
private data class Shortcut(val id: String, val action: String, val label: String, @DrawableRes val banner: Int)

private val SHORTCUTS = listOf(
    Shortcut("shortcut:google-home", "com.google.android.libraries.tv.smarthome.intent.action.OPEN_SMART_HOME", "Google Home", R.drawable.banner_google_home),
)

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
        for (sc in SHORTCUTS) {
            val ai = pm.resolveActivity(Intent(sc.action), 0)?.activityInfo ?: continue
            val banner = renderBanner(context.getDrawable(sc.banner)!!)
            out += AppEntry(
                id = sc.id,
                packageName = ai.packageName,
                component = ComponentName(ai.packageName, ai.name),
                category = Intent.CATEGORY_DEFAULT,
                label = sc.label,
                banner = banner.asImageBitmap(),
                icon = null,
                accent = accentOf(banner),
                action = sc.action,
            )
        }
        out.distinctBy { it.id }
    }

    private fun entryFor(ai: android.content.pm.ActivityInfo, category: String): AppEntry {
        val cn = ComponentName(ai.packageName, ai.name)
        val label = ai.loadLabel(pm).toString().trim()
        val bannerDrawable: Drawable? = ai.loadBanner(pm)
        val banner = bannerDrawable?.let { renderBanner(it) }
        val icon = if (banner == null) ai.loadIcon(pm)?.toBitmap(192, 192, Bitmap.Config.ARGB_8888) else null
        val accent = accentOf(banner ?: icon)
        return AppEntry(
            id = cn.flattenToShortString(),
            packageName = ai.packageName,
            component = cn,
            category = category,
            label = label,
            banner = banner?.asImageBitmap(),
            icon = icon?.asImageBitmap(),
            accent = accent,
        )
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
    }
}
