package app.expressiveclock.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Date mode and dismissal equality build on junksidetm's scheduling work in PR #13. */
internal fun Alarm.normalizedSchedule(): Alarm = when {
    invalidSpecificDate != null -> copy(days = emptySet(), enabled = false, snoozedUntilMillis = null)
    specificDate != null -> copy(days = emptySet())
    else -> this
}

internal fun Alarm.withSpecificDate(date: LocalDate?): Alarm = copy(
    specificDate = date, invalidSpecificDate = null,
    days = if (date != null || hasSpecificDate) emptySet() else days,
)

internal fun Alarm.withToggledWeekday(day: DayOfWeek): Alarm = copy(
    specificDate = null, invalidSpecificDate = null,
    days = if (hasSpecificDate) setOf(day) else if (day in days) days - day else days + day,
)

/** Java shifts gaps forward; overlaps explicitly select the earlier occurrence. */
internal fun Alarm.specificDateTime(zone: ZoneId): ZonedDateTime? =
    specificDate?.atTime(time)?.atZone(zone)?.withEarlierOffsetAtOverlap()
        ?.takeIf { it.toLocalDate() == specificDate }

internal enum class AlarmDateIssue { INVALID_DATE, PAST_DATE }

/** Validate the chosen date, not nextFire(): snooze and dismissal are separate occurrences. */
internal fun Alarm.dateIssue(now: ZonedDateTime): AlarmDateIssue? = when {
    invalidSpecificDate != null -> AlarmDateIssue.INVALID_DATE
    specificDate != null && specificDateTime(now.zone) == null -> AlarmDateIssue.INVALID_DATE
    specificDate != null && specificDateTime(now.zone)?.isAfter(now) != true -> AlarmDateIssue.PAST_DATE
    else -> null
}

internal fun Alarm.canEnable(now: ZonedDateTime): Boolean = dateIssue(now) == null

internal fun initialAlarmPickerDate(selected: LocalDate?, now: ZonedDateTime): LocalDate =
    selected ?: now.toLocalDate().plusDays(1)

/** Late queued date callbacks must not ring on another day; an explicit snooze may. */
internal fun Alarm.canDeliverDateOccurrence(at: Long, now: ZonedDateTime): Boolean =
    invalidSpecificDate == null && (specificDate == null || snoozedUntilMillis == at || specificDate == now.toLocalDate())
