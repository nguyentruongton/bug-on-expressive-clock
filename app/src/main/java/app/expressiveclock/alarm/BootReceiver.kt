package app.expressiveclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.expressiveclock.data.ClockStore
import app.expressiveclock.data.credentialStorageAvailable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reschedules all active alarms and timer notifications following device boot or system clock changes.
 *
 * ## Android 15 (API 35+) Compliance
 *
 * This receiver handles `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, and
 * `MY_PACKAGE_REPLACED`, plus a process-registered `USER_UNLOCKED` callback.
 *
 * To comply with Android 15 foreground service launch restrictions, this receiver contains **no references or
 * calls to `startForegroundService()`**. Its sole purpose is to re-arm alarms via `AlarmManager.setAlarmClock()`,
 * which gives each alarm an exact time callback with system-granted execution permissions when it later fires.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Preferences are credential-protected. Do not crash during locked boot;
        // BOOT_COMPLETED reschedules once the user unlocks that storage.
        if (!context.credentialStorageAvailable()) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                reschedule(app)
            } finally {
                pending.finish()
            }
        }
    }

    internal suspend fun reschedule(context: Context) = restorationMutex.withLock {
        val store = ClockStore(context)
        Notifications.ensureChannels(context)
        AlarmScheduler.scheduleAll(context, store.alarmsNow())
        // A reboot resets elapsedRealtime, so any stored deadline is meaningless. Rebuild the
        // timer's notification from what survived and let the store's own clamp decide.
        store.timerNow()?.let { TimerScheduler.sync(context, it) }
        android.util.Log.i("ClockRestore", "Restored persisted schedules after ${android.os.SystemClock.elapsedRealtime()}ms uptime")
    }

    companion object {
        private val restorationMutex = Mutex()
    }
}
