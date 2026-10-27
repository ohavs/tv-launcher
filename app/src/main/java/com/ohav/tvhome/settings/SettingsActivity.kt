package com.ohav.tvhome.settings

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ohav.tvhome.BuildConfig
import com.ohav.tvhome.R
import com.ohav.tvhome.HomeKeyService
import com.ohav.tvhome.ui.WALLPAPERS
import com.ohav.tvhome.data.AppRepository
import com.ohav.tvhome.data.CITIES
import com.ohav.tvhome.data.Prefs
import com.ohav.tvhome.data.TileStyle
import com.ohav.tvhome.data.cityOf
import com.ohav.tvhome.media.MediaListener
import com.ohav.tvhome.ui.MenuColumn
import com.ohav.tvhome.ui.MenuRow
import com.ohav.tvhome.ui.SectionTitle
import com.ohav.tvhome.update.Release
import com.ohav.tvhome.update.UpdateManager
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SettingsScreen() }
    }
}

private sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data class UpToDate(val version: String) : UpdateUi
    data class Available(val release: Release) : UpdateUi
    data class Downloading(val pct: Int) : UpdateUi
    data object Installing : UpdateUi
    data object NeedsPermission : UpdateUi
    data class Failed(val msg: String) : UpdateUi
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Prefs(context) }
    var homeKey by remember { mutableStateOf(HomeKeyService.isEnabled(context)) }
    var hidden by remember { mutableStateOf(prefs.hidden) }
    var update by remember { mutableStateOf<UpdateUi>(UpdateUi.Idle) }
    var showCredits by remember { mutableStateOf(false) }
    var tileStyle by remember { mutableStateOf(prefs.tileStyle) }
    var cityId by remember { mutableStateOf(prefs.cityId) }
    var showCities by remember { mutableStateOf(false) }
    var mediaAccess by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val allApps by produceState(emptyList(), hidden) { value = AppRepository(context).load() }

    fun check() {
        update = UpdateUi.Checking
        scope.launch {
            update = runCatching { UpdateManager.latest() }.fold(
                onSuccess = { if (UpdateManager.isNewer(it)) UpdateUi.Available(it) else UpdateUi.UpToDate(BuildConfig.VERSION_NAME) },
                onFailure = { UpdateUi.Failed(it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    fun install(r: Release) {
        if (!UpdateManager.canInstall(context)) {
            update = UpdateUi.NeedsPermission
            UpdateManager.requestInstallPermission(context)
            return
        }
        scope.launch {
            runCatching {
                update = UpdateUi.Downloading(0)
                val apk = UpdateManager.download(context, r) { update = UpdateUi.Downloading(it) }
                update = UpdateUi.Installing
                UpdateManager.install(context, apk)
            }.onFailure { update = UpdateUi.Failed(it.message ?: it.javaClass.simpleName) }
        }
    }

    LifecycleResumeEffect(Unit) {
        homeKey = HomeKeyService.isEnabled(context)
        mediaAccess = context.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(ComponentName(context, MediaListener::class.java))
        onPauseOrDispose { }
    }

    LaunchedEffect(Unit) {
        runCatching { first.requestFocus() }
        check()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF1B2735), Color(0xFF090A0F))))
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 48.dp)) {
            Column(Modifier.width(300.dp).fillMaxHeight()) {
                Text(stringResource(R.string.settings_title), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.version, BuildConfig.VERSION_NAME),
                    color = Color.White.copy(alpha = 0.5f), fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                MenuColumn {
                    val updateText = when (val u = update) {
                        UpdateUi.Idle -> null
                        UpdateUi.Checking -> stringResource(R.string.checking)
                        is UpdateUi.UpToDate -> stringResource(R.string.up_to_date, u.version)
                        is UpdateUi.Available -> stringResource(R.string.update_available, u.release.versionName)
                        is UpdateUi.Downloading -> stringResource(R.string.downloading, u.pct)
                        UpdateUi.Installing -> stringResource(R.string.installing)
                        UpdateUi.NeedsPermission -> stringResource(R.string.allow_unknown)
                        is UpdateUi.Failed -> stringResource(R.string.update_error, u.msg)
                    }
                    MenuRow(stringResource(R.string.check_updates), Modifier.focusRequester(first), value = updateText) {
                        when (val u = update) {
                            is UpdateUi.Available -> install(u.release)
                            is UpdateUi.Downloading, UpdateUi.Installing, UpdateUi.Checking -> Unit
                            else -> check()
                        }
                    }
                    MenuRow(
                        stringResource(R.string.home_key),
                        value = stringResource(if (homeKey) R.string.on else R.string.off),
                    ) {
                        runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    }
                    MenuRow(stringResource(R.string.system_settings)) {
                        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                    }

                    SectionTitle(stringResource(R.string.appearance))
                    MenuRow(
                        stringResource(R.string.tile_style),
                        value = stringResource(
                            when (tileStyle) {
                                TileStyle.Color -> R.string.tile_color
                                TileStyle.Mono -> R.string.tile_mono
                                TileStyle.Banner -> R.string.tile_banner
                            }
                        ),
                    ) {
                        tileStyle = TileStyle.entries[(tileStyle.ordinal + 1) % TileStyle.entries.size]
                        prefs.tileStyle = tileStyle
                    }
                    MenuRow(stringResource(R.string.weather_city), value = cityOf(cityId).name + if (showCities) "  ▲" else "  ▼") {
                        showCities = !showCities
                    }
                    if (showCities) {
                        CITIES.forEach { city ->
                            MenuRow(city.name, value = if (city.id == cityId) "✓" else null) {
                                cityId = city.id
                                prefs.cityId = city.id
                                showCities = false
                            }
                        }
                    }
                    MenuRow(
                        stringResource(R.string.media_access),
                        value = stringResource(if (mediaAccess) R.string.allowed else R.string.not_allowed),
                    ) { }

                    SectionTitle(stringResource(R.string.hidden_apps))
                    val hiddenApps = allApps.filter { it.id in hidden }
                    if (hiddenApps.isEmpty()) {
                        Text(stringResource(R.string.hidden_none), color = Color.White.copy(alpha = 0.45f), fontSize = 17.sp, modifier = Modifier.padding(start = 6.dp))
                    }
                    hiddenApps.forEach { app ->
                        MenuRow(app.label, value = stringResource(R.string.unhide)) {
                            hidden = hidden - app.id
                            prefs.hidden = hidden
                        }
                    }

                    SectionTitle(stringResource(R.string.credits))
                    MenuRow(stringResource(R.string.credits), value = if (showCredits) "▲" else "▼") { showCredits = !showCredits }
                    if (showCredits) {
                        WALLPAPERS.forEach {
                            Text("${it.title} — ${it.credit}", color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, modifier = Modifier.padding(start = 6.dp))
                        }
                        Text("Wikimedia Commons", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp, bottom = 40.dp))
                    }
                }
            }
        }
    }
}
