package com.ohav.tvhome

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.ohav.tvhome.control.ControlCenter
import com.ohav.tvhome.control.SleepTimer
import com.ohav.tvhome.data.Prefs
import com.ohav.tvhome.data.RemoteKey
import com.ohav.tvhome.media.NowPlaying
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
        controlCenter.close()
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }
    private var netflixDown = false
    private var netflixLong = false
    private val netflixLongPress = Runnable {
        netflixLong = true
        controlCenter.toggle()
    }
    lateinit var controlCenter: ControlCenter
        private set

    /**
     * Screen off (by us or by hand) ends the sleep timer. Otherwise its uptime-based delay would
     * resume after the device wakes up and turn the TV off at a random moment.
     */
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            SleepTimer.set(0)
            controlCenter.close()
        }
    }

    override fun onServiceConnected() {
        controlCenter = ControlCenter(this)
        SleepTimer.attach(this)
        instance = this
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        runCatching { unregisterReceiver(screenOff) }
        if (::controlCenter.isInitialized) controlCenter.close()
        SleepTimer.detach()
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (SleepTimer.onKey(event)) return true
        if (event.keyCode == KeyEvent.KEYCODE_MACRO_1) return onGoogleHomeKey(event)
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_3) return onAppKey(event, RemoteKey.YouTube)
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_4) return onNetflixKey(event)
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (!homeDown) {
                homeDown = true
                longFired = false
                handler.postDelayed(longPress, LONG_PRESS_MS)
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                if (homeDown && !longFired) {
                    controlCenter.close()
                    goHome()
                }
                homeDown = false
            }
        }
        return true
    }

    private fun onGoogleHomeKey(event: KeyEvent): Boolean {
        val open = Intent(SMART_HOME_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (open.resolveActivity(packageManager) == null) return false
        if (event.action == KeyEvent.ACTION_UP) {
            controlCenter.close()
            val active = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
            if (active == SMART_HOME_PACKAGE) performGlobalAction(GLOBAL_ACTION_BACK) else runCatching { startActivity(open) }
        }
        return true
    }

    /** Netflix: short press opens its app (or whatever it's mapped to), long press the control center. */
    private fun onNetflixKey(event: KeyEvent): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (!netflixDown) {
                netflixDown = true
                netflixLong = false
                handler.postDelayed(netflixLongPress, LONG_PRESS_MS)
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(netflixLongPress)
                if (netflixDown && !netflixLong) launchFor(RemoteKey.Netflix)
                netflixDown = false
            }
        }
        return true
    }

    /**
     * The remote's YouTube / Netflix buttons. The stock launcher's global-key listener starts the app
     * from the background, which Android blocks unless its own home is visible (like MACRO_1), so we
     * start it. YouTube opens our ad-free player when that's enabled.
     */
    private fun onAppKey(event: KeyEvent, key: RemoteKey): Boolean {
        if (event.action == KeyEvent.ACTION_UP) launchFor(key)
        return true
    }

    private fun launchFor(key: RemoteKey) {
        val prefs = Prefs(this)
        val pkg = prefs.keyTarget(key)
        val intent = if (pkg == YouTubeActivity.YOUTUBE_PACKAGE && prefs.ownYouTube) {
            YouTubeActivity.intent(this, sponsorBlock = prefs.sponsorBlock)
        } else {
            (packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg))
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
        }
        controlCenter.close()
        runCatching { startActivity(intent) }
    }

    /** Sleep timer ran out: pause playback, then sleep (HDMI-CEC turns the TV off with us). */
    fun sleepNow() {
        controlCenter.close()
        NowPlaying.pauseAll(this)
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) }, 300)
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
        private const val SMART_HOME_ACTION = "com.google.android.libraries.tv.smarthome.intent.action.OPEN_SMART_HOME"

        /** The running service (same process as the launcher), for the dashboard's control-center button. */
        var instance: HomeKeyService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, HomeKeyService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}
