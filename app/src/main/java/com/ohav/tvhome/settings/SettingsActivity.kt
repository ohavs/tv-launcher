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
import com.ohav.tvhome.data.CameraPrefs
import com.ohav.tvhome.data.HomeLayout
import com.ohav.tvhome.data.RtspProbe
import com.ohav.tvhome.data.ScanResult
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
    var layout by remember { mutableStateOf(prefs.layout) }
    var cityId by remember { mutableStateOf(prefs.cityId) }
    var showCities by remember { mutableStateOf(false) }
    var mediaAccess by remember { mutableStateOf(false) }
    var ownYouTube by remember { mutableStateOf(prefs.ownYouTube) }
    var sponsorBlock by remember { mutableStateOf(prefs.sponsorBlock) }
    val cams = remember { CameraPrefs(context) }
    var camEnabled by remember { mutableStateOf(cams.enabled) }
    var camHost by remember { mutableStateOf(cams.host) }
    var camUser by remember { mutableStateOf(cams.user) }
    var camPass by remember { mutableStateOf(cams.pass) }
    var camFound by remember { mutableStateOf(cams.found) }
    var camHidden by remember { mutableStateOf(cams.hidden) }
    var camPrimary by remember { mutableStateOf(cams.primary) }
    var scanText by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
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
                val download = UpdateManager.download(context, r) { update = UpdateUi.Downloading(it) }
                update = UpdateUi.Installing
                UpdateManager.install(context, download)
            }.onFailure { update = UpdateUi.Failed(it.message ?: it.javaClass.simpleName) }
        }
    }

    fun scanCameras() {
        if (scanning) return
        scanning = true
        scanText = context.getString(R.string.camera_scanning)
        scope.launch {
            val r = RtspProbe.scan(camHost, camUser, camPass)
            if (r is ScanResult.Found) {
                cams.format = r.format
                cams.found = r.channels
                camFound = r.channels
            }
            scanText = when (r) {
                is ScanResult.Found -> context.getString(R.string.camera_found, r.channels.size)
                ScanResult.BadLogin -> context.getString(R.string.camera_bad_login)
                ScanResult.NoStreams -> context.getString(R.string.camera_none)
                is ScanResult.Unreachable -> context.getString(R.string.camera_unreachable, camHost)
            }
            scanning = false
        }
    }

    LifecycleResumeEffect(Unit) {
        camEnabled = cams.enabled
        camHost = cams.host
        camUser = cams.user
        camPass = cams.pass
        camFound = cams.found
        camHidden = cams.hidden
        camPrimary = cams.primary
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
                        stringResource(R.string.layout),
                        value = stringResource(if (layout == HomeLayout.Cinema) R.string.layout_cinema else R.string.layout_classic),
                    ) {
                        layout = HomeLayout.entries[(layout.ordinal + 1) % HomeLayout.entries.size]
                        prefs.layout = layout
                    }
                    MenuRow(
                        stringResource(R.string.tile_style),
                        value = stringResource(
                            when (tileStyle) {
                                TileStyle.Round -> R.string.tile_round
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

                    SectionTitle(stringResource(R.string.youtube))
                    MenuRow(stringResource(R.string.yt_own), value = stringResource(if (ownYouTube) R.string.on else R.string.off)) {
                        ownYouTube = !ownYouTube
                        prefs.ownYouTube = ownYouTube
                    }
                    if (ownYouTube) {
                        MenuRow(stringResource(R.string.yt_sponsorblock), value = stringResource(if (sponsorBlock) R.string.on else R.string.off)) {
                            sponsorBlock = !sponsorBlock
                            prefs.sponsorBlock = sponsorBlock
                        }
                    }

                    SectionTitle(stringResource(R.string.cameras))
                    MenuRow(stringResource(R.string.camera_on_dashboard), value = stringResource(if (camEnabled) R.string.on else R.string.off)) {
                        camEnabled = !camEnabled
                        cams.enabled = camEnabled
                    }
                    val notSet = stringResource(R.string.not_set)
                    TextRow(stringResource(R.string.dvr_address), camHost, notSet) {
                        camHost = it.trim()
                        cams.host = camHost
                    }
                    TextRow(stringResource(R.string.dvr_user), camUser, notSet) {
                        camUser = it.trim()
                        cams.user = camUser
                    }
                    TextRow(stringResource(R.string.dvr_password), camPass, notSet, password = true) {
                        val changed = it != camPass
                        camPass = it
                        cams.pass = it
                        if (changed && camUser.isNotEmpty()) scanCameras()
                    }
                    MenuRow(
                        stringResource(R.string.camera_scan),
                        value = scanText ?: if (camFound.isNotEmpty()) stringResource(R.string.camera_found, camFound.size) else null,
                    ) { scanCameras() }
                    val dashboardCam = camFound.firstOrNull { it == camPrimary && it !in camHidden } ?: camFound.firstOrNull { it !in camHidden }
                    camFound.forEach { ch ->
                        val label = when {
                            ch in camHidden -> R.string.camera_hidden
                            ch == dashboardCam -> R.string.camera_primary
                            else -> R.string.camera_shown
                        }
                        // Shown -> on the dashboard -> hidden -> shown.
                        MenuRow(stringResource(R.string.camera_n, ch), value = stringResource(label)) {
                            when {
                                ch in camHidden -> camHidden = camHidden - ch
                                ch == dashboardCam -> {
                                    camHidden = camHidden + ch
                                    if (camPrimary == ch) camPrimary = 0
                                }
                                else -> camPrimary = ch
                            }
                            cams.hidden = camHidden
                            cams.primary = camPrimary
                        }
                    }

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
