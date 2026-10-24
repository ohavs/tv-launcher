package com.ohav.tvhome.data

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.tv.TvContract
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ShelfItem(
    val key: String,
    val title: String,
    val subtitle: String?,
    val imageUri: String?,
    val aspect: Float,
    val intentUri: String?,
    val progress: Float?,
)

/**
 * Reads "Top Shelf" content that apps publish to the system TV provider:
 * Watch Next (continue watching) first, then the app's preview channels.
 * Without ACCESS_ALL_EPG_DATA the provider rejects selections, so we filter in code.
 */
class ShelfRepository(private val context: Context) {
    private val cache = HashMap<String, Pair<Long, List<ShelfItem>>>()

    fun cached(pkg: String): List<ShelfItem>? = cache[pkg]?.second

    suspend fun itemsFor(pkg: String): List<ShelfItem> = withContext(Dispatchers.IO) {
        cache[pkg]?.let { (t, items) -> if (SystemClock.elapsedRealtime() - t < TTL_MS) return@withContext items }
        val items = (watchNext(pkg) + previewPrograms(pkg)).distinctBy { it.title + it.imageUri }.take(MAX_ITEMS)
        Log.i(TAG, "shelf $pkg: ${items.size} items")
        cache[pkg] = SystemClock.elapsedRealtime() to items
        items
    }

    /** Marks everything stale but keeps it for instant display while refreshing. */
    fun invalidate() = cache.replaceAll { _, v -> 0L to v.second }

    private fun watchNext(pkg: String): List<ShelfItem> = query(TvContract.WatchNextPrograms.CONTENT_URI, PROGRAM_COLUMNS + WATCH_COLUMNS) { c ->
        if (c.str(TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME) != pkg) return@query null
        val duration = c.long(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS)
        val position = c.long(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS)
        programOf(c, "w", if (duration > 0 && position > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else null)
    }.sortedByDescending { it.key }

    private fun previewPrograms(pkg: String): List<ShelfItem> {
        val channels = query(TvContract.Channels.CONTENT_URI, arrayOf(
            TvContract.Channels._ID, TvContract.Channels.COLUMN_PACKAGE_NAME, TvContract.Channels.COLUMN_TYPE,
        )) { c ->
            if (c.str(TvContract.Channels.COLUMN_PACKAGE_NAME) != pkg) return@query null
            if (c.str(TvContract.Channels.COLUMN_TYPE) != TvContract.Channels.TYPE_PREVIEW) return@query null
            c.long(TvContract.Channels._ID)
        }
        return channels.flatMap { id ->
            query(TvContract.buildPreviewProgramsUriForChannel(id), PROGRAM_COLUMNS) { c -> programOf(c, "p", null) }
        }
    }

    private fun programOf(c: Cursor, prefix: String, progress: Float?): ShelfItem? {
        val title = c.str(TvContract.PreviewPrograms.COLUMN_TITLE)?.takeIf { it.isNotBlank() } ?: return null
        val poster = c.str(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI)
        val thumb = c.str(TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI)
        val aspectCol = c.getColumnIndex(TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO)
        val aspect = if (aspectCol >= 0 && !c.isNull(aspectCol)) ASPECTS[c.getInt(aspectCol)] ?: WIDE else WIDE
        return ShelfItem(
            key = prefix + c.long(TvContract.PreviewPrograms._ID),
            title = title,
            subtitle = c.str(TvContract.PreviewPrograms.COLUMN_EPISODE_TITLE) ?: c.str(TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION),
            imageUri = poster ?: thumb,
            aspect = if (poster == null && thumb != null) WIDE else aspect,
            intentUri = c.str(TvContract.PreviewPrograms.COLUMN_INTENT_URI),
            progress = progress,
        )
    }

    private fun <T> query(uri: Uri, projection: Array<String>, map: (Cursor) -> T?): List<T> = try {
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            buildList { while (c.moveToNext()) map(c)?.let { add(it) } }
        } ?: emptyList()
    } catch (e: Exception) {
        Log.w(TAG, "query $uri failed: $e")
        emptyList()
    }

    private fun Cursor.str(col: String): String? = getColumnIndex(col).let { if (it < 0 || isNull(it)) null else getString(it) }
    private fun Cursor.long(col: String): Long = getColumnIndex(col).let { if (it < 0 || isNull(it)) 0L else getLong(it) }

    companion object {
        private const val TAG = "TvHomeShelf"
        private const val TTL_MS = 5 * 60_000L
        private const val MAX_ITEMS = 20
        private const val WIDE = 16f / 9f
        private val ASPECTS = mapOf(
            TvContract.PreviewPrograms.ASPECT_RATIO_16_9 to 16f / 9f,
            TvContract.PreviewPrograms.ASPECT_RATIO_3_2 to 3f / 2f,
            TvContract.PreviewPrograms.ASPECT_RATIO_4_3 to 4f / 3f,
            TvContract.PreviewPrograms.ASPECT_RATIO_1_1 to 1f,
            TvContract.PreviewPrograms.ASPECT_RATIO_2_3 to 2f / 3f,
            5 to 1f / 1.441f, // ASPECT_RATIO_MOVIE_POSTER (not in the framework constants)
        )
        private val PROGRAM_COLUMNS = arrayOf(
            TvContract.PreviewPrograms._ID,
            TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME,
            TvContract.PreviewPrograms.COLUMN_TITLE,
            TvContract.PreviewPrograms.COLUMN_EPISODE_TITLE,
            TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION,
            TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
            TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI,
            TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
            TvContract.PreviewPrograms.COLUMN_INTENT_URI,
        )
        private val WATCH_COLUMNS = arrayOf(
            TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
        )

        fun launchIntent(item: ShelfItem): Intent? = item.intentUri?.let {
            runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }.getOrNull()
        }
    }
}
