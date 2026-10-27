package com.ohav.tvhome.camera

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.ohav.tvhome.R
import com.ohav.tvhome.data.CameraConfig

/** Full-screen camera (main stream) in the `:camera` process. Left/Right switches channel, Back closes. */
class CameraActivity : Activity() {
    private lateinit var surface: SurfaceView
    private lateinit var label: TextView
    private lateinit var status: TextView
    private var player: ExoPlayer? = null
    private var mainUrls = emptyList<String>()
    private var subUrls = emptyList<String>()
    private var channels = IntArray(0)
    private var index = 0
    private var onSub = false
    private var video = VideoSize.UNKNOWN

    private val hideLabel = Runnable { label.animate().alpha(0f).setDuration(400) }

    private val events = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            status.visibility = if (state == Player.STATE_READY) View.GONE else View.VISIBLE
            if (state == Player.STATE_BUFFERING) status.setText(R.string.camera_connecting)
        }

        override fun onVideoSizeChanged(size: VideoSize) {
            video = size
            fit()
        }

        override fun onPlayerError(error: PlaybackException) {
            // Some DVRs refuse the main stream to a second client (or can't decode it): fall back to the sub stream.
            if (!onSub) {
                onSub = true
                player?.playRtsp(subUrls[index])
                return
            }
            status.visibility = View.VISIBLE
            status.setText(R.string.camera_offline)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainUrls = intent.getStringArrayListExtra(EXTRA_MAIN).orEmpty()
        subUrls = intent.getStringArrayListExtra(EXTRA_SUB).orEmpty()
        channels = intent.getIntArrayExtra(EXTRA_CHANNELS) ?: IntArray(0)
        index = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, (channels.size - 1).coerceAtLeast(0))
        if (channels.isEmpty() || mainUrls.size != channels.size || subUrls.size != channels.size) {
            finish()
            return
        }
        surface = SurfaceView(this)
        label = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            setShadowLayer(8f, 0f, 2f, Color.BLACK)
            setPadding(56, 36, 56, 36)
        }
        status = TextView(this).apply {
            setTextColor(0xB3FFFFFF.toInt())
            textSize = 18f
            setText(R.string.camera_connecting)
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(surface, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
            addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))
            addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fit() }
        })
    }

    override fun onStart() {
        super.onStart()
        if (channels.isEmpty()) return
        player = liveCameraPlayer(this, audio = false).apply {
            addListener(events)
            setVideoSurfaceView(surface)
        }
        play()
    }

    override fun onStop() {
        player?.run {
            removeListener(events)
            release()
        }
        player = null
        super.onStop()
    }

    private fun play() {
        onSub = false
        video = VideoSize.UNKNOWN
        status.visibility = View.VISIBLE
        status.setText(R.string.camera_connecting)
        val multi = channels.size > 1
        label.text = getString(R.string.camera_n, channels[index]) + if (multi) "   ‹ ${index + 1}/${channels.size} ›" else ""
        label.removeCallbacks(hideLabel)
        label.alpha = 1f
        label.postDelayed(hideLabel, 4000)
        player?.playRtsp(mainUrls[index])
    }

    /** Letterbox the video inside the screen, keeping its aspect ratio. */
    private fun fit() {
        val parent = surface.parent as? View ?: return
        val pw = parent.width
        val ph = parent.height
        if (pw == 0 || ph == 0) return
        val lp = surface.layoutParams
        if (video.width == 0 || video.height == 0) {
            if (lp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                surface.layoutParams = lp
            }
            return
        }
        val aspect = displayAspect(video.width * video.pixelWidthHeightRatio, video.height.toFloat())
        val (w, h) = if (pw.toFloat() / ph > aspect) (ph * aspect).toInt() to ph else pw to (pw / aspect).toInt()
        if (lp.width != w || lp.height != h) {
            lp.width = w
            lp.height = h
            surface.layoutParams = lp
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val rtl = window.decorView.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val step = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (rtl) -1 else 1
            KeyEvent.KEYCODE_DPAD_LEFT -> if (rtl) 1 else -1
            KeyEvent.KEYCODE_CHANNEL_UP -> 1
            KeyEvent.KEYCODE_CHANNEL_DOWN -> -1
            else -> return super.onKeyDown(keyCode, event)
        }
        if (channels.size > 1) {
            index = (index + step + channels.size) % channels.size
            play()
        }
        return true
    }

    companion object {
        private const val EXTRA_MAIN = "main"
        private const val EXTRA_SUB = "sub"
        private const val EXTRA_CHANNELS = "channels"
        private const val EXTRA_INDEX = "index"

        /** Opens [channel]; Left/Right then walks the channels in number order. */
        fun intent(context: Context, config: CameraConfig, channel: Int): Intent {
            val channels = config.channels.sorted()
            return intent(
                context,
                channels.map { config.url(it, sub = false) },
                channels.map { config.url(it, sub = true) },
                channels,
                channels.indexOf(channel).coerceAtLeast(0),
            )
        }

        fun intent(context: Context, main: List<String>, sub: List<String>, channels: List<Int>, index: Int): Intent =
            Intent(context, CameraActivity::class.java)
                .putStringArrayListExtra(EXTRA_MAIN, ArrayList(main))
                .putStringArrayListExtra(EXTRA_SUB, ArrayList(sub))
                .putExtra(EXTRA_CHANNELS, channels.toIntArray())
                .putExtra(EXTRA_INDEX, index)
    }
}
