package com.ohav.tvhome.camera

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ohav.tvhome.R
import com.ohav.tvhome.data.CameraConfig
import kotlin.math.ceil

/**
 * All cameras at once (sub-streams) in the `:camera` process. OK opens one full screen; Back goes straight home.
 * Cells are TextureViews: eight SurfaceView video layers crashed the vendor HW composer (whole UI restarted).
 */
class CameraGridActivity : Activity() {
    private class Cell(val channel: Int, val view: FrameLayout, val video: TextureView, val status: TextView) {
        var player: ExoPlayer? = null
    }

    private val cells = ArrayList<Cell>()
    private var mainUrls = emptyList<String>()
    private var subUrls = emptyList<String>()
    private var channels = emptyList<Int>()
    private var focusIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainUrls = intent.getStringArrayListExtra(EXTRA_MAIN).orEmpty()
        subUrls = intent.getStringArrayListExtra(EXTRA_SUB).orEmpty()
        channels = intent.getIntArrayExtra(EXTRA_CHANNELS)?.toList().orEmpty()
        if (channels.isEmpty() || mainUrls.size != channels.size || subUrls.size != channels.size) {
            finish()
            return
        }
        val count = channels.size.coerceAtMost(MAX_CELLS)
        val cols = when {
            count <= 1 -> 1
            count <= 4 -> 2
            else -> 3
        }
        val rows = ceil(count / cols.toFloat()).toInt()
        val dp = resources.displayMetrics.density
        val gap = (6 * dp).toInt()

        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(gap, gap, gap, gap)
        }
        for (r in 0 until rows) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0 until cols) {
                val i = r * cols + c
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { setMargins(gap, gap, gap, gap) }
                if (i >= count) {
                    row.addView(View(this), lp)
                    continue
                }
                row.addView(makeCell(i, dp).view, lp)
            }
            grid.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        })
        focusIndex = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, count - 1)
        cells[focusIndex].view.requestFocus()
    }

    private fun makeCell(i: Int, dp: Float): Cell {
        val video = TextureView(this)
        val label = TextView(this).apply {
            text = getString(R.string.camera_n, channels[i])
            setTextColor(Color.WHITE)
            textSize = 15f
            setShadowLayer(6f, 0f, 1f, Color.BLACK)
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
        }
        val status = TextView(this).apply {
            setTextColor(0x99FFFFFF.toInt())
            textSize = 14f
            setText(R.string.camera_connecting)
        }
        val frame = FrameLayout(this).apply {
            isFocusable = true
            setBackgroundColor(0xFF15171C.toInt())
            foreground = GradientDrawable().apply {
                cornerRadius = 6 * dp
                setStroke((3 * dp).toInt(), Color.WHITE)
                setColor(Color.TRANSPARENT)
            }.also { it.alpha = 0 }
            setOnFocusChangeListener { v, has ->
                (v as FrameLayout).foreground?.alpha = if (has) 255 else 0
                if (has) focusIndex = i
            }
            setOnClickListener { openFull(i) }
            addView(video, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START))
        }
        return Cell(channels[i], frame, video, status).also { cells.add(it) }
    }

    override fun onStart() {
        super.onStart()
        cells.forEachIndexed { i, cell ->
            cell.status.visibility = View.VISIBLE
            cell.status.setText(R.string.camera_connecting)
            cell.player = liveCameraPlayer(this, audio = false).apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        cell.status.visibility = if (state == Player.STATE_READY) View.GONE else View.VISIBLE
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        cell.status.visibility = View.VISIBLE
                        cell.status.setText(R.string.camera_offline)
                    }
                })
                setVideoTextureView(cell.video)
                playRtsp(subUrls[i])
            }
        }
    }

    override fun onStop() {
        cells.forEach { cell ->
            cell.player?.release()
            cell.player = null
        }
        super.onStop()
    }

    private fun openFull(i: Int) {
        startActivity(CameraActivity.intent(this, mainUrls, subUrls, channels, i))
    }

    companion object {
        private const val MAX_CELLS = 9
        private const val EXTRA_MAIN = "main"
        private const val EXTRA_SUB = "sub"
        private const val EXTRA_CHANNELS = "channels"
        private const val EXTRA_INDEX = "index"

        fun intent(context: Context, config: CameraConfig): Intent {
            val channels = config.channels.sorted()
            return Intent(context, CameraGridActivity::class.java)
                .putStringArrayListExtra(EXTRA_MAIN, ArrayList(channels.map { config.url(it, sub = false) }))
                .putStringArrayListExtra(EXTRA_SUB, ArrayList(channels.map { config.url(it, sub = true) }))
                .putExtra(EXTRA_CHANNELS, channels.toIntArray())
        }
    }
}
