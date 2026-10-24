package com.ohav.tvhome.aerial

import android.content.Context
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class)
private object AerialCache {
    @Volatile private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.noBackupFilesDir, "aerials"),
            LeastRecentlyUsedCacheEvictor(1_200L * 1024 * 1024),
            StandaloneDatabaseProvider(context),
        ).also { cache = it }
    }
}

private const val FADE_MS = 1400

/**
 * Full-screen muted aerial video. Never takes audio focus, so music keeps playing.
 * Dips to black between clips; releases the decoder whenever the launcher is not visible.
 * Calls [onUnavailable] when every clip failed (e.g. offline with an empty cache).
 */
@OptIn(UnstableApi::class)
@Composable
fun AerialBackground(onUnavailable: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    val veil = remember { Animatable(1f) }
    var resume by remember { mutableStateOf(-1 to 0L) }

    DisposableEffect(lifecycle) {
        fun start() {
            if (player != null) return
            val p = buildPlayer(context)
            var failures = 0
            p.addListener(object : Player.Listener {
                // Fires again for every new clip, so it also ends each dip to black.
                override fun onRenderedFirstFrame() {
                    failures = 0
                    scope.launch { veil.animateTo(0f, tween(FADE_MS, easing = LinearEasing)) }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (++failures >= p.mediaItemCount) {
                        onUnavailable()
                        return
                    }
                    p.seekToNextMediaItem()
                    p.prepare()
                }
            })
            val (index, pos) = resume
            if (index >= 0) p.seekTo(index, pos)
            p.prepare()
            p.playWhenReady = true
            player = p
        }

        fun stop() {
            player?.let { resume = it.currentMediaItemIndex to it.currentPosition; it.release() }
            player = null
            scope.launch { veil.snapTo(1f) }
        }

        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        onDispose {
            lifecycle.removeObserver(observer)
            stop()
        }
    }

    // Fade to black shortly before each clip ends.
    LaunchedEffect(player) {
        val p = player ?: return@LaunchedEffect
        while (isActive) {
            val left = p.duration - p.currentPosition
            if (p.isPlaying && p.duration != C.TIME_UNSET && left in 1..FADE_MS.toLong() + 200 && veil.targetValue < 1f) {
                veil.animateTo(1f, tween(left.toInt().coerceAtLeast(300), easing = LinearEasing))
            }
            delay(200)
        }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            factory = { SurfaceView(it) },
            update = { sv -> player?.setVideoSurfaceView(sv) },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = veil.value }
                .background(Color.Black)
        )
    }
}

@OptIn(UnstableApi::class)
private fun buildPlayer(context: Context): ExoPlayer {
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(Aerials.USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(10_000)
        .setReadTimeoutMs(15_000)
    val source = CacheDataSource.Factory()
        .setCache(AerialCache.get(context))
        .setUpstreamDataSourceFactory(http)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    val load = DefaultLoadControl.Builder()
        .setBufferDurationsMs(20_000, 60_000, 2_500, 5_000)
        .build()
    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(source))
        .setLoadControl(load)
        .build()
        .apply {
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .setMaxVideoSize(1920, 1080)
                .build()
            volume = 0f
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            repeatMode = Player.REPEAT_MODE_ALL
            shuffleModeEnabled = true
            setMediaItems(Aerials.clips.map { clip ->
                MediaItem.Builder()
                    .setUri(clip.url)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.startMs)
                            .setEndPositionMs(clip.endMs)
                            .build()
                    )
                    .build()
            })
        }
}
