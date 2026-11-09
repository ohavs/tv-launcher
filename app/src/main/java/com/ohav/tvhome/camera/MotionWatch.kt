package com.ohav.tvhome.camera

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.ohav.tvhome.data.RtspProbe
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Motion detection without decoding: a camera's compressed stream grows when something moves, so each
 * channel keeps a minimal RTSP session (sub stream, TCP) and only measures the size of its inter frames.
 * Key frames are skipped (their size follows the scene, not the motion). One idle thread per channel.
 */
class MotionWatch(
    urls: List<String>,
    channels: List<Int>,
    private val onMotion: (channel: Int, moving: Boolean) -> Unit,
) {
    private val watchers = urls.zip(channels).map { (url, ch) -> Watcher(url, ch) }

    fun start() = watchers.forEach { it.start() }

    fun stop() = watchers.forEach { it.stop() }

    private inner class Watcher(private val url: String, private val channel: Int) {
        @Volatile private var stopped = false
        @Volatile private var socket: Socket? = null
        private val detector = MotionDetector("ch$channel") { moving -> onMotion(channel, moving) }

        fun start() {
            thread(name = "motion-$channel", isDaemon = true) {
                var backoff = 5_000L
                while (!stopped) {
                    try {
                        RtspFrames(url) { socket = it }.run({ stopped }) { bytes, key ->
                            backoff = 5_000L
                            detector.frame(bytes, key, SystemClock.elapsedRealtime())
                        }
                    } catch (e: Exception) {
                        if (!stopped) Log.w(TAG, "ch$channel: ${e.message}")
                    }
                    detector.reset()
                    if (stopped) break
                    try { Thread.sleep(backoff) } catch (_: InterruptedException) { break }
                    backoff = (backoff * 2).coerceAtMost(60_000L)
                }
            }
        }

        fun stop() {
            stopped = true
            runCatching { socket?.close() }
        }
    }

    companion object { const val TAG = "Motion" }
}

/**
 * Inter-frame bytes per second (2 s windows) against a slowly adapting baseline. Motion starts when a window
 * carries [RATIO]× the baseline (and a minimum absolute jump), and ends after [HOLD_MS] without one.
 */
class MotionDetector(private val name: String, private val onChange: (Boolean) -> Unit) {
    private var windowStart = 0L
    private var windowBytes = 0
    private var frames = 0
    private var keyFrames = 0
    private var baseline = Float.MAX_VALUE
    private var windows = 0
    private var moving = false
    private var lastHit = 0L

    fun reset() {
        windowStart = 0L; windowBytes = 0; frames = 0; keyFrames = 0; baseline = Float.MAX_VALUE; windows = 0
        if (moving) { moving = false; onChange(false) }
    }

    fun frame(bytes: Int, key: Boolean, now: Long) {
        if (windowStart == 0L) windowStart = now
        frames++
        if (key) keyFrames++ else windowBytes += bytes
        val elapsed = now - windowStart
        if (elapsed < WINDOW_MS) return
        // A window with a key frame has one inter frame less: scale back to the full frame count.
        val inter = (frames - keyFrames).coerceAtLeast(1)
        window(windowBytes * 1000f / elapsed * frames / inter, now)
        windowStart = now; windowBytes = 0; frames = 0; keyFrames = 0
    }

    private fun window(rate: Float, now: Long) {
        // Warm-up: the quietest of the first windows (the stream's start is a burst).
        if (windows++ < WARMUP) { baseline = minOf(baseline, rate); return }
        val hit = rate > baseline * RATIO && rate - baseline > MIN_JUMP
        if (DEBUG) Log.d(MotionWatch.TAG, "$name rate=%.0f base=%.0f hit=%b".format(rate, baseline, hit))
        if (hit) lastHit = now
        // The baseline follows the quiet scene (fast down, slow up), not the motion.
        else baseline += (rate - baseline) * (if (rate < baseline) 0.3f else 0.05f)
        val state = hit || (moving && now - lastHit < HOLD_MS)
        if (state != moving) {
            moving = state
            Log.i(MotionWatch.TAG, "$name moving=$state rate=%.0f base=%.0f".format(rate, baseline))
            onChange(state)
        }
    }

