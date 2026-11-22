package app.expressiveclock.core

import java.time.Duration
import java.time.ZonedDateTime

/** Full-word alarm confirmation only; compact duration labels elsewhere stay unchanged. */
fun alarmConfirmationUntil(from: ZonedDateTime, to: ZonedDateTime): String {
    val minutes = Duration.between(from, to).toMinutes().coerceAtLeast(0)
    fun unit(value: Long, name: String) = "$value $name${if (value == 1L) "" else "s"}"
    val parts = buildList {
        val days = minutes / 1440
        val hours = minutes / 60 % 24
        val mins = minutes % 60
        if (days > 0) add(unit(days, "day"))
        if (hours > 0) add(unit(hours, "hour"))
        if (mins > 0) add(unit(mins, "minute"))
    }
    return when (parts.size) {
        0 -> "less than a minute"
        1 -> parts.single()
        else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
    }
}
