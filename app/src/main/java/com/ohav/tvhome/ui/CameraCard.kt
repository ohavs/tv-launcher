package com.ohav.tvhome.ui

import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import com.ohav.tvhome.R
import com.ohav.tvhome.camera.CAM_CONNECTING
import com.ohav.tvhome.camera.CAM_ERROR
import com.ohav.tvhome.camera.CAM_PLAYING
import com.ohav.tvhome.camera.CameraLink
import com.ohav.tvhome.camera.ICameraListener
import com.ohav.tvhome.data.CameraConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The SurfaceView's Surface while it exists, and how to let go of it before it's destroyed. */
private class SurfaceSlot {
    var surface by mutableStateOf<Surface?>(null)
    var release: (() -> Unit)? = null
}

/**
 * Live view of one camera channel. Decoding happens in the `:camera` process; it starts a moment after
 * the home screen shows (so it never slows the launch) and stops whenever the launcher is not visible.
 * Video goes to a SurfaceView, which the hardware composer layers under the window: a TextureView would
 * redraw the whole home screen on every video frame. Each card on screen uses its own [slot] in the
 * camera service.
 */
@Composable
fun CameraCard(
    config: CameraConfig,
    channel: Int,
    slot: Int,
    active: Boolean,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: (Rect) -> Unit,
    height: Dp,
) {
    val context = LocalContext.current
    val view = remember { SurfaceSlot() }
    var state by remember { mutableIntStateOf(CAM_CONNECTING) }
    var started by remember { mutableStateOf(false) }
    val url = config.url(channel, sub = true)

    LifecycleStartEffect(Unit) {
        started = true
        onStopOrDispose { started = false }
    }

    val listener = remember {
        val main = Handler(Looper.getMainLooper())
        object : ICameraListener.Stub() {
            override fun onState(s: Int, message: String?) {
                main.post { state = s }
            }

            // The card is 16:9 and DVR frames are meant to be stretched to 16:9 (see displayAspect).
            override fun onVideoSize(width: Int, height: Int) = Unit
        }
    }

    LaunchedEffect(view.surface, started && active, url) {
        state = CAM_CONNECTING
        val surface = view.surface ?: return@LaunchedEffect
        if (!started || !active) return@LaunchedEffect
        delay(1200L + slot * 400L)
        val link = runCatching { CameraLink.connect(context.applicationContext) { state = CAM_ERROR } }.getOrElse {
            state = CAM_ERROR
            return@LaunchedEffect
        }
        // The decoder must let go of the surface before surfaceDestroyed returns.
        view.release = { runCatching { link.player.stop(slot) } }
        try {
            withContext(Dispatchers.IO) { link.player.play(slot, surface, url, listener) }
            awaitCancellation()
        } finally {
            view.release = null
            withContext(NonCancellable + Dispatchers.IO) { runCatching { link.player.stop(slot) } }
            link.close()
        }
    }

    val playing = state == CAM_PLAYING
    val shown by animateFloatAsState(if (playing) 1f else 0f, tween(500), label = "cam")
    GlassButton(requester, onFocused, onClick, Modifier.size(height * 16f / 9f, height), tilt = false) { f ->
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            view.surface = holder.surface
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            view.release?.invoke()
                            view.release = null
                            view.surface = null
                        }
                    })
                }
            },
            // The video shows through a hole the view punches in the window, so it can't fade:
            // the hole opens once frames are coming.
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (playing) 1f else 0f },
        )
        if (state != CAM_PLAYING) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                GlyphIcon(Glyph.Camera, Color.White.copy(alpha = 0.7f), 26.dp)
                Text(
                    stringResource(if (state == CAM_ERROR) R.string.camera_offline else R.string.camera_connecting),
                    color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f * shown))))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (state == CAM_PLAYING) Color(0xFFFF4D4D) else Color.White.copy(alpha = 0.4f)))
                Text(
                    "  " + stringResource(R.string.camera_n, channel),
                    color = Color.White.copy(alpha = 0.75f + 0.25f * f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                )
            }
        }
    }
}
