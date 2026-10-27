package com.ohav.tvhome.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.ohav.tvhome.HomeController
import com.ohav.tvhome.R
import com.ohav.tvhome.data.AppEntry

private const val COLS = 6

/** Full-screen grid of every visible app; also the comfortable place to rearrange them. */
@Composable
fun AllApps(home: HomeController, onDismiss: () -> Unit) {
    var movingId by remember { mutableStateOf<String?>(null) }
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    Dialog(
        onDismissRequest = { if (movingId == null) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val apps = home.apps
        val style = home.tileStyle
        val motion = LocalFocusMotion.current
        val focusManager = LocalFocusManager.current
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val grid = rememberLazyGridState()
        val requesters = remember { HashMap<String, FocusRequester>() }
        fun req(id: String) = requesters.getOrPut(id) { FocusRequester() }
        val enter = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            withFrameNanos { }
            apps.firstOrNull()?.let { runCatching { req(it.id).requestFocus() } }
            enter.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        }
        // Keep the tile being moved on screen.
        LaunchedEffect(movingId, apps) {
            val i = apps.indexOfFirst { it.id == movingId }
            if (i < 0) return@LaunchedEffect
            val visible = grid.layoutInfo.visibleItemsInfo
            if (visible.none { it.index == i } || visible.first().index == i || visible.last().index == i) grid.animateScrollToItem(i)
        }

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xF2141821), Color(0xFA07080C))))
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
                    false
                }
        ) {
            val sidePad = 48.dp
            val gap = 18.dp
            val tileW = (maxWidth - sidePad * 2 - gap * (COLS - 1)) / COLS
            Column(
                Modifier.graphicsLayer {
                    alpha = enter.value
                    val s = 0.96f + 0.04f * enter.value
                    scaleX = s
                    scaleY = s
                }
            ) {
                Text(
                    stringResource(R.string.all_apps),
                    modifier = Modifier.padding(start = sidePad, top = 34.dp, bottom = 6.dp),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(COLS),
                    state = grid,
                    contentPadding = PaddingValues(horizontal = sidePad, vertical = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(apps, key = { _, app -> app.id }) { _, app ->
                        AppTile(
                            app = app,
                            style = style,
                            width = tileW,
                            focusRequester = req(app.id),
                            moving = movingId == app.id,
                            onFocused = { },
                            onClick = { rect -> home.launch(app, rect) },
                            onLongPress = { menuApp = app },
                            modifier = Modifier.animateItem().zIndex(if (movingId == app.id) 1f else 0f),
                        )
                    }
                }
            }
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
}
