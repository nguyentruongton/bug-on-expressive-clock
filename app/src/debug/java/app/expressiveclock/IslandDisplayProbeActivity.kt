package app.expressiveclock

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import app.expressiveclock.overlay.IslandDisplayMonitor

/** Explicit ADB-only test host. Requests a window orientation, never modifies system rotation settings. */
class IslandDisplayProbeActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = if (intent.getStringExtra("orientation") == "landscape")
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val label = TextView(this).apply { textSize = 20f; setPadding(48, 200, 48, 48) }
        setContentView(label)
        val update = object : Runnable {
            override fun run() {
                val display = IslandDisplayMonitor.state.value
                label.text = "Clock debug display test\n${display.width} × ${display.height}\nActive: ${display.profile?.label ?: "Standard phone"}\nRotation: ${display.rotation}\nNo system settings changed"
                handler.postDelayed(this, 500)
            }
        }
        handler.post(update)
    }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }
}