    companion object {
        var DEBUG = false
        const val WINDOW_MS = 2000L
        const val WARMUP = 4
        const val RATIO = 2.5f
        const val MIN_JUMP = 3_000f
        const val HOLD_MS = 12_000L
    }
}

/**
 * RTSP over TCP (interleaved), video track only; calls [run]'s callback with each frame's payload size and
 * whether it is a key frame. Supports H.264 and H.265 RTP packetization (single, aggregated, fragmented).
 */
class RtspFrames(url: String, private val onSocket: (Socket?) -> Unit) {
    private val uri = Uri.parse(url)
    private val host = uri.host ?: error("no host")
    private val port = if (uri.port > 0) uri.port else 554
    private val user = uri.userInfo?.substringBefore(':')?.let(Uri::decode).orEmpty()
    private val pass = uri.userInfo?.substringAfter(':', "")?.let(Uri::decode).orEmpty()
    private val base = "rtsp://$host:$port${uri.encodedPath.orEmpty()}${uri.encodedQuery?.let { "?$it" }.orEmpty()}"

    private lateinit var input: InputStream
    private lateinit var output: OutputStream
    private var cseq = 0
    private var challenge: String? = null
    private var session: String? = null

    fun run(stopped: () -> Boolean, onFrame: (bytes: Int, key: Boolean) -> Unit) {
        val socket = Socket()
        onSocket(socket)
        try {
            socket.connect(InetSocketAddress(host, port), 4000)
            socket.soTimeout = 10_000
            input = BufferedInputStream(socket.getInputStream(), 64 * 1024)
            output = socket.getOutputStream()

            val describe = request("DESCRIBE", base, "Accept: application/sdp\r\n")
            val sdp = describe.body
            val contentBase = describe.headers["content-base"] ?: base
            val video = sdp.split("\nm=").firstOrNull { it.startsWith("video") }
                ?: error("no video track")
            val hevc = Regex("rtpmap:\\d+ (H265|HEVC)", RegexOption.IGNORE_CASE).containsMatchIn(video)
            val control = Regex("a=control:(\\S+)").find(video)?.groupValues?.get(1) ?: "*"
            val track = when {
                control.startsWith("rtsp://") -> control
                control == "*" -> contentBase
                else -> contentBase.trimEnd('/') + "/" + control
            }
            val setup = request("SETUP", track, "Transport: RTP/AVP/TCP;unicast;interleaved=0-1\r\n")
            session = setup.headers["session"]?.substringBefore(';') ?: error("no session")
            request("PLAY", contentBase, "Range: npt=0.000-\r\n")

            val frame = FrameSizer(hevc, onFrame)
            var lastKeepAlive = SystemClock.elapsedRealtime()
            val buf = ByteArray(65536)
            while (!stopped()) {
                val b = input.read()
                if (b < 0) throw EOFException("closed")
                if (b == '$'.code) {
                    val ch = readByte()
                    val len = (readByte() shl 8) or readByte()
                    readFully(buf, len)
                    if (ch == 0) frame.rtp(buf, len)
                } else if (b == 'R'.code) {
                    readResponse(firstChar = 'R') // keep-alive answer
                }
                val now = SystemClock.elapsedRealtime()
                if (now - lastKeepAlive > 25_000) {
                    lastKeepAlive = now
                    send("OPTIONS", base, "")
                }
            }
            runCatching { send("TEARDOWN", contentBase, "") }
        } finally {
            onSocket(null)
            runCatching { socket.close() }
        }
    }

    private class Response(val status: Int, val headers: Map<String, String>, val body: String)

