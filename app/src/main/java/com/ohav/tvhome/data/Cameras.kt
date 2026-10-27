package com.ohav.tvhome.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest

/** RTSP path schemes of the common DVR brands; [Tvt] is Provision-ISR. */
enum class DvrFormat {
    Tvt, Hikvision, Dahua;

    fun path(channel: Int, sub: Boolean): String = when (this) {
        Tvt -> "/chID=$channel&streamType=${if (sub) "sub" else "main"}&linkType=tcp"
        Hikvision -> "/Streaming/Channels/${channel}0${if (sub) 2 else 1}"
        Dahua -> "/cam/realmonitor?channel=$channel&subtype=${if (sub) 1 else 0}"
    }
}

data class CameraConfig(
    val host: String,
    val user: String,
    val pass: String,
    val format: DvrFormat,
    /** Channels to show, in order; the first one is on the dashboard. */
    val channels: List<Int>,
) {
    fun url(channel: Int, sub: Boolean): String {
        val auth = if (user.isEmpty()) "" else Uri.encode(user) + ":" + Uri.encode(pass) + "@"
        return "rtsp://$auth$host:554" + format.path(channel, sub)
    }
}

class CameraPrefs(context: Context) {
    private val sp = context.getSharedPreferences("cameras", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(v) = sp.edit().putBoolean("enabled", v).apply()
    var host: String
        get() = sp.getString("host", "10.0.0.14")!!
        set(v) = sp.edit().putString("host", v.trim()).apply()
    var user: String
        get() = sp.getString("user", "")!!
        set(v) = sp.edit().putString("user", v.trim()).apply()
    var pass: String
        get() = sp.getString("pass", "")!!
        set(v) = sp.edit().putString("pass", v).apply()
    var format: DvrFormat?
        get() = runCatching { DvrFormat.valueOf(sp.getString("format", null)!!) }.getOrNull()
        set(v) = sp.edit().putString("format", v?.name).apply()
    /** Channels the last scan found. */
    var found: List<Int>
        get() = ints("found")
        set(v) = sp.edit().putString("found", v.joinToString(",")).apply()
    /** Found channels the user turned off. */
    var hidden: List<Int>
        get() = ints("hidden")
        set(v) = sp.edit().putString("hidden", v.joinToString(",")).apply()

    /** Channel shown on the dashboard (0 = the first visible one). */
    var primary: Int
        get() = sp.getInt("primary", 0)
        set(v) = sp.edit().putInt("primary", v).apply()

    private fun ints(key: String) = sp.getString(key, "")!!.split(',').mapNotNull { it.toIntOrNull() }

    /** What the dashboard shows, or null when cameras are off or not set up yet. */
    fun config(): CameraConfig? {
        if (!enabled) return null
        val format = format ?: return null
        val hidden = hidden
        val primary = primary
        val channels = found.filter { it !in hidden }.sortedBy { if (it == primary) 0 else 1 }
        if (channels.isEmpty()) return null
        return CameraConfig(host, user, pass, format, channels)
    }
}

sealed interface ScanResult {
    data class Found(val format: DvrFormat, val channels: List<Int>) : ScanResult
    data object BadLogin : ScanResult
    data object NoStreams : ScanResult
    data class Unreachable(val msg: String) : ScanResult
}

/** Just enough RTSP (DESCRIBE with Digest/Basic auth) to find which channels a DVR serves. */
object RtspProbe {
    private const val MAX_CHANNELS = 16

    suspend fun scan(host: String, user: String, pass: String): ScanResult = withContext(Dispatchers.IO) {
        var format: DvrFormat? = null
        var badLogin = false
        for (f in DvrFormat.entries) {
            val code = try {
                describe(host, f.path(1, true), user, pass)
            } catch (e: Exception) {
                return@withContext ScanResult.Unreachable(e.message ?: e.javaClass.simpleName)
            }
            if (code == 200) { format = f; break }
            if (code == 401) badLogin = true
        }
        if (format == null) return@withContext if (badLogin) ScanResult.BadLogin else ScanResult.NoStreams
        val channels = (1..MAX_CHANNELS).filter { ch ->
            ch == 1 || runCatching { describe(host, format.path(ch, true), user, pass) }.getOrDefault(0) == 200
        }
        ScanResult.Found(format, channels)
    }

    /** Returns the final status code (401 again means the login was rejected). */
    fun describe(host: String, path: String, user: String, pass: String): Int {
        val uri = "rtsp://$host:554$path"
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, 554), 2500)
            socket.soTimeout = 2500
            val out = socket.getOutputStream()
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
            fun send(cseq: Int, auth: String?): Pair<Int, Map<String, String>> {
                val req = buildString {
                    append("DESCRIBE $uri RTSP/1.0\r\nCSeq: $cseq\r\nAccept: application/sdp\r\nUser-Agent: TVHome\r\n")
                    if (auth != null) append("Authorization: $auth\r\n")
                    append("\r\n")
                }
                out.write(req.toByteArray(Charsets.ISO_8859_1))
                out.flush()
                val status = reader.readLine()?.split(' ')?.getOrNull(1)?.toIntOrNull() ?: 0
                val headers = HashMap<String, String>()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    val i = line.indexOf(':')
                    if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
                }
                // Skip the body (SDP) so the next response starts clean.
                headers["content-length"]?.toIntOrNull()?.let { n -> repeat(n) { reader.read() } }
                return status to headers
            }
            val (first, headers) = send(1, null)
            if (first != 401) return first
            val challenge = headers["www-authenticate"] ?: return first
            return send(2, authorization(challenge, uri, user, pass)).first
        }
    }

    private fun authorization(challenge: String, uri: String, user: String, pass: String): String {
        if (challenge.startsWith("Basic", ignoreCase = true)) {
            return "Basic " + android.util.Base64.encodeToString("$user:$pass".toByteArray(), android.util.Base64.NO_WRAP)
        }
        val params = Regex("(\\w+)=\"([^\"]*)\"").findAll(challenge).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        val realm = params["realm"].orEmpty()
        val nonce = params["nonce"].orEmpty()
        val ha1 = md5("$user:$realm:$pass")
        val ha2 = md5("DESCRIBE:$uri")
        return "Digest username=\"$user\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\", response=\"${md5("$ha1:$nonce:$ha2")}\"" +
            (params["opaque"]?.let { ", opaque=\"$it\"" } ?: "")
    }

    private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
