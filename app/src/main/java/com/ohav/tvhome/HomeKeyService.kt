package com.ohav.tvhome

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.ohav.tvhome.data.Prefs
import com.ohav.tvhome.youtube.YouTubeActivity

/**
 * Google TV sends Home to its own launcher no matter which app holds the Home role, and that
 * launcher must stay enabled (the Google Home panel and the dashboard depend on its services).
 * This service takes over the Home key: short press opens TV Home, long press opens the
 * Google TV dashboard. If the stock home still surfaces (e.g. after boot) we bounce to TV Home.
 *
 * It also handles the remote's Google Home button (MACRO_1): the stock launcher's background
 * service would open the panel, but Android blocks that launch while its home isn't visible.
 * Accessibility services are exempt, so we open (or close) the panel ourselves. Same for the
 * YouTube and Netflix buttons.
 */
class HomeKeyService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var homeDown = false
    private var longFired = false
    private val longPress = Runnable {
        longFired = true
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_MACRO_1) return onGoogleHomeKey(event)
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_3) return onAppKey(event, YouTubeActivity.YOUTUBE_PACKAGE)
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_4) return onAppKey(event, NETFLIX_PACKAGE)
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (!homeDown) {
                homeDown = true
                longFired = false
                handler.postDelayed(longPress, LONG_PRESS_MS)
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                if (homeDown && !longFired) goHome()
                homeDown = false
            }
        }
        return true
    }

    private fun onGoogleHomeKey(event: KeyEvent): Boolean {
        val open = Intent(SMART_HOME_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (open.resolveActivity(packageManager) == null) return false
        if (event.action == KeyEvent.ACTION_UP) {
            val active = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
            if (active == SMART_HOME_PACKAGE) performGlobalAction(GLOBAL_ACTION_BACK) else runCatching { startActivity(open) }
        }
        return true
    }

    /**
     * The remote's YouTube / Netflix buttons. The stock launcher's global-key listener starts the app
     * from the background, which Android blocks unless its own home is visible (like MACRO_1), so we
     * start it. YouTube opens our ad-free player when that's enabled.
     */
    private fun onAppKey(event: KeyEvent, pkg: String): Boolean {
        val prefs = Prefs(this)
        val intent = if (pkg == YouTubeActivity.YOUTUBE_PACKAGE && prefs.ownYouTube) {
            YouTubeActivity.intent(this, sponsorBlock = prefs.sponsorBlock)
        } else {
            packageManager.getLeanbackLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return false
        }
        if (event.action == KeyEvent.ACTION_UP) runCatching { startActivity(intent) }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString().orEmpty()
        if (pkg == STOCK_PACKAGE && cls == STOCK_HOME) goHome()
    }

    override fun onInterrupt() = Unit

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setClass(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        )
    }

    companion object {
        private const val LONG_PRESS_MS = 600L
        const val STOCK_PACKAGE = "com.google.android.apps.tv.launcherx"
        const val STOCK_HOME = "com.google.android.apps.tv.launcherx.home.HomeActivity"
        private const val SMART_HOME_PACKAGE = "com.google.android.apps.tv.dreamx"
        private const val NETFLIX_PACKAGE = "com.netflix.ninja"
        private const val SMART_HOME_ACTION = "com.google.android.libraries.tv.smarthome.intent.action.OPEN_SMART_HOME"

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, HomeKeyService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}
