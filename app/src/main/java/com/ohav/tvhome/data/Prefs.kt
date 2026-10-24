package com.ohav.tvhome.data

import android.content.Context

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
