package app.expressiveclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.expressiveclock.core.canDeliverDateOccurrence
import app.expressiveclock.core.TimerState
import app.expressiveclock.data.ClockStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles exact alarm firings and timer expiries from `AlarmManager`.
 *
 * ## `goAsync`
 *
 * Reading DataStore suspends and `onReceive` runs on the main thread with a hard deadline. The
 * pending result keeps the process alive across the coroutine; `finish()` must be reached on every
 * path or the receiver leaks and the system eventually kills the app for it.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> fire(app, intent.getLongExtra(EXTRA_ID, -1L), intent.getLongExtra(UpcomingAlarm.AT, 0))
                    UpcomingAlarm.SHOW, UpcomingAlarm.SKIP -> UpcomingAlarm.receive(app, intent)
                    ACTION_TIMER_EXPIRED -> timerExpired(app)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire(context: Context, id: Long, at: Long) {
        val store = ClockStore(context)
        if (at > System.currentTimeMillis() + 1000) return
        var firing: app.expressiveclock.core.Alarm? = null
        var expiredDate = false
        val cleared = store.updateAlarms { list -> list.map { alarm ->
            if (alarm.id != id || !alarm.enabled || (at > 0 && !UpcomingAlarm.matches(alarm, at))) alarm
            else if (!alarm.canDeliverDateOccurrence(at, java.time.ZonedDateTime.now())) {
                expiredDate = true
                alarm.copy(enabled = false, snoozedUntilMillis = null,
                    skippedOccurrenceMillis = at.takeIf { it > 0 }, skippedOccurrenceDate = alarm.specificDate)
            } else {
                firing = alarm
                alarm.copy(enabled = !alarm.isOneShot, snoozedUntilMillis = null, skippedOccurrenceMillis = at.takeIf { it > 0 } ?: alarm.skippedOccurrenceMillis,
                    skippedOccurrenceDate = if (at > 0) java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        else java.time.LocalDate.now())
            }
        } }
        if (expiredDate) { AlarmScheduler.cancel(context, id); return }
        val alarm = firing ?: return
        UpcomingAlarm.hide(context,id)
        cleared.firstOrNull { it.id == id }?.let { AlarmScheduler.schedule(context, it) }

        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmService::class.java)
                .setAction(AlarmService.ACTION_START)
                .putExtra(EXTRA_ID, alarm.id)
                .putExtra(UpcomingAlarm.AT, at),
        )
    }

    private suspend fun timerExpired(context: Context) {
        val store = ClockStore(context)
        val timer = store.timerNow() ?: return
        if (timer.state != TimerState.RUNNING ||
            timer.remaining(android.os.SystemClock.elapsedRealtime()).toMillis() > 1000) return
        // Mark the timer as FINISHED so the full-screen finished screen is presented in the app
        store.putTimer(timer.copy(state = TimerState.FINISHED))
        Notifications.hideTimer(context)
        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmService::class.java).setAction(AlarmService.ACTION_START_TIMER),
        )
    }

    companion object {
        const val ACTION_FIRE = "app.expressiveclock.FIRE"
        const val ACTION_TIMER_EXPIRED = "app.expressiveclock.TIMER_EXPIRED"
        const val EXTRA_ID = "id"
    }
}
