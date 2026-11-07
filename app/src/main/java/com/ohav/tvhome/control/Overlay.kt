package com.ohav.tvhome.control

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** What a ComposeView needs when it lives in a window outside any activity. */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    fun resume() {
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

/**
 * A Compose window drawn by the accessibility service on top of whatever app is showing.
 * It doesn't pause that app (a video keeps playing). [focusable] windows take the remote's keys.
 */
class ComposeOverlay(
    private val service: AccessibilityService,
    private val params: WindowManager.LayoutParams,
    content: @Composable () -> Unit,
) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val owner = OverlayOwner()
    private val view = ComposeView(service).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setViewTreeViewModelStoreOwner(owner)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent(content)
    }

    fun show() {
        owner.resume()
        wm.addView(view, params)
        if (params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0) view.requestFocus()
    }

    fun dismiss() {
        runCatching { wm.removeView(view) }
        owner.destroy()
    }

    companion object {
        fun params(width: Int, height: Int, gravity: Int, focusable: Boolean, dim: Float = 0f) = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) or
                (if (dim > 0f) WindowManager.LayoutParams.FLAG_DIM_BEHIND else 0),
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.gravity = gravity
            dimAmount = dim
        }

        /** The panel's side: the end of the reading direction (left in Hebrew). */
        fun endGravity(service: AccessibilityService): Int {
            val rtl = service.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL
            return (if (rtl) Gravity.LEFT else Gravity.RIGHT) or Gravity.TOP
        }
    }
}
