package app.expressiveclock.alarm

import app.expressiveclock.core.Alarm
import app.expressiveclock.data.ClockStore
import app.expressiveclock.data.DesktopStateExporter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime

interface AlarmSchedulerGateway {
    fun schedule(alarm: Alarm)
    fun cancel(id: Long)
}

object SystemdAlarmSchedulerGateway : AlarmSchedulerGateway {
    override fun schedule(alarm: Alarm) = SystemdAlarmScheduler.schedule(alarm)
    override fun cancel(id: Long) = SystemdAlarmScheduler.cancel(id)
}

/**
 * Single source of truth for desktop alarm ring / snooze / dismiss state transitions.
 * Shared by [app.expressiveclock.ui.ClockViewModel], standalone `--ring`, and `--action` CLI calls.
 */
object DesktopAlarmCoordinator {

    private val transitionMutex = Mutex()

    suspend fun defaultExportState(store: ClockStore) {
        DesktopStateExporter.export(
            alarms = store.alarmsNow(),
            timer = store.timerNow(),
            stopwatch = store.stopwatchNow(),
        )
    }

    /**
     * Computes a snooze wall-clock deadline aligned to whole seconds (`000` ms) so it matches
     * `systemd`'s `OnCalendar=yyyy-MM-dd HH:mm:ss` precision without sub-second drift.
     */
    fun computeSnoozeUntilMillis(nowMillis: Long, snoozeMinutes: Int): Long {
        val mins = snoozeMinutes.coerceAtLeast(1).toLong()
        val candidate = ((nowMillis + mins * 60_000L) / 1_000L) * 1_000L
        val minFuture = ((nowMillis / 1_000L) + 1L) * 1_000L
        return candidate.coerceAtLeast(minFuture)
    }

    /**
     * Consumes the currently firing occurrence as soon as ringing starts (matching Android's
     * `AlarmReceiver.fire`): disarms one-shot alarms, clears any pending snooze, and records
     * `skippedOccurrenceDate` for regular repeating fires without overwriting it on a
     * midnight-crossing snooze fire.
     */
    suspend fun onAlarmFired(
        store: ClockStore,
        alarmId: Long,
        now: ZonedDateTime = ZonedDateTime.now(),
        scheduler: AlarmSchedulerGateway = SystemdAlarmSchedulerGateway,
        exportState: suspend (ClockStore) -> Unit = ::defaultExportState,
    ): Alarm? = transitionMutex.withLock {
        var firedSnapshot: Alarm? = null
        val updated = store.updateAlarms { list ->
            list.map { alarm ->
                if (alarm.id != alarmId || !alarm.enabled) {
                    alarm
                } else {
                    firedSnapshot = alarm
                    when {
                        alarm.isOneShot -> alarm.copy(
                            enabled = false,
                            snoozedUntilMillis = null,
                            skippedOccurrenceMillis = null,
                            skippedOccurrenceDate = null,
                        )
                        alarm.snoozedUntilMillis != null -> alarm.copy(
                            snoozedUntilMillis = null,
                        )
                        else -> alarm.copy(
                            snoozedUntilMillis = null,
                            skippedOccurrenceDate = now.toLocalDate(),
                        )
                    }
                }
            }
        }
        updated.firstOrNull { it.id == alarmId }?.let { alarm ->
            if (alarm.enabled) scheduler.schedule(alarm) else scheduler.cancel(alarm.id)
        }
        exportState(store)
        firedSnapshot
    }

    /**
     * Re-arms the alarm (including one-shots disarmed on fire) for `snoozeMinutes` from [nowMillis].
     */
    suspend fun snooze(
        store: ClockStore,
        alarmId: Long,
        nowMillis: Long = System.currentTimeMillis(),
        scheduler: AlarmSchedulerGateway = SystemdAlarmSchedulerGateway,
        exportState: suspend (ClockStore) -> Unit = ::defaultExportState,
    ): Alarm? = transitionMutex.withLock {
        val snoozeMins = store.settingsNow().alarms.snoozeMinutes
        val snoozeUntil = computeSnoozeUntilMillis(nowMillis, snoozeMins)
        val updated = store.updateAlarms { list ->
            list.map { alarm ->
                if (alarm.id == alarmId) {
                    alarm.copy(enabled = true, snoozedUntilMillis = snoozeUntil)
                } else {
                    alarm
                }
            }
        }
        val target = updated.firstOrNull { it.id == alarmId }
        if (target != null) {
            scheduler.schedule(target)
        }
        exportState(store)
        target
    }

    /**
     * Dismisses an alarm, disarming one-shots and clearing any pending snooze while preserving
     * an already-recorded `skippedOccurrenceDate` on repeating alarms.
     */
    suspend fun dismiss(
        store: ClockStore,
        alarmId: Long,
        now: ZonedDateTime = ZonedDateTime.now(),
        scheduler: AlarmSchedulerGateway = SystemdAlarmSchedulerGateway,
        exportState: suspend (ClockStore) -> Unit = ::defaultExportState,
    ): Alarm? = transitionMutex.withLock {
        val updated = store.updateAlarms { list ->
            list.map { alarm ->
                if (alarm.id != alarmId) {
                    alarm
                } else if (alarm.isOneShot) {
                    alarm.copy(enabled = false, snoozedUntilMillis = null)
                } else if (alarm.snoozedUntilMillis != null) {
                    alarm.copy(snoozedUntilMillis = null)
                } else {
                    alarm.copy(
                        snoozedUntilMillis = null,
                        skippedOccurrenceDate = alarm.skippedOccurrenceDate ?: now.toLocalDate(),
                    )
                }
            }
        }
        val target = updated.firstOrNull { it.id == alarmId }
        if (target != null) {
            if (target.enabled) scheduler.schedule(target) else scheduler.cancel(target.id)
        }
        exportState(store)
        target
    }
}