    private fun request(method: String, url: String, extra: String): Response {
        send(method, url, extra)
        var r = readResponse()
        if (r.status == 401) {
            challenge = r.headers["www-authenticate"] ?: error("401 without challenge")
            send(method, url, extra)
            r = readResponse()
        }
        if (r.status != 200) error("$method $url → ${r.status}")
        return r
    }

    private fun send(method: String, url: String, extra: String) {
        val req = buildString {
            append("$method $url RTSP/1.0\r\nCSeq: ${++cseq}\r\nUser-Agent: TVHome\r\n")
            challenge?.let { append("Authorization: ${RtspProbe.authorization(method, it, url, user, pass)}\r\n") }
            session?.let { append("Session: $it\r\n") }
            append(extra)
            append("\r\n")
        }
        output.write(req.toByteArray(Charsets.ISO_8859_1))
        output.flush()
    }

    private fun readResponse(firstChar: Char? = null): Response {
        val status = ((firstChar?.toString() ?: "") + readLine()).split(' ').getOrNull(1)?.toIntOrNull() ?: 0
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine()
            if (line.isEmpty()) break
            val i = line.indexOf(':')
            if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        val n = headers["content-length"]?.toIntOrNull() ?: 0
        val body = ByteArray(n).also { readFully(it, n) }
        return Response(status, headers, String(body, Charsets.ISO_8859_1))
    }

    private fun readLine(): String {
        val sb = StringBuilder()
        while (true) {
            val c = readByte()
            if (c == '\n'.code) break
            if (c != '\r'.code) sb.append(c.toChar())
        }
        return sb.toString()
    }

    private fun readByte(): Int = input.read().also { if (it < 0) throw EOFException("closed") }

    private fun readFully(buf: ByteArray, len: Int) {
        var off = 0
        while (off < len) {
            val n = input.read(buf, off, len - off)
            if (n < 0) throw EOFException("closed")
            off += n
        }
    }
}

/** Groups RTP packets into frames (by timestamp / marker bit) and reports each frame's size. */
private class FrameSizer(private val hevc: Boolean, private val onFrame: (bytes: Int, key: Boolean) -> Unit) {
    private var ts = -1L
    private var bytes = 0
    private var key = false

    fun rtp(p: ByteArray, len: Int) {
        if (len < 13) return
        val cc = p[0].toInt() and 0x0f
        val ext = p[0].toInt() and 0x10 != 0
        val marker = p[1].toInt() and 0x80 != 0
        val stamp = ((p[4].toLong() and 0xff) shl 24) or ((p[5].toLong() and 0xff) shl 16) or
            ((p[6].toLong() and 0xff) shl 8) or (p[7].toLong() and 0xff)
        var off = 12 + cc * 4
        if (ext && off + 4 <= len) off += 4 + (((p[off + 2].toInt() and 0xff) shl 8) or (p[off + 3].toInt() and 0xff)) * 4
        if (off >= len) return
        if (ts != -1L && stamp != ts) flush()
        ts = stamp
        bytes += len - off
        if (isKey(p, off, len)) key = true
        if (marker) flush()
    }

    private fun flush() {
        if (bytes > 0) onFrame(bytes, key)
        bytes = 0
        key = false
    }

    private fun isKey(p: ByteArray, off: Int, len: Int): Boolean = if (hevc) {
        when (val t = (p[off].toInt() shr 1) and 0x3f) {
            49 -> off + 2 < len && ((p[off + 2].toInt() and 0x3f) in 16..21)   // FU
            48 -> true                                                        // AP: VPS/SPS/PPS + IRAP
            else -> t in 16..21 || t in 32..34
        }
    } else {
        when (val t = p[off].toInt() and 0x1f) {
            28, 29 -> off + 1 < len && ((p[off + 1].toInt() and 0x1f) == 5)  // FU-A/B
            24 -> true                                                        // STAP-A: SPS/PPS + IDR
            else -> t == 5 || t == 7 || t == 8
        }
    }
}
