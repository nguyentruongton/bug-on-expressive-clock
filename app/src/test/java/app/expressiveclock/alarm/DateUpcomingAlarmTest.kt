package app.expressiveclock.alarm

import app.expressiveclock.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class DateUpcomingAlarmTest {
    private val now = ZonedDateTime.of(2026, 11, 24, 6, 0, 0, 0, ZoneId.systemDefault())
    private val alarm = Alarm(44, LocalTime.of(7, 0), specificDate = now.toLocalDate().plusDays(2))
    @Test fun originalDateMatchesButDateTimeEditsAndDisableRejectStaleCallback() {
        val at = alarm.nextFire(now)!!.toInstant().toEpochMilli()
        assertTrue(UpcomingAlarm.matches(alarm, at))
        assertFalse(UpcomingAlarm.matches(alarm.copy(specificDate = alarm.specificDate!!.plusDays(1)), at))
        assertFalse(UpcomingAlarm.matches(alarm.copy(time = LocalTime.of(8, 0)), at))
        assertFalse(UpcomingAlarm.matches(alarm.copy(enabled = false), at))
        assertFalse(UpcomingAlarm.matches(alarm.copy(skippedOccurrenceMillis = at), at))
        assertFalse(UpcomingAlarm.matches(alarm.copy(skippedOccurrenceDate = alarm.specificDate), at))
    }
    @Test fun snoozedDateMatchesOnlyNewAbsoluteOccurrence() {
        val at = alarm.nextFire(now)!!.toInstant().toEpochMilli()
        val snoozed = alarm.copy(snoozedUntilMillis = at + 24 * 3600000L, skippedOccurrenceDate = alarm.specificDate)
        assertFalse(UpcomingAlarm.matches(snoozed, at))
        assertTrue(UpcomingAlarm.matches(snoozed, at + 24 * 3600000L))
        assertFalse(UpcomingAlarm.matches(snoozed, at + 24 * 3600000L + 60000))
    }
}
