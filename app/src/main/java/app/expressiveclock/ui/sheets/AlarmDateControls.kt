package app.expressiveclock.ui.sheets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.expressiveclock.R
import app.expressiveclock.core.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Compact Google Clock-style interaction; date scheduling originally contributed in PR #13. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AlarmDateControls(
    draft: Alarm,
    now: ZonedDateTime,
    issue: AlarmDateIssue?,
    onDateChange: (LocalDate?) -> Unit,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val size = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val density = androidx.compose.ui.platform.LocalDensity.current
    val windowWidth = with(density) { size.width.toDp() }
    val windowHeight = with(density) { size.height.toDp() }
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = { showPicker = true }, modifier = Modifier
            .heightIn(min = 48.dp).widthIn(max = (windowWidth - 72.dp).coerceAtLeast(120.dp))) {
            Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(draft.specificDate?.format(formatter) ?: stringResource(R.string.alarm_schedule_action), maxLines = 2)
        }
        if (draft.hasSpecificDate) TextButton(onClick = { onDateChange(null) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.alarm_date_cancel))
        }
    }
    val resolved = draft.specificDateTime(now.zone)
    val notice = when (issue) {
        AlarmDateIssue.INVALID_DATE -> stringResource(R.string.alarm_date_invalid)
        AlarmDateIssue.PAST_DATE -> stringResource(R.string.alarm_date_past)
        null -> if (resolved != null && (resolved.toLocalTime() != draft.time || resolved.toLocalDate() != draft.specificDate)) {
            val actualTime = android.text.format.DateFormat.getTimeFormat(LocalContext.current)
                .format(java.util.Date.from(resolved.toInstant()))
            stringResource(R.string.alarm_date_dst, resolved.toLocalDate().format(formatter), actualTime)
        } else null
    }
    if (notice != null) Text(
        notice, style = MaterialTheme.typography.bodySmall,
        color = if (issue != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
    if (showPicker) {
        // DatePicker encodes calendar dates as UTC midnight, not as device-zone instants.
        val initial = initialAlarmPickerDate(draft.specificDate, now)
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = minOf(now.year, initial.year)..maxOf(now.year + 100, initial.year),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() >= LocalDate.now()
            },
        )
        val chosen = picker.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(enabled = chosen != null && chosen >= now.toLocalDate(), onClick = {
                    chosen?.let(onDateChange)
                    showPicker = false
                }) { Text(stringResource(R.string.alarm_date_done)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.alarm_date_cancel)) }
            },
        ) {
            Box(Modifier.heightIn(max = windowHeight * .7f)
                .verticalScroll(rememberScrollState())) { DatePicker(state = picker) }
        }
    }
}
