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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

const val CAM_CONNECTING = 1
const val CAM_PLAYING = 2
const val CAM_ERROR = 3

/** Low-latency live RTSP player (TCP, small buffers, no audio unless asked). */
@OptIn(UnstableApi::class)
fun liveCameraPlayer(context: Context, audio: Boolean): ExoPlayer {
    val player = ExoPlayer.Builder(context)
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
 * The launcher hands over a Surface (from its TextureView) and the player renders straight into it.
 */
class CameraService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var listener: ICameraListener? = null
    private var url: String? = null
    private var retries = 0

    private val events = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) retries = 0
            notify(if (state == Player.STATE_READY) CAM_PLAYING else CAM_CONNECTING, null)
        }

        override fun onVideoSizeChanged(size: VideoSize) {
            if (size.width > 0) runCatching { listener?.onVideoSize((size.width * size.pixelWidthHeightRatio).toInt(), size.height) }
        }

        override fun onPlayerError(error: PlaybackException) {
            notify(CAM_ERROR, error.errorCodeName)
            // DVRs drop idle sessions now and then: reconnect a few times before giving up.
            if (retries++ < 5) main.postAtTime({ url?.let { player?.playRtsp(it) } }, RETRY, SystemClock.uptimeMillis() + 2000L * retries)
        }
    }

    private val binder = object : ICameraPlayer.Stub() {
        override fun play(surface: Surface, url: String, listener: ICameraListener) {
            main.post { start(surface, url, listener) }
        }

        override fun stop() {
            val done = CountDownLatch(1)
            main.post { release(); done.countDown() }
            done.await(1, TimeUnit.SECONDS)
        }
    }

    private fun start(surface: Surface, url: String, listener: ICameraListener) {
        release()
        this.listener = listener
        this.url = url
        retries = 0
        notify(CAM_CONNECTING, null)
        player = liveCameraPlayer(this, audio = false).apply {
            addListener(events)
            setVideoSurface(surface)
            playRtsp(url)
        }
    }

    private fun release() {
        main.removeCallbacksAndMessages(RETRY)
        player?.run {
            removeListener(events)
            clearVideoSurface()
            release()
        }
        player = null
        listener = null
        url = null
    }

    private fun notify(state: Int, msg: String?) {
        runCatching { listener?.onState(state, msg) }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onUnbind(intent: Intent): Boolean {
        release()
        return false
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private companion object {
        val RETRY = Any()
    }
}
