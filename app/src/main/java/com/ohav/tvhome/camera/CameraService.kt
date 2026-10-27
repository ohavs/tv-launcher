package com.ohav.tvhome.camera

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

const val CAM_CONNECTING = 1
const val CAM_PLAYING = 2
const val CAM_ERROR = 3

/**
 * Analog-HD DVRs send anamorphic frames (1080N = 960x1080, CIF = 352x288) without aspect info; they're meant
 * to fill a 16:9 screen. Anything clearly wide already is shown as is.
 */
fun displayAspect(width: Float, height: Float): Float = (width / height).let { if (it < 1.5f) 16f / 9f else it }

/**
 * Low-latency live RTSP player (TCP, small buffers, no audio unless asked). The hardware decoder only has ~4
 * instances, so further streams fall back to the software decoder (cheap at the DVR's 352x288 sub-stream).
 */
@OptIn(UnstableApi::class)
fun liveCameraPlayer(context: Context, audio: Boolean): ExoPlayer {
    val player = ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
        .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(500, 2000, 300, 300).build())
        .build()
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, !audio)
        .build()
    if (!audio) player.volume = 0f
    return player
}

@OptIn(UnstableApi::class)
fun ExoPlayer.playRtsp(url: String) {
    setMediaSource(RtspMediaSource.Factory().setForceUseRtpTcp(true).setTimeoutMs(6000).createMediaSource(MediaItem.fromUri(Uri.parse(url))))
    prepare()
    playWhenReady = true
}

/**
 * Runs in the `:camera` process so the decoder pipeline and RTSP buffers stay out of the launcher's memory.
 * The launcher hands over Surfaces (from its TextureViews) and each slot's player renders straight into one.
 */
class CameraService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val slots = HashMap<Int, Slot>()

    private inner class Slot(val url: String, val listener: ICameraListener) : Player.Listener {
        val player: ExoPlayer = liveCameraPlayer(this@CameraService, audio = false)
        private var retries = 0

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) retries = 0
            notify(if (state == Player.STATE_READY) CAM_PLAYING else CAM_CONNECTING)
        }

        override fun onVideoSizeChanged(size: VideoSize) {
            if (size.width > 0) runCatching { listener.onVideoSize((size.width * size.pixelWidthHeightRatio).toInt(), size.height) }
        }

        override fun onPlayerError(error: PlaybackException) {
            notify(CAM_ERROR)
            // DVRs drop idle sessions now and then: reconnect a few times before giving up.
            if (retries++ < 5) main.postAtTime({ player.playRtsp(url) }, this, SystemClock.uptimeMillis() + 2000L * retries)
        }

        fun notify(state: Int) {
            runCatching { listener.onState(state, null) }
        }

        fun release() {
            main.removeCallbacksAndMessages(this)
            player.removeListener(this)
            player.clearVideoSurface()
            player.release()
        }
    }

    private val binder = object : ICameraPlayer.Stub() {
        override fun play(slot: Int, surface: Surface, url: String, listener: ICameraListener) {
            main.post { start(slot, surface, url, listener) }
        }

        override fun stop(slot: Int) {
            val done = CountDownLatch(1)
            main.post { slots.remove(slot)?.release(); done.countDown() }
            done.await(1, TimeUnit.SECONDS)
        }
    }

    private fun start(id: Int, surface: Surface, url: String, listener: ICameraListener) {
        slots.remove(id)?.release()
        val slot = Slot(url, listener)
        slots[id] = slot
        slot.notify(CAM_CONNECTING)
        slot.player.addListener(slot)
        slot.player.setVideoSurface(surface)
        slot.player.playRtsp(url)
    }

    private fun releaseAll() {
        slots.values.forEach { it.release() }
        slots.clear()
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onUnbind(intent: Intent): Boolean {
        releaseAll()
        return false
    }

    override fun onDestroy() {
        releaseAll()
        super.onDestroy()
    }
}
