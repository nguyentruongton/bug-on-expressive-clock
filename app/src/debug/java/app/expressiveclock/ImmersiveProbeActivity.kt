package app.expressiveclock

import android.app.Activity
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.LinearLayout

/** Debug-only controlled inset test. Changes only this Activity's window, never system settings. */
class ImmersiveProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40,180,40,40) }
        for ((label, hide) in listOf("Normal edge-to-edge" to false, "Immersive fullscreen" to true)) {
            layout.addView(Button(this).apply { text = label; setOnClickListener {
                if (android.os.Build.VERSION.SDK_INT < 30) return@setOnClickListener
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.apply {
                    systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (hide) hide(WindowInsets.Type.systemBars()) else show(WindowInsets.Type.systemBars())
                }
            } })
        }
        setContentView(layout)
    }
}
