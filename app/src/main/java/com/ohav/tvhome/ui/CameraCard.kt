package com.ohav.tvhome.ui

import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
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

/** Holds the TextureView's SurfaceTexture; released only once the camera process has let go of it. */
private class TextureSlot {
    var texture by mutableStateOf<SurfaceTexture?>(null)
    var inUse = false
    var orphan: SurfaceTexture? = null
}

/**
 * Live view of one camera channel. Decoding happens in the `:camera` process; it starts a moment after
 * the home screen shows (so it never slows the launch) and stops whenever the launcher is not visible.
 * Each card on screen uses its own [slot] in the camera service.
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
    val tex = remember { TextureSlot() }
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

    LaunchedEffect(tex.texture, started && active, url) {
        val texture = tex.texture ?: return@LaunchedEffect
        if (!started || !active) return@LaunchedEffect
        state = CAM_CONNECTING
        delay(1200L + slot * 400L)
        val link = runCatching { CameraLink.connect(context.applicationContext) { state = CAM_ERROR } }.getOrElse {
            state = CAM_ERROR
            return@LaunchedEffect
        }
        val surface = Surface(texture)
        tex.inUse = true
        try {
            withContext(Dispatchers.IO) { link.player.play(slot, surface, url, listener) }
            awaitCancellation()
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { runCatching { link.player.stop(slot) } }
            link.close()
            surface.release()
            tex.inUse = false
            tex.orphan?.release()
            tex.orphan = null
        }
    }

    val shown by animateFloatAsState(if (state == CAM_PLAYING) 1f else 0f, tween(500), label = "cam")
    GlassButton(requester, onFocused, onClick, Modifier.size(height * 16f / 9f, height)) { f ->
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    isOpaque = false
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            tex.texture = st
                        }

                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit

                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            tex.texture = null
                            if (!tex.inUse) return true
                            tex.orphan = st
                            return false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = shown },
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
