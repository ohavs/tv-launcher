package com.ohav.tvhome.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import com.ohav.tvhome.camera.CameraLink
import com.ohav.tvhome.camera.IMotionListener
import com.ohav.tvhome.data.CameraConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Channels with motion right now, most recent first. The `:camera` process watches every shown channel's
 * sub stream (sizes only, no decoding) while [active] and the launcher is in the foreground.
 */
@Composable
fun rememberCameraMotion(config: CameraConfig?, active: Boolean): State<List<Int>> {
    val context = LocalContext.current
    val moving = remember { mutableStateOf(emptyList<Int>()) }
    var started by remember { mutableStateOf(false) }
    LifecycleStartEffect(Unit) {
        started = true
        onStopOrDispose { started = false }
    }
    val on = config != null && config.motion && active && started
    LaunchedEffect(config, on) {
        moving.value = emptyList()
        if (!on || config == null) return@LaunchedEffect
        delay(3000) // after the dashboard's own streams are up
        val link = runCatching { CameraLink.connect(context.applicationContext) {} }.getOrNull() ?: return@LaunchedEffect
        val main = Handler(Looper.getMainLooper())
        var alive = true
        val listener = object : IMotionListener.Stub() {
            override fun onMotion(channel: Int, isMoving: Boolean) {
                main.post {
                    if (!alive) return@post
                    val rest = moving.value - channel
                    moving.value = if (isMoving) listOf(channel) + rest else rest
                }
            }
        }
        try {
            withContext(Dispatchers.IO) {
                link.player.watchMotion(
                    config.channels.map { config.url(it, sub = true) }.toTypedArray(),
                    config.channels.toIntArray(),
                    listener,
                )
            }
            awaitCancellation()
        } finally {
            alive = false
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { link.player.stopMotion() }
                link.close() // inside: see CameraCard
            }
        }
    }
    return moving
}
