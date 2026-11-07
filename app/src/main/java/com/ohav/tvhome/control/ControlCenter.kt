package com.ohav.tvhome.control

import android.content.Intent
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohav.tvhome.HomeKeyService
import com.ohav.tvhome.R
import com.ohav.tvhome.camera.CameraGridActivity
import com.ohav.tvhome.data.CameraPrefs
import com.ohav.tvhome.data.Prefs
import com.ohav.tvhome.media.NowPlaying
import com.ohav.tvhome.settings.SettingsActivity
import com.ohav.tvhome.ui.CircleButton
import com.ohav.tvhome.ui.GlassButton
import com.ohav.tvhome.ui.Glyph
import com.ohav.tvhome.ui.nextWallpaper
import com.ohav.tvhome.ui.selectKeys
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date

/**
 * Side panel over any app: what's playing, sleep timer, wallpaper, cameras and quick switches.
 * Opened by a long press on the remote's Netflix button or from the dashboard.
 */
class ControlCenter(private val service: HomeKeyService) {
    private var overlay: ComposeOverlay? = null
    val isOpen get() = overlay != null

    fun toggle() = if (isOpen) close() else open()

    fun open() {
        if (overlay != null) return
        val width = (PANEL_WIDTH.value * service.resources.displayMetrics.density).toInt()
        val params = ComposeOverlay.params(
            width, WindowManager.LayoutParams.MATCH_PARENT, ComposeOverlay.endGravity(service), focusable = true, dim = 0.45f,
        )
        overlay = ComposeOverlay(service, params) { Panel(service, ::close, ::launch) }.also { it.show() }
    }

    fun close() {
        overlay?.dismiss()
        overlay = null
    }

    private fun launch(intent: Intent) {
        close()
        runCatching { service.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    companion object {
        val PANEL_WIDTH = 340.dp
    }
}

private val EaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val RowShape = RoundedCornerShape(12.dp)

@Composable
private fun Panel(service: HomeKeyService, close: () -> Unit, launch: (Intent) -> Unit) {
    val prefs = remember { Prefs(service) }
    val nowPlaying = remember { NowPlaying(service) }
    DisposableEffect(Unit) {
        nowPlaying.start()
        onDispose { nowPlaying.stop() }
    }
    val track = nowPlaying.track
    val cameras = remember { CameraPrefs(service).config() }
    var quiet by remember { mutableStateOf(prefs.quiet) }
    var ownYouTube by remember { mutableStateOf(prefs.ownYouTube) }
    var sponsorBlock by remember { mutableStateOf(prefs.sponsorBlock) }
    val first = remember { FocusRequester() }
    val slide = remember { Animatable(if (prefs.quiet) 1f else 0f) }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val windowInfo = LocalWindowInfo.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        withTimeoutOrNull(600) { snapshotFlow { windowInfo.isWindowFocused }.first { it } }
        runCatching { first.requestFocus() }
        slide.animateTo(1f, tween(240, easing = EaseOut))
    }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000 - SystemClock.elapsedRealtime() % 1000)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = (1f - slide.value) * size.width * (if (isRtl) -1f else 1f)
                alpha = 0.4f + 0.6f * slide.value
            }
            .background(Color(0xF2111418))
            .onPreviewKeyEvent { ev ->
                if (ev.key != Key.Back && ev.key != Key.Menu) return@onPreviewKeyEvent false
                if (ev.type == KeyEventType.KeyUp) close()
                true
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.cc_title), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(DateFormat.getTimeFormat(service).format(Date(now)), color = Color.White.copy(alpha = 0.7f), fontSize = 19.sp)
        }

        if (track != null) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.1f))) {
                    track.art?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(track.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(track.subtitle, track.app).joinToString(" · "),
                        color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Media controls read left to right in every language.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextCircle("−10", remember { FocusRequester() }) { nowPlaying.seekBy(-10_000) }
                    CircleButton(if (track.playing) Glyph.Pause else Glyph.Play, first, {}, { nowPlaying.playPause() }, size = 50.dp)
                    TextCircle("+10", remember { FocusRequester() }) { nowPlaying.seekBy(10_000) }
                }
            }
        }

        // Reads the ticking clock, so the countdown refreshes every second.
        val left = if (now > 0L) SleepTimer.remainingMs() else 0L
        Label(
            stringResource(R.string.sleep_timer),
            when {
                SleepTimer.endsAt == 0L -> stringResource(R.string.sleep_off)
                left >= 60_000 -> stringResource(R.string.sleep_in_min, ((left + 59_999) / 60_000).toInt())
                else -> stringResource(R.string.sleep_in_sec, (left / 1000).toInt())
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SleepTimer.PRESETS.forEach { m ->
                val selected = if (SleepTimer.endsAt == 0L) m == 0 else m == SleepTimer.preset
                Chip(
                    text = if (m == 0) stringResource(R.string.sleep_off) else "$m",
                    selected = selected,
                    modifier = if (track == null && selected) Modifier.focusRequester(first) else Modifier,
                ) { SleepTimer.set(m) }
            }
        }

        Spacer(Modifier.height(4.dp))
        PanelRow(stringResource(R.string.next_wallpaper)) { nextWallpaper() }
        if (cameras != null) PanelRow(stringResource(R.string.all_cameras)) { launch(CameraGridActivity.intent(service, cameras)) }
        PanelRow(stringResource(R.string.quiet_mode), stringResource(if (quiet) R.string.on else R.string.off)) {
            quiet = !quiet
            prefs.quiet = quiet
        }
        PanelRow(stringResource(R.string.yt_own_short), stringResource(if (ownYouTube) R.string.on else R.string.off)) {
            ownYouTube = !ownYouTube
            prefs.ownYouTube = ownYouTube
        }
        if (ownYouTube) {
            PanelRow(stringResource(R.string.sponsorblock_short), stringResource(if (sponsorBlock) R.string.on else R.string.off)) {
                sponsorBlock = !sponsorBlock
                prefs.sponsorBlock = sponsorBlock
            }
        }
        PanelRow(stringResource(R.string.home_settings)) { launch(Intent(service, SettingsActivity::class.java)) }
    }
}

@Composable
private fun Label(text: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text(value, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
    }
}

@Composable
private fun TextCircle(text: String, requester: FocusRequester, onClick: () -> Unit) {
    GlassButton(requester, {}, { _: Rect -> onClick() }, Modifier.size(42.dp), CircleShape, focusScale = 1.14f, solidFocus = true) { f ->
        Text(
            text, color = lerp(Color.White, Color(0xFF111318), f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** Compact, focusable row of the panel: white with dark text when focused. */
@Composable
private fun PanelRow(title: String, value: String? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (focused) Color.White else Color.White.copy(alpha = 0.07f), label = "bg")
    val fg by animateColorAsState(if (focused) Color(0xFF111111) else Color.White, label = "fg")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .selectKeys(onPressed = {}, onClick = onClick)
            .focusable()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = fg.copy(alpha = 0.6f), fontSize = 15.sp)
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, size: Dp = 38.dp, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        when {
            focused -> Color.White
            selected -> Color(0xFF3D7BFF)
            else -> Color.White.copy(alpha = 0.07f)
        },
        label = "chip",
    )
    Box(
        modifier
            .width(if (text.length > 3) size + 8.dp else size)
            .height(size)
            .clip(RowShape)
            .background(bg)
            .border(1.dp, if (selected && !focused) Color.White.copy(alpha = 0.3f) else Color.Transparent, RowShape)
            .onFocusChanged { focused = it.isFocused }
            .selectKeys(onPressed = {}, onClick = onClick)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (focused) Color(0xFF111111) else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
