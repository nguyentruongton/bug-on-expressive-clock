package app.expressiveclock.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime

class AlarmConfirmationDurationTest {
    private val now = ZonedDateTime.parse("2026-11-22T10:00:00Z")
    @Test fun includesMinutesBeyondOneDay() {
        assertEquals("1 day, 13 hours and 33 minutes", alarmConfirmationUntil(now, now.plusDays(1).plusHours(13).plusMinutes(33)))
    }
    @Test fun singularAndPluralUnits() {
        assertEquals("1 day, 1 hour and 1 minute", alarmConfirmationUntil(now, now.plusDays(1).plusHours(1).plusMinutes(1)))
        assertEquals("2 days, 2 hours and 2 minutes", alarmConfirmationUntil(now, now.plusDays(2).plusHours(2).plusMinutes(2)))
    }
    @Test fun omitsZeroUnitsAndJoinsTwoParts() {
        assertEquals("1 day and 33 minutes", alarmConfirmationUntil(now, now.plusDays(1).plusMinutes(33)))
        assertEquals("13 hours and 33 minutes", alarmConfirmationUntil(now, now.plusHours(13).plusMinutes(33)))
        assertEquals("1 minute", alarmConfirmationUntil(now, now.plusMinutes(1)))
        assertEquals("1 day", alarmConfirmationUntil(now, now.plusDays(1)))
    }
    @Test fun shortAndPastDurationsAreSafe() {
        assertEquals("less than a minute", alarmConfirmationUntil(now, now.plusSeconds(59)))
        assertEquals("less than a minute", alarmConfirmationUntil(now, now.minusMinutes(1)))
    }
    @Test fun usesActualElapsedTimeAcrossDst() {
        val before = ZonedDateTime.parse("2026-03-07T12:00:00-05:00[America/New_York]")
        assertEquals("23 hours", alarmConfirmationUntil(before, before.plusDays(1)))
    }
    @Test fun compactFormattingIsUnaffected() {
        assertEquals("1 d 13 h", humanUntil(now, now.plusDays(1).plusHours(13).plusMinutes(33)))
    }
}
