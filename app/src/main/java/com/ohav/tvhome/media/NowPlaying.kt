package com.ohav.tvhome.media

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale

/** Exists only so the system lets us read media sessions (granted with `cmd notification allow_listener`). */
class MediaListener : NotificationListenerService()

data class Track(
    val packageName: String,
    val app: String,
    val title: String,
    val subtitle: String?,
    val art: ImageBitmap?,
    val artUri: String?,
    val playing: Boolean,
)

/**
 * What's playing on the TV, from the active media sessions. Event driven and only
 * registered while the home screen is started.
 */
class NowPlaying(private val context: Context) {
    var track by mutableStateOf<Track?>(null)
        private set
    var granted by mutableStateOf(true)
        private set

    private val msm = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, MediaListener::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var artFor: MediaMetadata? = null
    private var art: ImageBitmap? = null

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = update()
        override fun onMetadataChanged(metadata: MediaMetadata?) = update()
        override fun onSessionDestroyed() = select(sessions())
    }
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { select(it ?: emptyList()) }

    fun start() {
        try {
            msm.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler)
            granted = true
            select(sessions())
        } catch (_: SecurityException) {
            granted = false
            attach(null)
        }
    }

    fun stop() {
        runCatching { msm.removeOnActiveSessionsChangedListener(sessionsChanged) }
        attach(null)
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    /** Opens the player's own screen; false when the session doesn't offer one. */
    fun open(): Boolean {
        val pi = controller?.sessionActivity ?: return false
        return runCatching {
            val opts = ActivityOptions.makeBasic().apply {
                if (Build.VERSION.SDK_INT >= 34) {
                    @Suppress("DEPRECATION")
                    setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                }
            }
            pi.send(context, 0, null, null, null, null, opts.toBundle())
        }.isSuccess
    }

    private fun sessions(): List<MediaController> = runCatching { msm.getActiveSessions(listener) }.getOrDefault(emptyList())

    private fun select(list: List<MediaController>) {
        val usable = list.filter { it.metadata?.title() != null && show(it.playbackState) }
        attach(usable.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: usable.firstOrNull())
    }

    /** Playing, or paused recently (Spotify keeps a stale paused session from boot). */
    private fun show(s: PlaybackState?): Boolean = when (s?.state) {
        PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING -> true
        PlaybackState.STATE_PAUSED -> SystemClock.elapsedRealtime() - s.lastPositionUpdateTime < STALE_MS
        else -> false
    }

    private fun attach(c: MediaController?) {
        if (c?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = c
            c?.registerCallback(callback, handler)
        }
        update()
    }

    private fun update() {
        val c = controller
        val m = c?.metadata
        val title = m?.title()
        if (c == null || m == null || title == null) {
            track = null
            return
        }
        if (m !== artFor) {
            artFor = m
            art = (m.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON))?.let { shrink(it).asImageBitmap() }
        }
        val pkg = c.packageName
        track = Track(
            packageName = pkg,
            app = runCatching { context.packageManager.run { getApplicationLabel(getApplicationInfo(pkg, 0)).toString() } }.getOrDefault(pkg),
            title = title,
            subtitle = m.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM),
            art = art,
            artUri = m.getString(MediaMetadata.METADATA_KEY_ART_URI)
                ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),
            playing = c.playbackState?.state.let { it == PlaybackState.STATE_PLAYING || it == PlaybackState.STATE_BUFFERING },
        )
    }

    private fun shrink(b: Bitmap): Bitmap {
        val max = 240
        if (b.width <= max && b.height <= max) return b
        val f = max.toFloat() / maxOf(b.width, b.height)
        return b.scale((b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1))
    }

    private fun MediaMetadata.title(): String? =
        (getString(MediaMetadata.METADATA_KEY_TITLE) ?: getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE))?.takeIf { it.isNotBlank() }

    companion object {
        private const val STALE_MS = 30 * 60_000L
    }
}
