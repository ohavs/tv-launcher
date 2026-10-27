package com.ohav.tvhome.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Tiny image loader for Top Shelf artwork (http(s) and content:// URIs). */
object ImageLoader {
    private val cache = object : LruCache<String, ImageBitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val gate = Semaphore(4)

    fun cached(uri: String): ImageBitmap? = cache.get(uri)

    suspend fun load(context: Context, uri: String, maxWidth: Int): ImageBitmap? {
        cache.get(uri)?.let { return it }
        return gate.withPermit {
            withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = read(context, uri) ?: return@runCatching null
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
                }.getOrNull()?.also { cache.put(uri, it) }
            }
        }
    }

    private fun read(context: Context, uri: String): ByteArray? {
        val u = Uri.parse(uri)
        return when (u.scheme) {
            "http", "https" -> {
                val conn = URL(uri).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 10000
                conn.instanceFollowRedirects = true
                try {
                    if (conn.responseCode !in 200..299) null else conn.inputStream.use { it.readBytes() }
                } finally {
                    conn.disconnect()
                }
            }
            else -> context.contentResolver.openInputStream(u)?.use { it.readBytes() }
        }
    }
}
