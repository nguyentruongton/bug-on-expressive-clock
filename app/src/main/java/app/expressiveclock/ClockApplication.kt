package app.expressiveclock

import android.app.Application
import app.expressiveclock.overlay.AppLifecycleTracker
import app.expressiveclock.overlay.OverlayManager

class ClockApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(AppLifecycleTracker)
        // USER_UNLOCKED is a registered-receiver broadcast, not a manifest subscription.
        // Register even in a process created for LOCKED_BOOT_COMPLETED; boot and unlock
        // restoration are idempotent and share the same persisted source of truth.
        androidx.core.content.ContextCompat.registerReceiver(
            this, app.expressiveclock.alarm.BootReceiver(),
            android.content.IntentFilter(android.content.Intent.ACTION_USER_UNLOCKED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        OverlayManager.init(this)
    }
}
