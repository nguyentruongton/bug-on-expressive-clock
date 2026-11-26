package app.expressiveclock.alarm

import app.expressiveclock.core.Alarm
import app.expressiveclock.data.ClockStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopAlarmCoordinatorTest {

    private class RecordingScheduler : AlarmSchedulerGateway {
        val scheduled = mutableListOf<Alarm>()
        val cancelled = mutableListOf<Long>()

        override fun schedule(alarm: Alarm) {
            scheduled += alarm
        }

        override fun cancel(id: Long) {
            cancelled += id
        }
    }

    private fun tempStore(
        initialAlarms: List<Alarm>,
        snoozeMinutes: Int = 10,
    ): ClockStore {
        val cfgDir = Files.createTempDirectory("ec-coord-cfg")
        val dataDir = Files.createTempDirectory("ec-coord-data")
        val store = ClockStore(configDirOverride = cfgDir, dataDirOverride = dataDir)
        runBlocking {
            store.update { it.copy(alarms = it.alarms.copy(snoozeMinutes = snoozeMinutes)) }
            store.updateAlarms { initialAlarms }
        }
        return store
    }

    @Test
    fun computeSnoozeUntilMillisAlignsToWholeSecondsAndClampsMinimumOneMinute() {
        val nowMillis = 1795489565_847L // ...05.847s
        val tenMinDue = DesktopAlarmCoordinator.computeSnoozeUntilMillis(nowMillis, snoozeMinutes = 10)
        assertEquals(0L, tenMinDue % 1_000L)
        assertEquals(1795490165_000L, tenMinDue)

        val zeroMinDue = DesktopAlarmCoordinator.computeSnoozeUntilMillis(nowMillis, snoozeMinutes = 0)
        val negativeMinDue = DesktopAlarmCoordinator.computeSnoozeUntilMillis(nowMillis, snoozeMinutes = -5)
        assertEquals(1795489625_000L, zeroMinDue)
        assertEquals(1795489625_000L, negativeMinDue)
        assertTrue(zeroMinDue >= ((nowMillis / 1_000L) + 1L) * 1_000L)
    }

    @Test
    fun oneShotAlarmDisarmsOnFireReArmsOnSnoozeAndDisarmsOnDismiss() = runBlocking {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val oneShot = Alarm(
            id = 2L,
            time = LocalTime.of(10, 6),
            label = "Standup",
            days = emptySet(),
            enabled = true,
        )
        val store = tempStore(listOf(oneShot), snoozeMinutes = 10)
        val scheduler = RecordingScheduler()
        var exports = 0

        val fireNow = ZonedDateTime.of(2026, 11, 24, 10, 6, 0, 0, zone)
        val fired = DesktopAlarmCoordinator.onAlarmFired(
            store = store,
            alarmId = 2L,
            now = fireNow,
            scheduler = scheduler,
            exportState = { exports++ },
        )
        assertNotNull(fired)
        val afterFire = store.alarmsNow().single()
        assertFalse(afterFire.enabled)
        assertNull(afterFire.snoozedUntilMillis)
        assertEquals(listOf(2L), scheduler.cancelled)
        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(1, exports)

        val snoozeClickAt = ZonedDateTime.of(2026, 11, 24, 10, 6, 12, 750_000_000, zone)
        val snoozed = DesktopAlarmCoordinator.snooze(
            store = store,
            alarmId = 2L,
            nowMillis = snoozeClickAt.toInstant().toEpochMilli(),
            scheduler = scheduler,
            exportState = { exports++ },
        )
        assertNotNull(snoozed)
        val afterSnooze = store.alarmsNow().single()
        assertTrue(afterSnooze.enabled)
        val expectedDue = ZonedDateTime.of(2026, 11, 24, 10, 16, 12, 0, zone)
        assertEquals(expectedDue.toInstant().toEpochMilli(), afterSnooze.snoozedUntilMillis)
        assertEquals(1, scheduler.scheduled.size)
        assertEquals(expectedDue, scheduler.scheduled.single().nextFire(snoozeClickAt))
        assertEquals(2, exports)

        val dismissAt = ZonedDateTime.of(2026, 11, 24, 10, 16, 15, 0, zone)
        DesktopAlarmCoordinator.dismiss(
            store = store,
            alarmId = 2L,
            now = dismissAt,
            scheduler = scheduler,
            exportState = { exports++ },
        )
        val afterDismiss = store.alarmsNow().single()
        assertFalse(afterDismiss.enabled)
        assertNull(afterDismiss.snoozedUntilMillis)
        assertEquals(listOf(2L, 2L), scheduler.cancelled)
        assertEquals(3, exports)
    }

    @Test
    fun repeatingAlarmSnoozedAcrossMidnightPreservesNextEveningOccurrence() = runBlocking {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val daily = Alarm(
            id = 7L,
            time = LocalTime.of(23, 55),
            label = "Night",
            days = DayOfWeek.entries.toSet(),
            enabled = true,
        )
        val store = tempStore(listOf(daily), snoozeMinutes = 10)
        val scheduler = RecordingScheduler()

        val monFire = ZonedDateTime.of(2026, 11, 23, 23, 55, 0, 0, zone)
        DesktopAlarmCoordinator.onAlarmFired(
            store = store,
            alarmId = 7L,
            now = monFire,
            scheduler = scheduler,
            exportState = {},
        )
        val afterMonFire = store.alarmsNow().single()
        assertTrue(afterMonFire.enabled)
        assertEquals(LocalDate.of(2026, 11, 23), afterMonFire.skippedOccurrenceDate)

        val monSnoozeAt = ZonedDateTime.of(2026, 11, 23, 23, 55, 10, 0, zone)
        DesktopAlarmCoordinator.snooze(
            store = store,
            alarmId = 7L,
            nowMillis = monSnoozeAt.toInstant().toEpochMilli(),
            scheduler = scheduler,
            exportState = {},
        )
        val afterSnooze = store.alarmsNow().single()
        val tueSnoozeFire = ZonedDateTime.of(2026, 11, 24, 0, 5, 10, 0, zone)
        assertEquals(tueSnoozeFire.toInstant().toEpochMilli(), afterSnooze.snoozedUntilMillis)
        assertEquals(LocalDate.of(2026, 11, 23), afterSnooze.skippedOccurrenceDate)

        DesktopAlarmCoordinator.onAlarmFired(
            store = store,
            alarmId = 7L,
            now = tueSnoozeFire,
            scheduler = scheduler,
            exportState = {},
        )
        val afterSnoozeFire = store.alarmsNow().single()
        assertNull(afterSnoozeFire.snoozedUntilMillis)
        assertEquals(LocalDate.of(2026, 11, 23), afterSnoozeFire.skippedOccurrenceDate)
        val expectedTueNight = ZonedDateTime.of(2026, 11, 24, 23, 55, 0, 0, zone)
        assertEquals(
            expectedTueNight,
            afterSnoozeFire.nextFire(ZonedDateTime.of(2026, 11, 24, 0, 5, 11, 0, zone)),
        )

        DesktopAlarmCoordinator.dismiss(
            store = store,
            alarmId = 7L,
            now = ZonedDateTime.of(2026, 11, 24, 0, 5, 20, 0, zone),
            scheduler = scheduler,
            exportState = {},
        )
        val afterDismiss = store.alarmsNow().single()
        assertNull(afterDismiss.snoozedUntilMillis)
        assertEquals(LocalDate.of(2026, 11, 23), afterDismiss.skippedOccurrenceDate)
        assertEquals(
            expectedTueNight,
            afterDismiss.nextFire(ZonedDateTime.of(2026, 11, 24, 0, 5, 21, 0, zone)),
        )
    }

    @Test
    fun concurrentOnAlarmFiredAndSnoozeNeverCancelsSnoozeTimer() = runBlocking {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val oneShot = Alarm(
            id = 9L,
            time = LocalTime.of(10, 6),
            label = "Race",
            days = emptySet(),
            enabled = true,
        )
        val store = tempStore(listOf(oneShot), snoozeMinutes = 10)
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        val cancelStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slowScheduler = object : AlarmSchedulerGateway {
            override fun schedule(alarm: Alarm) {
                events += "schedule:${alarm.snoozedUntilMillis != null}"
            }

            override fun cancel(id: Long) {
                cancelStarted.complete(Unit)
                Thread.sleep(80)
                events += "cancel:$id"
            }
        }

        val fireNow = ZonedDateTime.of(2026, 11, 24, 10, 6, 0, 0, zone)
        val snoozeNow = ZonedDateTime.of(2026, 11, 24, 10, 6, 1, 0, zone)

        val fireJob = async(Dispatchers.IO) {
            DesktopAlarmCoordinator.onAlarmFired(
                store = store,
                alarmId = 9L,
                now = fireNow,
                scheduler = slowScheduler,
                exportState = {},
            )
        }
        cancelStarted.await()
        val snoozeJob = async(Dispatchers.IO) {
            DesktopAlarmCoordinator.snooze(
                store = store,
                alarmId = 9L,
                nowMillis = snoozeNow.toInstant().toEpochMilli(),
                scheduler = slowScheduler,
                exportState = {},
            )
        }
        fireJob.await()
        snoozeJob.await()

        val finalAlarm = store.alarmsNow().single()
        assertTrue(finalAlarm.enabled, "One-shot must remain re-armed after snooze")
        assertNotNull(finalAlarm.snoozedUntilMillis, "Snooze deadline must be persisted")
        assertEquals(
            listOf("cancel:9", "schedule:true"),
            events,
            "onAlarmFired cancel must complete before snooze schedule runs: $events",
        )
    }
}
