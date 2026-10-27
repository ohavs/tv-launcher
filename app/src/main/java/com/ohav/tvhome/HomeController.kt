package com.ohav.tvhome

import android.app.ActivityOptions
import android.content.ComponentName
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
import androidx.lifecycle.lifecycleScope
import com.ohav.tvhome.data.AppEntry
import com.ohav.tvhome.data.AppRepository
import com.ohav.tvhome.data.Prefs
import com.ohav.tvhome.data.ShelfItem
import com.ohav.tvhome.data.ShelfRepository
import com.ohav.tvhome.data.WeatherApi
import com.ohav.tvhome.data.WeatherNow
import com.ohav.tvhome.data.cityOf
import com.ohav.tvhome.media.NowPlaying
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class HomeController(private val activity: ComponentActivity) {
    val prefs = Prefs(activity)
    val repo = AppRepository(activity)
    val shelf = ShelfRepository(activity)
    val nowPlaying = NowPlaying(activity)
    val packageName: String = activity.packageName

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var tileStyle by mutableStateOf(prefs.tileStyle)
        private set
    var weather by mutableStateOf(prefs.weather)
        private set
    /** Google Home panel, when the system has it. */
    val smartHome: ComponentName? = activity.packageManager.resolveActivity(Intent(SMART_HOME_ACTION), 0)
        ?.activityInfo?.let { ComponentName(it.packageName, it.name) }

    /** Bumped when Home/Back is pressed on the home screen: back to the first app. */
    var homeSignal by mutableIntStateOf(0)
    /** Bumped each time the launcher becomes visible again (shelf refresh, entrance animation). */
    var resumeSignal by mutableIntStateOf(0)

    private var all: List<AppEntry> = emptyList()
    private var weatherJob: Job? = null

    suspend fun reload() {
        all = repo.load()
        apps = prefs.arrange(all)
    }

    fun onResume() {
        shelf.invalidate()
        resumeSignal++
        tileStyle = prefs.tileStyle
        if (all.isNotEmpty()) apps = prefs.arrange(all)
        refreshWeather()
    }

    private fun refreshWeather() {
        val city = cityOf(prefs.cityId)
        val cached = prefs.weather
        weather = cached?.takeIf { it.cityId == city.id }
        if (weatherJob?.isActive == true) return
        if (cached != null && cached.cityId == city.id && System.currentTimeMillis() - cached.at < WeatherApi.MAX_AGE_MS) return
        weatherJob = activity.lifecycleScope.launch {
            runCatching { WeatherApi.fetch(city) }.onSuccess { w: WeatherNow ->
                prefs.weather = w
                weather = w
            }
        }
    }

    fun launch(app: AppEntry, from: Rect) = start(app.launchIntent(), from)

    fun open(item: ShelfItem, from: Rect) {
        val intent = ShelfRepository.launchIntent(item) ?: return
        start(intent, from)
    }

    fun openSmartHome(from: Rect) {
        val cn = smartHome ?: return
        start(Intent(SMART_HOME_ACTION).setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), from)
    }

    fun openNowPlaying(from: Rect) {
        if (nowPlaying.open()) return
        val pkg = nowPlaying.track?.packageName ?: return
        apps.firstOrNull { it.packageName == pkg }?.let { launch(it, from) }
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

    fun systemSettings() = start(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), Rect.Zero)

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

    companion object {
        const val SMART_HOME_ACTION = "com.google.android.libraries.tv.smarthome.intent.action.OPEN_SMART_HOME"
    }
}
