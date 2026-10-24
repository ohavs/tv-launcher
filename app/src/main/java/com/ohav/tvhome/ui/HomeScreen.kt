package com.ohav.tvhome.ui

import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ohav.tvhome.HomeController
import com.ohav.tvhome.R
import com.ohav.tvhome.aerial.AerialBackground
import com.ohav.tvhome.data.AppEntry
import com.ohav.tvhome.data.ShelfItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt

private const val COLS = 5

@OptIn(ExperimentalFoundationApi::class)
private val NoAutoScroll = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(home: HomeController, openSettings: () -> Unit) {
    val apps = home.apps
    val context = LocalContext.current
    val motion = remember { FocusMotion() }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val defaultSpec = LocalBringIntoViewSpec.current
    val requesters = remember { HashMap<String, FocusRequester>() }
    fun req(id: String) = requesters.getOrPut(id) { FocusRequester() }

    var shelfApp by remember { mutableStateOf<AppEntry?>(null) }
    var shelfItems by remember { mutableStateOf<List<ShelfItem>>(emptyList()) }
    var focusedId by remember { mutableStateOf<String?>(null) }
    var lastIndex by remember { mutableIntStateOf(0) }
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    var movingId by remember { mutableStateOf<String?>(null) }
    var videoFailed by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    fun focusIndex(i: Int) {
        val app = apps.getOrNull(i.coerceIn(0, (apps.size - 1).coerceAtLeast(0))) ?: return
        runCatching { req(app.id).requestFocus() }
    }

    CompositionLocalProvider(LocalFocusMotion provides motion) {
        BoxWithConstraints(
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
                            if (dir != null) {
                                val delta = when (dir) {
                                    Dir.Left -> if (isRtl) 1 else -1
                                    Dir.Right -> if (isRtl) -1 else 1
                                    Dir.Up -> -COLS
                                    Dir.Down -> COLS
                                }
                                if (!home.move(moving, delta)) motion.bump(dir)
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
                            if (!focusManager.moveFocus(dir.focus)) motion.bump(dir)
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (ev.key == Key.Menu) {
                        if (ev.type == KeyEventType.KeyUp) openSettings()
                        return@onPreviewKeyEvent true
                    }
                    if (ev.key == Key.Settings) {
                        if (ev.type == KeyEventType.KeyUp) runCatching {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        return@onPreviewKeyEvent true
                    }
                    false
                }
        ) {
            val sidePad = 64.dp
            val gap = 28.dp
            val tileW = (maxWidth - sidePad * 2 - gap * (COLS - 1)) / COLS
            val tileH = tileW * 9 / 16
            val rowH = tileH + 60.dp
            val screenH = maxHeight
            val shelfH = screenH * 0.56f
            val shelfPx = with(density) { shelfH.toPx() }
            val rowPx = with(density) { rowH.toPx() }
            val pivotPx = with(density) { (maxHeight * 0.30f).toPx() }

            fun scrollToRow(row: Int) {
                scope.launch {
                    val target = if (row == 0) 0 else (shelfPx + row * rowPx - pivotPx).roundToInt().coerceIn(0, scroll.maxValue)
                    scroll.animateScrollTo(target, spring(dampingRatio = 1f, stiffness = 210f))
                }
            }

            // Background: aerial video, or a soft glow in the focused app's colour.
            val accent by animateColorAsState(
                apps.firstOrNull { it.id == focusedId }?.accent ?: Color(0xFF1E2A3A), tween(900), label = "accent",
            )
            if (home.videoEnabled && !videoFailed) {
                AerialBackground(onUnavailable = { videoFailed = true })
            } else {
                Box(Modifier.fillMaxSize().drawBehind {
                    drawRect(Color(0xFF05070A))
                    drawRect(Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.75f), accent.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width * 0.3f, size.height * 0.05f),
                        radius = size.width * 0.85f,
                    ))
                })
            }
            // Scrims: top for the clock, bottom for the rows, plus a dim that grows as you scroll into the grid.
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.35f), 0.18f to Color.Transparent,
                0.42f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.62f),
            )))
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = (scroll.value / shelfPx).coerceIn(0f, 1f) }.background(Color.Black.copy(alpha = 0.6f)))

            CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScroll) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll, enabled = false)) {
                    CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                        TopShelf(
                            state = ShelfState(shelfApp, shelfItems),
                            height = shelfH,
                            sidePad = sidePad,
                            downTarget = shelfApp?.let { req(it.id) },
                            onOpen = home::open,
                            modifier = Modifier.graphicsLayer { alpha = 1f - (scroll.value / shelfPx).coerceIn(0f, 1f) },
                        )
                    }
                    val rows = (apps.size + COLS - 1) / COLS
                    Box(
                        Modifier
                            .padding(horizontal = sidePad)
                            .fillMaxWidth()
                            .height(rowH * rows + screenH * 0.5f)
                    ) {
                        apps.forEachIndexed { i, app ->
                            key(app.id) {
                                val row = i / COLS
                                val target = with(density) { IntOffset(((tileW + gap) * (i % COLS)).roundToPx(), (rowH * row).roundToPx()) }
                                val pos by animateIntOffsetAsState(target, spring(dampingRatio = 0.78f, stiffness = 340f), label = "pos")
                                AppTile(
                                    app = app,
                                    width = tileW,
                                    focusRequester = req(app.id),
                                    moving = movingId == app.id,
                                    onFocused = {
                                        focusedId = app.id
                                        lastIndex = i
                                        if (row == 0) shelfApp = app
                                        scrollToRow(row)
                                    },
                                    onClick = { rect -> home.launch(app, rect) },
                                    onLongPress = { menuApp = app },
                                    modifier = Modifier
                                        .offset { pos }
                                        .zIndex(if (focusedId == app.id) 1f else 0f),
                                )
                            }
                        }
                    }
                }
            }

            Clock(Modifier.align(Alignment.TopEnd).padding(top = 30.dp, end = sidePad))

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

            // Keep the moving tile in view as it changes rows.
            LaunchedEffect(movingId, apps) {
                val id = movingId ?: return@LaunchedEffect
                val i = apps.indexOfFirst { it.id == id }
                if (i >= 0) { lastIndex = i; scrollToRow(i / COLS) }
            }
        }
    }

    // First focus, and recovery when the focused app was hidden or removed.
    LaunchedEffect(apps) {
        if (apps.isEmpty()) return@LaunchedEffect
        if (shelfApp == null || apps.none { it.id == shelfApp?.id }) shelfApp = apps.first()
        if (focusedId == null || apps.none { it.id == focusedId }) {
            withFrameNanos { }
            focusIndex(if (focusedId == null) 0 else lastIndex)
        }
    }

    // Home/Back on the home screen: back to the top-left app.
    LaunchedEffect(home.homeSignal) {
        if (home.homeSignal == 0) return@LaunchedEffect
        movingId?.let { home.saveOrder(); movingId = null }
        menuApp = null
        motion.lastDir = null
        focusIndex(0)
        scroll.animateScrollTo(0, spring(dampingRatio = 1f, stiffness = 210f))
    }

    // Top Shelf content for the focused first-row app; refreshed whenever we come back.
    LaunchedEffect(shelfApp?.packageName, home.resumeSignal) {
        val pkg = shelfApp?.packageName ?: return@LaunchedEffect
        shelfItems = home.shelf.cached(pkg) ?: emptyList()
        delay(140)
        shelfItems = home.shelf.itemsFor(pkg)
    }
    LaunchedEffect(apps.take(COLS).map { it.packageName }, home.resumeSignal) {
        apps.take(COLS).forEach { home.shelf.itemsFor(it.packageName) }
    }

    menuApp?.let { app ->
        AppMenu(
            app = app,
            canHide = app.packageName != context.packageName,
            onMove = { menuApp = null; movingId = app.id },
            onHide = { menuApp = null; home.hide(app) },
            onInfo = { menuApp = null; home.appInfo(app) },
            onDismiss = { menuApp = null },
        )
    }
}

@Composable
private fun Clock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val time by produceState(DateFormat.getTimeFormat(context).format(Date())) {
        while (true) {
            value = DateFormat.getTimeFormat(context).format(Date())
            delay((60 - Calendar.getInstance().get(Calendar.SECOND)) * 1000L)
        }
    }
    Text(
        time,
        modifier = modifier,
        color = Color.White,
        fontSize = 26.sp,
        fontWeight = FontWeight.Medium,
        style = TextStyle(shadow = LegibleShadow),
    )
}
