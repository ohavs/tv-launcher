package com.ohav.tvhome.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ohav.tvhome.HomeController
import com.ohav.tvhome.R
import com.ohav.tvhome.data.AppEntry
import com.ohav.tvhome.data.HomeLayout
import com.ohav.tvhome.data.ShelfItem
import com.ohav.tvhome.data.TileStyle
import com.ohav.tvhome.data.cityOf
import kotlinx.coroutines.delay

/** Focus rows, top to bottom. Up/Down jumps between them and returns to the last item used. */
private enum class Section { Top, Widgets, Apps, Content }

/** Scrolls a row only as much as needed to keep the focused item [marginPx] away from the edges. */
@OptIn(ExperimentalFoundationApi::class)
private class KeepInView(private val marginPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val lead = offset - marginPx
        val trail = offset + size - (containerSize - marginPx)
        return when {
            lead < 0f || size > containerSize - 2 * marginPx -> lead
            trail > 0f -> trail
            else -> 0f
        }
    }
}

private val EaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val WidgetHeight = 90.dp
private val ClassicOrder = listOf(Section.Top, Section.Widgets, Section.Apps, Section.Content)
private val CinemaOrder = listOf(Section.Top, Section.Widgets, Section.Content, Section.Apps)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(home: HomeController, openSettings: () -> Unit) {
    val apps = home.apps
    val style = home.tileStyle
    val track = home.nowPlaying.track
    val cinema = home.layout == HomeLayout.Cinema
    val motion = remember { FocusMotion() }
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val requesters = remember { HashMap<String, FocusRequester>() }
    fun req(id: String) = requesters.getOrPut(id) { FocusRequester() }

    var section by remember { mutableStateOf(Section.Apps) }
    val lastIndex = remember { IntArray(Section.entries.size) }
    /** Bumped on every focus move, for what reads [lastIndex] while composing (cinema hero and carousel). */
    var focusMoves by remember { mutableIntStateOf(0) }
    var shelfApp by remember { mutableStateOf<AppEntry?>(null) }
    var shelfItems by remember { mutableStateOf<List<ShelfItem>>(emptyList()) }
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    var movingId by remember { mutableStateOf<String?>(null) }
    var showAll by remember { mutableStateOf(false) }
    /** False until focus first lands on the apps row (the window's own initial focus goes top-left). */
    var settled by remember { mutableStateOf(false) }
    val enter = remember { Animatable(0f) }

    val topIds = listOf("top:all", "top:settings")
    val cams = home.camera
    val dashCams = cams?.channels?.take(2).orEmpty()
    val widgetIds = buildList {
        dashCams.indices.forEach { add("w:cam$it") }
        if (dashCams.size > 1) add("w:allcams")
        if (track != null) { add("w:now"); add("w:pp") }
    }
    fun ids(s: Section): List<String> = when (s) {
        Section.Top -> topIds
        Section.Widgets -> widgetIds
        Section.Apps -> apps.map { it.id }
        Section.Content -> shelfItems.map { "c:" + it.key }
    }
    fun focus(s: Section, i: Int = lastIndex[s.ordinal]): Boolean {
        val l = ids(s)
        if (l.isEmpty()) return false
        return runCatching { req(l[i.coerceIn(0, l.lastIndex)]).requestFocus() }.isSuccess
    }
    fun onFocused(s: Section, i: Int) {
        section = s
        lastIndex[s.ordinal] = i
        focusMoves++
    }
    fun stepTo(dir: Dir): Boolean = when (dir) {
        Dir.Left, Dir.Right -> {
            val step = (if (dir == Dir.Right) 1 else -1) * (if (isRtl) -1 else 1)
            val i = lastIndex[section.ordinal] + step
            i in ids(section).indices && focus(section, i)
        }
        Dir.Up, Dir.Down -> {
            // Cinema puts the app's content above the carousel; classic puts it below the apps.
            val order = if (cinema) CinemaOrder else ClassicOrder
            val at = order.indexOf(section)
            val next = if (dir == Dir.Up) order.take(at).reversed() else order.drop(at + 1)
            next.firstOrNull { ids(it).isNotEmpty() }?.let { focus(it) } ?: false
        }
    }

    CompositionLocalProvider(LocalFocusMotion provides motion) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onPreviewKeyEvent { ev ->
                    val dir = when (ev.key) {
                        Key.DirectionLeft -> Dir.Left
                        Key.DirectionRight -> Dir.Right
                        Key.DirectionUp -> Dir.Up
                        Key.DirectionDown -> Dir.Down
                        else -> null
                    }
                    val moving = movingId
                    if (moving != null) {
                        if (ev.type == KeyEventType.KeyDown) {
                            if (dir == Dir.Left || dir == Dir.Right) {
                                val delta = (if (dir == Dir.Right) 1 else -1) * (if (isRtl) -1 else 1)
                                if (home.move(moving, delta)) lastIndex[Section.Apps.ordinal] = home.apps.indexOfFirst { it.id == moving }
                                else motion.bump(dir)
                            } else if (dir != null) {
                                motion.bump(dir)
                            } else if (ev.isSelect() || ev.key == Key.Back) {
                                home.saveOrder()
                                movingId = null
                            }
                        }
                        return@onPreviewKeyEvent dir != null || ev.isSelect() || ev.key == Key.Back
                    }
                    if (dir != null) {
                        if (ev.type == KeyEventType.KeyDown) {
                            motion.lastDir = dir
                            if (!stepTo(dir)) motion.bump(dir)
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (ev.key == Key.Menu) {
                        if (ev.type == KeyEventType.KeyUp) openSettings()
                        return@onPreviewKeyEvent true
                    }
                    if (ev.key == Key.Settings) {
                        if (ev.type == KeyEventType.KeyUp) home.systemSettings()
                        return@onPreviewKeyEvent true
                    }
                    false
                }
        ) {
            val sidePad = 48.dp
            val tileW = if (style == TileStyle.Banner) 128.dp else 96.dp
            val tileH = tileHeight(style, tileW)
            val gap = 18.dp
            val keep = remember(density) { KeepInView(with(density) { 120.dp.toPx() }) }

            // Entrance: wallpaper settles, then header, widgets, apps and content rise in turn.
            fun Modifier.stagger(k: Int) = graphicsLayer {
                val p = EaseOut.transform(((enter.value - k * 0.09f) / 0.62f).coerceIn(0f, 1f))
                alpha = p
                translationY = (1f - p) * 22.dp.toPx()
            }

            focusMoves
            val heroItem = if (section == Section.Content) shelfItems.getOrNull(lastIndex[Section.Content.ordinal])
                else shelfItems.firstOrNull { it.imageUri != null } ?: shelfItems.firstOrNull()
            if (cinema) {
                CinemaBackdrop(shelfApp, heroItem, Modifier.graphicsLayer {
                    val s = 1.06f - 0.06f * EaseOut.transform(enter.value)
                    scaleX = s
                    scaleY = s
                })
                CinemaScrims(isRtl)
            } else {
            WallpaperBackground(Modifier.graphicsLayer {
                val s = 1.06f - 0.06f * EaseOut.transform(enter.value)
                scaleX = s
                scaleY = s
            })
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.50f),
                        0.24f to Color.Black.copy(alpha = 0.12f),
                        0.40f to Color.Black.copy(alpha = 0.28f),
                        0.62f to Color.Black.copy(alpha = 0.80f),
                        1f to Color.Black.copy(alpha = 0.94f),
                    )
                )
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        if (isRtl) listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.32f))
                        else listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent, Color.Transparent)
                    )
                )
            )
            }

            Column(Modifier.fillMaxSize().padding(top = 30.dp)) {
                Box(Modifier.fillMaxWidth().padding(horizontal = sidePad)) {
                    ClockBlock(home.weather, cityOf(home.prefs.cityId), Modifier.align(Alignment.CenterStart).stagger(0))
                    Row(Modifier.align(Alignment.CenterEnd).stagger(0), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        CircleButton(Glyph.Apps, req("top:all"), { onFocused(Section.Top, 0) }, { showAll = true })
                        CircleButton(Glyph.Settings, req("top:settings"), { onFocused(Section.Top, 1) }, { openSettings() })
                    }
                }

                // Dashboard: cameras on the start side, whatever is playing on the end side.
                if (widgetIds.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = sidePad, end = sidePad, top = 14.dp).height(WidgetHeight).stagger(1),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        var i = 0
                        if (cams != null) {
                            dashCams.forEachIndexed { slot, channel ->
                                val iCam = i++
                                CameraCard(
                                    cams, channel, slot, active = !showAll, req("w:cam$slot"), { onFocused(Section.Widgets, iCam) },
                                    { home.openCamera(channel, it) }, WidgetHeight,
                                )
                            }
                            if (dashCams.size > 1) {
                                val iAll = i++
                                AllCamerasCard(cams.channels.size, req("w:allcams"), { onFocused(Section.Widgets, iAll) }, home::openCameraGrid, WidgetHeight)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (track != null) {
                            val iNow = i++
                            val iPp = i
                            NowPlayingCard(track, req("w:now"), { onFocused(Section.Widgets, iNow) }, home::openNowPlaying, WidgetHeight)
                            PlayPauseButton(track.playing, req("w:pp"), { onFocused(Section.Widgets, iPp) }, home.nowPlaying::playPause)
                        }
                    }
                }

                if (cinema) {
                    Spacer(Modifier.weight(1f))
                    CinemaHero(shelfApp, heroItem, sidePad, Modifier.stagger(2))
                    CompositionLocalProvider(LocalBringIntoViewSpec provides keep) {
                        ContentRow(
                            state = ShelfState(shelfApp, shelfItems),
                            sidePad = sidePad,
                            cardHeight = 62.dp,
                            req = ::req,
                            onFocused = { onFocused(Section.Content, it) },
                            onOpen = home::open,
                            modifier = Modifier.stagger(2),
                            showTitle = false,
                            captions = false,
                        )
                    }
                    AppCarousel(
                        apps = apps,
                        style = style,
                        center = lastIndex[Section.Apps.ordinal].coerceIn(0, (apps.size - 1).coerceAtLeast(0)),
                        movingId = movingId,
                        req = ::req,
                        onFocused = { i, app ->
                            onFocused(Section.Apps, i)
                            if (shelfApp?.id != app.id) lastIndex[Section.Content.ordinal] = 0
                            shelfApp = app
                        },
                        onClick = home::launch,
                        onLongPress = { menuApp = it },
                        modifier = Modifier.stagger(3).padding(bottom = 16.dp),
                    )
                } else {
                Spacer(Modifier.height(if (widgetIds.isEmpty()) 60.dp else 4.dp))
                CompositionLocalProvider(LocalBringIntoViewSpec provides keep) {
                    Box(Modifier.fillMaxWidth().stagger(2).horizontalScroll(rememberScrollState())) {
                        Box(
                            Modifier
                                .padding(horizontal = sidePad, vertical = 10.dp)
                                .width((tileW + gap) * apps.size - gap)
                                .height(tileH + 34.dp)
                        ) {
                            apps.forEachIndexed { i, app ->
                                key(app.id) {
                                    val target = with(density) { IntOffset(((tileW + gap) * i).roundToPx(), 0) }
                                    val pos by animateIntOffsetAsState(target, spring(dampingRatio = 0.78f, stiffness = 340f), label = "pos")
                                    AppTile(
                                        app = app,
                                        style = style,
                                        width = tileW,
                                        focusRequester = req(app.id),
                                        moving = movingId == app.id,
                                        onFocused = {
                                            onFocused(Section.Apps, i)
                                            if (shelfApp?.id != app.id) lastIndex[Section.Content.ordinal] = 0
                                            shelfApp = app
                                        },
                                        onClick = { rect -> home.launch(app, rect) },
                                        onLongPress = { menuApp = app },
                                        modifier = Modifier
                                            .offset { pos }
                                            .zIndex(if (section == Section.Apps && lastIndex[Section.Apps.ordinal] == i) 1f else 0f),
                                    )
                                }
                            }
                        }
                    }
                    ContentRow(
                        state = ShelfState(shelfApp, shelfItems),
                        sidePad = sidePad,
                        cardHeight = 100.dp,
                        req = ::req,
                        onFocused = { onFocused(Section.Content, it) },
                        onOpen = home::open,
                        modifier = Modifier.stagger(3),
                    )
                }
                }
            }

            AnimatedVisibility(
                visible = movingId != null,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 26.dp),
            ) {
                Text(
                    stringResource(R.string.move_hint),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xCC1C1C1F))
                        .padding(horizontal = 26.dp, vertical = 12.dp),
                    color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                )
            }
        }

        if (showAll) {
            AllApps(home, onDismiss = { showAll = false; focus(Section.Top, 0) })
        }
    }

    LaunchedEffect(home.resumeSignal) {
        if (home.resumeSignal == 0) return@LaunchedEffect
        enter.snapTo(0f)
        enter.animateTo(1f, tween(1100, easing = LinearEasing))
    }

    // First focus, and recovery when the focused app was hidden or removed.
    LaunchedEffect(apps) {
        if (apps.isEmpty()) return@LaunchedEffect
        if (shelfApp == null || apps.none { it.id == shelfApp?.id }) shelfApp = apps.first()
        withFrameNanos { }
        if (!settled) settled = focus(Section.Apps, 0)
        else if (section == Section.Apps) focus(Section.Apps)
    }

    // The window hands initial focus to the top-left item; put it where we want it.
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (!windowFocused || showAll || menuApp != null) return@LaunchedEffect
        withFrameNanos { }
        if (!settled) settled = focus(Section.Apps, 0) else focus(section)
    }

    // The now-playing card can vanish while focused.
    LaunchedEffect(widgetIds.size) {
        if (section == Section.Widgets && widgetIds.isEmpty()) {
            withFrameNanos { }
            focus(Section.Apps)
        }
    }

    // Home/Back on the home screen: back to the first app.
    LaunchedEffect(home.homeSignal) {
        if (home.homeSignal == 0) return@LaunchedEffect
        movingId?.let { home.saveOrder(); movingId = null }
        menuApp = null
        showAll = false
        motion.lastDir = null
        focus(Section.Apps, 0)
    }

    // Content for the focused app; refreshed whenever we come back.
    LaunchedEffect(shelfApp?.packageName, home.resumeSignal) {
        val pkg = shelfApp?.packageName ?: return@LaunchedEffect
        shelfItems = home.shelf.cached(pkg) ?: emptyList()
        delay(140)
        shelfItems = home.shelf.itemsFor(pkg)
    }
    LaunchedEffect(apps.take(6).map { it.packageName }, home.resumeSignal) {
        apps.take(6).forEach { home.shelf.itemsFor(it.packageName) }
    }

    menuApp?.let { app ->
        AppMenu(
            app = app,
            style = style,
            canHide = app.packageName != home.packageName,
            onMove = { menuApp = null; movingId = app.id },
            onHide = { menuApp = null; home.hide(app) },
            onInfo = { menuApp = null; home.appInfo(app) },
            onDismiss = { menuApp = null },
        )
    }
}
