package app.expressiveclock.core

import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.TimeZone

class DateAlarmTest {
    private val zone = ZoneId.of("America/New_York")
    private val now = ZonedDateTime.of(2026, 11, 24, 6, 0, 0, 0, zone)
    private val alarm = Alarm(42, LocalTime.of(7, 0), specificDate = now.toLocalDate())

    @Test fun todayFutureAndExactInstant() {
        assertNull(alarm.dateIssue(now))
        assertEquals(Instant.parse("2026-11-24T12:00:00Z"), alarm.nextFire(now)!!.toInstant())
    }
    @Test fun todayPastAndExactNowCannotBeArmed() {
        for (at in listOf(now.withHour(7), now.withHour(8))) {
            assertEquals(AlarmDateIssue.PAST_DATE, alarm.dateIssue(at))
            assertFalse(alarm.canEnable(at))
            assertNull(alarm.nextFire(at))
        }
    }
    @Test fun saveCrossingTheMinuteRevalidates() {
        val before = now.withHour(6).withMinute(59).withSecond(59)
        assertNull(alarm.dateIssue(before))
        assertNotNull(alarm.dateIssue(before.plusSeconds(1)))
    }
    @Test fun monthsAndLeapDayKeepCalendarDate() {
        for (date in listOf(LocalDate.of(2027, 6, 18), LocalDate.of(2028, 2, 29))) {
            assertEquals(date, alarm.copy(specificDate = date).nextFire(now)!!.toLocalDate())
        }
    }
    @Test fun chooseDateClearsWeekdaysAndClearReturnsToOnce() {
        val weekly = alarm.withSpecificDate(null).copy(days = setOf(DayOfWeek.MONDAY))
        val dated = weekly.withSpecificDate(now.toLocalDate().plusDays(3))
        assertTrue(dated.days.isEmpty())
        assertTrue(dated.isOneShot)
        val cleared = dated.withSpecificDate(null)
        assertFalse(cleared.hasSpecificDate)
        assertTrue(cleared.days.isEmpty())
        assertNotNull(cleared.nextFire(now))
    }
    @Test fun chooseWeekdayAfterDateStartsFreshRecurringRule() {
        val mixed = alarm.copy(days = setOf(DayOfWeek.TUESDAY))
        val monday = mixed.withToggledWeekday(DayOfWeek.MONDAY)
        assertNull(monday.specificDate)
        assertEquals(setOf(DayOfWeek.MONDAY), monday.days)
        assertFalse(monday.isOneShot)
    }
    @Test fun persistenceBoundaryNormalizesConflictingModes() {
        assertTrue(alarm.copy(days = DayOfWeek.entries.toSet()).normalizedSchedule().days.isEmpty())
    }
    @Test fun entireSkippedCalendarDateCannotRollIntoAnotherDate() {
        val skipped = alarm.copy(specificDate = LocalDate.of(2011, 12, 30))
        val apia = ZoneId.of("Pacific/Apia")
        assertNull(skipped.specificDateTime(apia))
        assertNotNull(skipped.dateIssue(ZonedDateTime.of(2011, 12, 1, 0, 0, 0, 0, apia)))
    }
    @Test fun disabledDateHasNoOccurrence() { assertNull(alarm.copy(enabled = false).nextFire(now)) }
    @Test fun expiredDateNeverRollsToTomorrow() {
        assertNull(alarm.nextFire(now.plusDays(1)))
        assertFalse(alarm.canEnable(now.plusDays(1)))
        assertEquals(now.toLocalDate(), alarm.specificDate)
    }
    @Test fun snoozeCrossesMidnightAndOriginalDateButDisabledDoesNotRing() {
        val at = now.plusDays(1).toInstant().toEpochMilli()
        val snoozed = alarm.copy(snoozedUntilMillis = at, skippedOccurrenceDate = alarm.specificDate)
        val late = now.withHour(23)
        assertEquals(at, snoozed.nextFire(late)!!.toInstant().toEpochMilli())
        assertNull(snoozed.copy(enabled = false).nextFire(late))
    }
    @Test fun springGapUsesAdjustedTimeAndOverlapUsesEarlierOffset() {
        val gap = alarm.copy(specificDate = LocalDate.of(2027, 3, 14), time = LocalTime.of(2, 30))
        assertEquals(LocalTime.of(3, 30), gap.nextFire(now)!!.toLocalTime())
        val overlap = alarm.copy(specificDate = LocalDate.of(2027, 11, 7), time = LocalTime.of(1, 30))
        assertEquals(ZoneOffset.ofHours(-4), overlap.nextFire(now)!!.offset)
        val afterFirst = Instant.parse("2027-11-07T05:45:00Z").atZone(zone)
        assertNull(overlap.nextFire(afterFirst)) // no second occurrence
    }
    @Test fun nonHourGapShiftsByActualGapLength() {
        val lordHowe = ZoneId.of("Australia/Lord_Howe")
        val gap = alarm.copy(specificDate = LocalDate.of(2027, 10, 3), time = LocalTime.of(2, 15))
        assertEquals(LocalTime.of(2, 45), gap.specificDateTime(lordHowe)!!.toLocalTime())
    }
    @Test fun timezoneChangesInSameProcessChangeInstantNotCalendarChoice() {
        val previous = TimeZone.getDefault()
        try {
            val inst = now.toInstant()
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            val a = alarm.copy(specificDate = LocalDate.of(2026, 12, 18))
            val first = a.nextFire(inst.atZone(ZoneId.systemDefault()))!!
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val second = a.nextFire(inst.atZone(ZoneId.systemDefault()))!!
            assertNotEquals(first.toInstant(), second.toInstant())
            assertEquals(first.toLocalDateTime(), second.toLocalDateTime())
        } finally { TimeZone.setDefault(previous) }
    }
    @Test fun dismissedDateIsPreservedAcrossLabelChangesAndZones() {
        val skipped = alarm.copy(skippedOccurrenceMillis = alarm.nextFire(now)!!.toInstant().toEpochMilli(),
            skippedOccurrenceDate = alarm.specificDate)
        val edited = skipped.copy(label = "Edited").preservingDismissal(skipped)
        assertNull(edited.nextFire(now))
        assertNull(edited.nextFire(now.withZoneSameLocal(ZoneId.of("Asia/Tokyo"))))
        assertNull(skipped.copy(specificDate = alarm.specificDate!!.plusDays(1)).preservingDismissal(skipped).skippedOccurrenceDate)
    }
    @Test fun pickerDefaultsToLocalTomorrowAndExistingDate() {
        val la = Instant.parse("2026-11-25T02:00:00Z").atZone(ZoneId.of("America/Los_Angeles"))
        assertEquals(LocalDate.of(2026, 11, 25), initialAlarmPickerDate(null, la))
        assertEquals(alarm.specificDate, initialAlarmPickerDate(alarm.specificDate, la))
    }
    @Test fun lateCallbackCannotRingAfterTheChosenCalendarDate() {
        val at = alarm.nextFire(now)!!.toInstant().toEpochMilli()
        assertTrue(alarm.canDeliverDateOccurrence(at, now.withHour(8)))
        assertFalse(alarm.canDeliverDateOccurrence(at, now.plusDays(1)))
        assertFalse(alarm.canDeliverDateOccurrence(at, now.minusDays(1)))
        val snoozed = alarm.copy(snoozedUntilMillis = at + 24 * 3600000L)
        assertTrue(snoozed.canDeliverDateOccurrence(at + 24 * 3600000L, now.plusDays(1)))
        assertFalse(snoozed.canDeliverDateOccurrence(at, now.plusDays(1)))
        assertTrue(alarm.copy(specificDate = null).canDeliverDateOccurrence(at, now.plusDays(1)))
    }
    @Test fun malformedDateCannotSnoozeOrEnable() {
        val bad = alarm.copy(specificDate = null, invalidSpecificDate = "not-a-date", snoozedUntilMillis = Long.MAX_VALUE)
        assertNull(bad.nextFire(now))
        assertFalse(bad.canEnable(now))
        assertFalse(bad.normalizedSchedule().enabled)
    }
}
