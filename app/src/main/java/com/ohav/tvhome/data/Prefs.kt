package com.ohav.tvhome.data

import android.content.Context

/** Remote buttons we can remap. Long press on Netflix opens the control center. */
enum class RemoteKey(val defaultPackage: String) {
    YouTube("com.google.android.youtube.tv"),
    Netflix("com.netflix.ninja"),
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("home", Context.MODE_PRIVATE)

    var order: List<String>
        get() = sp.getString("order", "")!!.split('\n').filter { it.isNotEmpty() }
        set(v) = sp.edit().putString("order", v.joinToString("\n")).apply()

    var hidden: Set<String>
        get() = sp.getStringSet("hidden", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("hidden", v).apply()

    var packageSequence: Int
        get() = sp.getInt("pkgSeq", 0)
        set(v) = sp.edit().putInt("pkgSeq", v).apply()

    var tileStyle: TileStyle
        get() = runCatching { TileStyle.valueOf(sp.getString("tileStyle", null)!!) }.getOrDefault(TileStyle.Banner)
        set(v) = sp.edit().putString("tileStyle", v.name).apply()

    var layout: HomeLayout
        get() = runCatching { HomeLayout.valueOf(sp.getString("layout", null)!!) }.getOrDefault(HomeLayout.Clock)
        set(v) = sp.edit().putString("layout", v.name).apply()

    var cityId: String
        get() = sp.getString("city", CITIES.first().id)!!
        set(v) = sp.edit().putString("city", v).apply()

    /** YouTube tile, YouTube content and the remote's YouTube button open our ad-free player. */
    var ownYouTube: Boolean
        get() = sp.getBoolean("ownYouTube", true)
        set(v) = sp.edit().putBoolean("ownYouTube", v).apply()

    var sponsorBlock: Boolean
        get() = sp.getBoolean("sponsorBlock", true)
        set(v) = sp.edit().putBoolean("sponsorBlock", v).apply()

    var weather: WeatherNow?
        get() = sp.getString("weather", null)?.let { WeatherNow.fromJson(it) }
        set(v) = sp.edit().putString("weather", v?.toJson()).apply()

    /** Fewer animations: no entrance, tilt, launch zoom or wallpaper crossfade. */
    var quiet: Boolean
        get() = sp.getBoolean(QUIET, false)
        set(v) = sp.edit().putBoolean(QUIET, v).apply()

    /** Minutes between wallpapers; 0 = keep the current one. */
    var wallpaperMinutes: Int
        get() = sp.getInt("wallpaperMin", 5)
        set(v) = sp.edit().putInt("wallpaperMin", v).apply()

    /** App a remote button opens (short press). */
    fun keyTarget(key: RemoteKey): String = sp.getString("key_${key.name}", null) ?: key.defaultPackage
    fun setKeyTarget(key: RemoteKey, pkg: String) = sp.edit().putString("key_${key.name}", pkg).apply()

    /** Calls [onChange] with the key of every changed setting; keep the returned listener referenced. */
    fun listen(onChange: (String?) -> Unit) =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key -> onChange(key) }
            .also { sp.registerOnSharedPreferenceChangeListener(it) }

    /**
     * Applies the saved order; apps never seen before are slotted in by [defaultRank]
     * among the new ones and appended after the saved ones.
     */
    fun arrange(apps: List<AppEntry>): List<AppEntry> {
        val hidden = hidden
        val visible = apps.filter { it.id !in hidden }
        val saved = order
        val byId = visible.associateBy { it.id }
        val known = saved.mapNotNull { byId[it] }
        val fresh = visible.filter { it.id !in saved }.sortedWith(compareBy({ defaultRank(it) }, { it.label.lowercase() }))
        val result = known + fresh
        // Keep ids that are hidden or uninstalled so they return to their old spot.
        if (fresh.isNotEmpty()) order = result.map { it.id } + saved.filter { it !in byId }
        return result
    }

    /** Saves a new visible order, keeping hidden/uninstalled ids at the end. */
    fun saveOrder(visible: List<AppEntry>) {
        val ids = visible.map { it.id }
        order = ids + order.filter { it !in ids }
    }

    private fun defaultRank(app: AppEntry): Int {
        val idx = FAVORITES.indexOf(app.packageName)
        if (idx >= 0) return idx
        return when (app.packageName) {
            "com.android.tv.settings", "com.android.vending" -> 900
            "com.ohav.tvhome" -> 999
            else -> 500
        }
    }

    companion object {
        const val QUIET = "quiet"
        val WALLPAPER_CHOICES = listOf(1, 5, 15, 30, 60, 0)

        private val FAVORITES = listOf(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.spotify.tv.android",
            "com.stremio.one",
            "com.teamsmart.videomanager.tv",
            "com.disneyplus.mea",
            "com.amazon.amazonvideo.livingroom",
            "com.apple.atve.androidtv.appletv",
            "com.cellcom.cellcom_tv",
            "com.google.android.youtube.tvmusic",
        )
    }
}
