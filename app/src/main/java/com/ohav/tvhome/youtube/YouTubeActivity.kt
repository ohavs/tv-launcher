package com.ohav.tvhome.youtube

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.ohav.tvhome.R
import com.ohav.tvhome.data.Prefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * YouTube on TV (youtube.com/tv) in a WebView, in its own process (`:yt`) so the launcher's
 * memory stays flat. The page is told it runs on a TV, [SCRIPT] strips ads from its data and
 * skips SponsorBlock segments; remote keys reach it as the keyboard keys the TV app expects.
 */
class YouTubeActivity : ComponentActivity() {
    private lateinit var web: WebView
    private lateinit var toast: TextView
    private var page: JavaScriptReplyProxy? = null
    private var sponsorBlock = true
    private var toastJob: Job? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        runCatching { WebView.setDataDirectorySuffix("yt") }
        super.onCreate(savedInstanceState)
        sponsorBlock = intent.getBooleanExtra(EXTRA_SPONSOR_BLOCK, true)
        // Dev switch, adb only: `setprop log.tag.YouTube DEBUG` → DevTools (tools/cdp.py) + console in logcat.
        if (debug) WebView.setWebContentsDebuggingEnabled(true)

        web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = USER_AGENT
            webChromeClient = Chrome()
            webViewClient = Client()
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web, "TVHomeBridge", ORIGINS) { _, message, _, isMainFrame, reply ->
                if (isMainFrame) onMessage(message.data ?: return@addWebMessageListener, reply)
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, assets.open(SCRIPT).bufferedReader().use { it.readText() }, ORIGINS)
        }

        toast = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(0xCC202124.toInt())
            }
            visibility = View.GONE
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(web, FrameLayout.LayoutParams(-1, -1))
            addView(toast, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                topMargin = dp(28); marginEnd = dp(36)
            })
        })
        web.requestFocus()
        web.loadUrl(startUrl(intent))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sponsorBlock = intent.getBooleanExtra(EXTRA_SPONSOR_BLOCK, sponsorBlock)
        intent.getStringExtra(EXTRA_VIDEO)?.takeIf { VIDEO_ID.matches(it) }?.let { web.loadUrl(startUrl(intent)) }
    }

    override fun onStart() {
        super.onStart()
        web.onResume()
        web.resumeTimers()
    }

    override fun onStop() {
        // The WebView would keep playing behind other apps.
        page?.postMessage("""{"t":"pause"}""")
        web.onPause()
        web.pauseTimers()
        super.onStop()
    }

    override fun onDestroy() {
        (web.parent as? FrameLayout)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }

    /** Back is Escape for the TV app (it decides between closing a panel and "Exit YouTube?"). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            web.dispatchKeyEvent(KeyEvent(event.downTime, event.eventTime, event.action, KeyEvent.KEYCODE_ESCAPE, event.repeatCount, event.metaState))
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun onMessage(data: String, reply: JavaScriptReplyProxy) {
        page = reply
        val m = runCatching { JSONObject(data) }.getOrNull() ?: return
        when (m.optString("t")) {
            "video" -> {
                val id = m.optString("id").takeIf { VIDEO_ID.matches(it) } ?: return
                if (!sponsorBlock) return
                lifecycleScope.launch {
                    val segs = runCatching { SponsorBlock.segments(id) }.onFailure { Log.w(TAG, "sponsorblock", it) }.getOrNull()
                    if (!segs.isNullOrEmpty()) reply.postMessage(SponsorBlock.message(id, segs))
                }
            }
            "skipped" -> showToast(
                getString(if (m.optString("c") == "selfpromo") R.string.yt_skipped_selfpromo else R.string.yt_skipped_sponsor)
            )
            "exit" -> finish()
        }
    }

    private fun showToast(text: String) {
        toast.text = text
        toast.alpha = 0f
        toast.visibility = View.VISIBLE
        toast.animate().alpha(1f).setDuration(180).start()
        toastJob?.cancel()
        toastJob = lifecycleScope.launch {
            delay(2500)
            toast.animate().alpha(0f).setDuration(300).withEndAction { toast.visibility = View.GONE }.start()
        }
    }

    private val debug get() = Log.isLoggable(TAG, Log.DEBUG)

    private fun dp(v: Int) =(v * resources.displayMetrics.density).toInt()

    private inner class Chrome : WebChromeClient() {
        // Without this, a <video> with no frame yet shows WebView's grey "play" poster, stretched.
        override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            if (debug) Log.d(TAG, "console: ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            // Widevine for films/paid content.
            val drm = request.resources.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
            if (drm.isNotEmpty() && request.origin.host?.endsWith("youtube.com") == true) request.grant(drm.toTypedArray())
            else request.deny()
        }
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val host = request.url.host.orEmpty()
            return !(host.endsWith("youtube.com") || host.endsWith("google.com") || host.endsWith("youtube-nocookie.com"))
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Log.w(TAG, "renderer gone (crash=${detail.didCrash()})")
            finish()
            return true
        }
    }

    companion object {
        private const val TAG = "YouTube"
        private const val SCRIPT = "youtube/tvhome.js"
        private const val EXTRA_VIDEO = "v"
        private const val EXTRA_SPONSOR_BLOCK = "sponsorBlock"
        private val ORIGINS = setOf("https://www.youtube.com")
        private val VIDEO_ID = Regex("[\\w-]{11}")

        /** A Cobalt (YouTube on Android TV) user agent: youtube.com/tv only serves the TV app to TVs. */
        private const val USER_AGENT = "Mozilla/5.0 (Linux armeabi-v7a; Android 14) Cobalt/25.lts.30.1034958-gold " +
            "(unlike Gecko) v8/8.8.278.17-jit gles Starboard/15, Google_ATV_kirkwood_2024/UTTK.260317.003 " +
            "(google, Google TV Streamer) com.google.android.youtube.tv/5.30.340"

        private fun startUrl(intent: Intent): String {
            val id = intent.getStringExtra(EXTRA_VIDEO)?.takeIf { VIDEO_ID.matches(it) }
            return "https://www.youtube.com/tv" + (id?.let { "#/watch?v=$it" } ?: "")
        }

        fun intent(context: Context, videoId: String? = null, sponsorBlock: Boolean = true): Intent =
            Intent(context, YouTubeActivity::class.java)
                .putExtra(EXTRA_SPONSOR_BLOCK, sponsorBlock)
                .apply { if (videoId != null) putExtra(EXTRA_VIDEO, videoId) }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        const val YOUTUBE_PACKAGE = "com.google.android.youtube.tv"

        /** An intent for the official YouTube app (or a YouTube link) → ours, same video, when enabled. */
        fun redirect(context: Context, intent: Intent): Intent {
            val prefs = Prefs(context)
            if (!prefs.ownYouTube) return intent
            val target = intent.component?.packageName ?: intent.`package`
            val link = intent.data?.let { it.scheme == "vnd.youtube" || it.host?.let { h -> h.endsWith("youtube.com") || h == "youtu.be" } == true } == true
            if (target != YOUTUBE_PACKAGE && !(target == null && link)) return intent
            return intent(context, videoId(intent.data), prefs.sponsorBlock)
        }

        /** Video id from a YouTube watch link (youtube.com/watch?v=, youtu.be/, vnd.youtube:). */
        fun videoId(uri: Uri?): String? {
            uri ?: return null
            val id = when {
                uri.scheme == "vnd.youtube" -> uri.schemeSpecificPart?.substringBefore('?')
                uri.host?.endsWith("youtu.be") == true -> uri.lastPathSegment
                else -> runCatching { uri.getQueryParameter("v") }.getOrNull()
            }
            return id?.takeIf { VIDEO_ID.matches(it) }
        }
    }
}
