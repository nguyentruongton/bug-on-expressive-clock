package app.expressiveclock.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import app.expressiveclock.R
import app.expressiveclock.data.ClockStore
import app.expressiveclock.data.SILENT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The ringer.
 *
 * ## Why this one *is* a foreground service
 *
 * Unlike the timer and stopwatch notifications, which are a timestamp the platform renders, a
 * ringing alarm is genuine ongoing work: a looping `MediaPlayer` and a repeating vibration that
 * must keep running with the screen off and the app long since swapped out. That is what a
 * foreground service is for. It is started from an exact alarm's broadcast, which grants the
 * temporary allowlist that makes a background start legal.
 *
 * The declared type is `mediaPlayback`, an honest description of what it does; there is no
 * alarm-specific type in the enum.
 *
 * ## Audio
 *
 * `USAGE_ALARM` is the whole ballgame. It routes to the alarm stream, so the alarm sounds at alarm
 * volume rather than media volume, respecting system interruption policy. `setLooping` because an alarm that plays a four-second ringtone once is not one.
 *
 * ## Giving up
 *
 * [app.expressiveclock.data.AlarmSettings.silenceAfterMinutes] is enforced here rather than by the
 * UI, because by then there may be no UI. When it expires the service stops itself, which is also
 * what makes the notification disappear.
 */
class AlarmService : Service() {

    private val sound by lazy { AlarmAudio(this) }
    private var vibrator: Vibrator? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val starts = Mutex()
    private var active = false
    private var silenceJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifications.ensureChannels(this)
        when (intent?.action) {
            ACTION_START -> startRinging(intent.getLongExtra(AlarmReceiver.EXTRA_ID, -1L), intent.getLongExtra(UpcomingAlarm.AT, 0), startId)
            ACTION_START_TIMER -> startTimerChime(startId)
            else -> stopSelf(startId)
        }
        // Not sticky: a restarted ringer with no intent has no idea which alarm it is, and an
        // alarm that resurrects itself after the user killed the app is a bug report.
        return START_NOT_STICKY
    }

    private fun startRinging(id: Long, at: Long, startId: Int) {
        scope.launch {
            starts.withLock {
                val store = ClockStore(this@AlarmService)
                val alarm = store.alarmsNow().firstOrNull { it.id == id }
                if (alarm == null || (at > 0 && alarm.skippedOccurrenceMillis != at)) {
                    // A stale start must neither resurrect a deleted alarm nor stop another ringer.
                    if (!active) stopSelf(startId)
                    return@launch
                }
                val settings = store.settingsNow()
                ring(ringingNotification(this@AlarmService, id, alarm.label.ifBlank { "Alarm" }),
                    alarm.soundUri, alarm.vibrate, ALARM_PATTERN, settings.alarms.silenceAfterMinutes)
            }
        }
    }

    private fun startTimerChime(startId: Int) {
        scope.launch {
            starts.withLock {
                val store = ClockStore(this@AlarmService)
                if (store.timerNow()?.state != app.expressiveclock.core.TimerState.FINISHED) {
                    if (!active) stopSelf(startId)
                    return@launch
                }
                val settings = store.settingsNow()
                ring(timerNotification(this@AlarmService), settings.timers.soundUri,
                    settings.timers.vibrate, TIMER_PATTERN, settings.alarms.silenceAfterMinutes)
            }
        }
    }

    private fun ring(notification: android.app.Notification, uri: String?, shouldVibrate: Boolean,
                     pattern: LongArray, silenceMinutes: Int) {
        silenceJob?.cancel()
        sound.stop()
        vibrator?.cancel()
        foreground(notification)
        active = true
        if (uri != SILENT) sound.play(uri)
        if (shouldVibrate) vibrate(pattern)
        // This timeout belongs to the active ringer, not a pending replacement's load job.
        silenceJob = scope.launch {
            if (silenceMinutes <= 0) return@launch
            delay(silenceMinutes * 60_000L)
            stopSelf()
        }
    }

    private fun foreground(n: android.app.Notification) {
        // The two-argument API uses the type declared in the manifest on Android 10+.
        // It also avoids a shared R8 API-29 outline with unrelated widget/boot helpers.
        startForeground(Notifications.ID_RINGING, n)
    }

    private fun vibrate(pattern: LongArray) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService<Vibrator>()
        }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        // Index 0 restarts the pattern from its first element, i.e. repeat forever.
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0), attrs)
    }

    override fun onDestroy() {
        active = false
        silenceJob?.cancel()
        sound.stop()
        vibrator?.cancel()
        scope.cancel()
        NotificationManagerCompat.from(this).cancel(Notifications.ID_RINGING)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "app.expressiveclock.RING"
        const val ACTION_START_TIMER = "app.expressiveclock.RING_TIMER"

        /** Half a second on, half off: insistent without being a klaxon. */
        private val ALARM_PATTERN = longArrayOf(0, 500, 500)
        private val TIMER_PATTERN = longArrayOf(0, 300, 200, 300, 900)

        fun stop(context: Context) {
            context.stopService(Intent(context, AlarmService::class.java))
        }

        private fun ringingNotification(context: Context, id: Long, label: String) =
            NotificationCompat.Builder(context, Notifications.CHANNEL_ALARM)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle(label)
                .setContentText("Alarm")
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                // Android 14+ may allow ongoing notifications to be swiped away.
                // Treat that explicit dismissal like the visible Dismiss action.
                .setDeleteIntent(Notifications.broadcast(context, ClockActionReceiver.ACTION_DISMISS, 12, id))
                // The full-screen intent is what wakes the device and puts the dismiss screen in
                // front of a locked phone. It degrades to a heads-up banner where the permission
                // is not held. That is still usable, since both actions are on the notification.
                .setFullScreenIntent(AlarmRingActivity.intent(context, id, label), true)
                .setContentIntent(AlarmRingActivity.intent(context, id, label))
                .addAction(0, "Snooze", Notifications.broadcast(context, ClockActionReceiver.ACTION_SNOOZE, 10, id))
                .addAction(0, "Dismiss", Notifications.broadcast(context, ClockActionReceiver.ACTION_DISMISS, 11, id))
                .build()

        private fun timerNotification(context: Context) =
            NotificationCompat.Builder(context, Notifications.CHANNEL_ALARM)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle("Timer")
                .setContentText("Time's up")
                .setDeleteIntent(Notifications.broadcast(context, ClockActionReceiver.ACTION_TIMER_CANCEL, 25))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setContentIntent(Notifications.openApp(context, Notifications.TAB_TIMERS))
                .addAction(0, "Dismiss", Notifications.broadcast(context, ClockActionReceiver.ACTION_TIMER_CANCEL, 24))
                .build()
    }
}
