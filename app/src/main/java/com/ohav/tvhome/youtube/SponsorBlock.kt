package com.ohav.tvhome.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** Community-marked sponsor segments (sponsor.ajay.app). */
object SponsorBlock {
    data class Segment(val start: Double, val end: Double, val category: String)

    private val CATEGORIES = listOf("sponsor", "selfpromo")

    /**
     * Uses the hash-prefix endpoint, so the server never learns which video is playing:
     * it returns every video whose id hashes to the same 4-character prefix.
     */
    suspend fun segments(videoId: String): List<Segment> = withContext(Dispatchers.IO) {
        val prefix = MessageDigest.getInstance("SHA-256").digest(videoId.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(4)
        val categories = URLEncoder.encode(JSONArray(CATEGORIES).toString(), "UTF-8")
        val conn = URL("https://sponsor.ajay.app/api/skipSegments/$prefix?categories=$categories").openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        try {
            if (conn.responseCode == 404) return@withContext emptyList()
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val videos = JSONArray(body)
            (0 until videos.length()).map { videos.getJSONObject(it) }
                .firstOrNull { it.optString("videoID") == videoId }
                ?.optJSONArray("segments")
                ?.let { segs -> (0 until segs.length()).map { segs.getJSONObject(it) } }
                .orEmpty()
                .filter { it.optString("actionType", "skip") == "skip" && it.optString("category") in CATEGORIES }
                .mapNotNull { s ->
                    val range = s.optJSONArray("segment") ?: return@mapNotNull null
                    Segment(range.getDouble(0), range.getDouble(1), s.optString("category"))
                }
                .filter { it.end - it.start >= 1.0 }
        } finally {
            conn.disconnect()
        }
    }

    fun message(videoId: String, segments: List<Segment>): String = JSONObject()
        .put("t", "segments")
        .put("id", videoId)
        .put("s", JSONArray(segments.map { JSONArray().put(it.start).put(it.end).put(it.category) }))
        .toString()
}
