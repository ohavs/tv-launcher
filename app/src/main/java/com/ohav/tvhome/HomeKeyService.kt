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

/**
 * Google TV sends Home to its own launcher no matter which app holds the Home role, and that
 * launcher must stay enabled (the Google Home panel and the dashboard depend on its services).
 * This service takes over the Home key: short press opens TV Home, long press opens the
 * Google TV dashboard. If the stock home still surfaces (e.g. after boot) we bounce to TV Home.
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

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName == STOCK_PACKAGE && event.className?.toString() == STOCK_HOME) goHome()
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

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, HomeKeyService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}
