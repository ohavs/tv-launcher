package com.ohav.tvhome.control

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohav.tvhome.HomeKeyService
import com.ohav.tvhome.R
import kotlinx.coroutines.delay

/**
 * Turns the TV off after a while. A minute before, a notice shows over whatever is playing:
 * OK adds 15 minutes, Back cancels. Lives in the accessibility service's process, which is always running.
 */
object SleepTimer {
    val PRESETS = listOf(0, 15, 30, 45, 60, 90, 120)
    private const val WARN_MS = 60_000L
    private const val EXTEND_MIN = 15

    /** `elapsedRealtime` when the TV turns off; 0 = no timer. */
    var endsAt by mutableLongStateOf(0L)
        private set
    /** The preset last chosen (highlighted in the control center). */
    var preset by mutableIntStateOf(0)
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var service: HomeKeyService? = null
    private var warning: ComposeOverlay? = null

    fun attach(s: HomeKeyService) {
        service = s
    }

    fun detach() {
        set(0)
        service = null
    }

    fun remainingMs(): Long = if (endsAt == 0L) 0L else (endsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    fun set(minutes: Int) {
        handler.removeCallbacksAndMessages(null)
        hideWarning()
        preset = minutes
        if (minutes <= 0) {
            endsAt = 0L
            return
        }
        val total = minutes * 60_000L
        endsAt = SystemClock.elapsedRealtime() + total
        handler.postDelayed({ showWarning() }, (total - WARN_MS).coerceAtLeast(0L))
        handler.postDelayed({ fire() }, total)
    }

    /** Remote keys while the notice is up. Returns true when the key was ours. */
    fun onKey(event: KeyEvent): Boolean {
        if (warning == null) return false
        val ok = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER ||
            event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        val back = event.keyCode == KeyEvent.KEYCODE_BACK
        if (!ok && !back) return false
        if (event.action == KeyEvent.ACTION_UP) set(if (ok) EXTEND_MIN else 0)
        return true
    }

    private fun showWarning() {
        val s = service ?: return
        if (warning != null) return
        val params = ComposeOverlay.params(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, focusable = false,
        ).apply { y = (48 * s.resources.displayMetrics.density).toInt() }
        warning = ComposeOverlay(s, params) { SleepNotice() }.also { it.show() }
    }

    private fun hideWarning() {
        warning?.dismiss()
        warning = null
    }

    private fun fire() {
        hideWarning()
        endsAt = 0L
        preset = 0
        service?.sleepNow()
    }
}

@Composable
private fun SleepNotice() {
    var seconds by androidx.compose.runtime.remember { mutableIntStateOf(((SleepTimer.remainingMs() + 999) / 1000).toInt()) }
    LaunchedEffect(Unit) {
        while (true) {
            seconds = ((SleepTimer.remainingMs() + 999) / 1000).toInt()
            delay(1000 - SystemClock.elapsedRealtime() % 1000)
        }
    }
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .background(Color(0xF0121519), shape)
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .padding(horizontal = 34.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.sleep_warning, seconds), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.sleep_warning_hint),
            color = Color.White.copy(alpha = 0.65f), fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp),
        )
    }
}
