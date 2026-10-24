package com.ohav.tvhome

import android.app.ActivityOptions
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.ohav.tvhome.data.AppEntry
import com.ohav.tvhome.data.AppRepository
import com.ohav.tvhome.data.Prefs
import com.ohav.tvhome.data.ShelfItem
import com.ohav.tvhome.data.ShelfRepository

class HomeController(private val activity: ComponentActivity) {
    val prefs = Prefs(activity)
    val repo = AppRepository(activity)
    val shelf = ShelfRepository(activity)

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var videoEnabled by mutableStateOf(prefs.videoEnabled)
    /** Bumped when Home/Back is pressed on the home screen: scroll to top. */
    var homeSignal by mutableIntStateOf(0)
    /** Bumped each time the launcher becomes visible again (shelf refresh). */
    var resumeSignal by mutableIntStateOf(0)

    private var all: List<AppEntry> = emptyList()

    suspend fun reload() {
        all = repo.load()
        apps = prefs.arrange(all)
    }

    fun onResume() {
        videoEnabled = prefs.videoEnabled
        shelf.invalidate()
        resumeSignal++
        if (all.isNotEmpty()) apps = prefs.arrange(all)
    }

    fun launch(app: AppEntry, from: Rect) = start(app.launchIntent(), from)

    fun open(item: ShelfItem, from: Rect) {
        val intent = ShelfRepository.launchIntent(item) ?: return
        start(intent, from)
    }

    fun hide(app: AppEntry) {
        prefs.hidden = prefs.hidden + app.id
        apps = prefs.arrange(all)
    }

    fun appInfo(app: AppEntry) = start(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        Rect.Zero,
    )

    /** Moves [id] by [delta] positions; returns false at the edges. */
    fun move(id: String, delta: Int): Boolean {
        val list = apps.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in list.indices) return false
        list.add(to, list.removeAt(from))
        apps = list
        return true
    }

    fun saveOrder() = prefs.saveOrder(apps)

    private fun start(intent: Intent, from: Rect) {
        val opts = if (from.width > 0f) ActivityOptions.makeScaleUpAnimation(
            activity.window.decorView, from.left.toInt(), from.top.toInt(), from.width.toInt(), from.height.toInt(),
        ).toBundle() else null
        try {
            activity.startActivity(intent, opts)
        } catch (e: Exception) {
            Toast.makeText(activity, e.localizedMessage ?: e.javaClass.simpleName, Toast.LENGTH_SHORT).show()
        }
    }
}
