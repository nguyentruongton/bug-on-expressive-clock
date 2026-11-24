package app.expressiveclock.ui.sheets

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.expressiveclock.core.Alarm
import app.expressiveclock.core.dateIssue
import app.expressiveclock.data.WeekStart
import app.expressiveclock.data.order
import kotlinx.coroutines.launch
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditSheet(
    initial: Alarm,
    weekStart: WeekStart,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: ((Long) -> Unit)?,
    onCancelStarted: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val is24Hour = app.expressiveclock.ui.rememberSystem24HourFormat()

    var hour by rememberSaveable(initial.id) { mutableStateOf(initial.time.hour) }
    var minute by rememberSaveable(initial.id) { mutableStateOf(initial.time.minute) }
    var pm by rememberSaveable(initial.id) { mutableStateOf(initial.time.hour >= 12) }
    var editingHour by rememberSaveable(initial.id) { mutableStateOf(true) }
    var label by rememberSaveable(initial.id) { mutableStateOf(initial.label) }
    var dayMask by rememberSaveable(initial.id) { androidx.compose.runtime.mutableIntStateOf(initial.days.fold(0) { mask, d -> mask or (1 shl d.ordinal) }) }
    val days = java.time.DayOfWeek.entries.filter { dayMask and (1 shl it.ordinal) != 0 }.toSet()
    var dateText by rememberSaveable(initial.id) { mutableStateOf(initial.specificDate?.toString() ?: initial.invalidSpecificDate) }
    val specificDate = dateText?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
    var vibrate by rememberSaveable(initial.id) { mutableStateOf(initial.vibrate) }
    var soundUri by rememberSaveable(initial.id) { mutableStateOf(initial.soundUri) }

    val openSoundSelector = rememberAlarmSoundSelector(soundUri) { soundUri = it }

    val soundLabel by androidx.compose.runtime.produceState(initialValue = "Default", key1 = soundUri, key2 = context) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            app.expressiveclock.ui.sheets.alarmSoundTitle(context, soundUri)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.alarmEditorImeLift(),
        contentWindowInsets = { WindowInsets.navigationBars },
        shape = AlarmSheetShape,
        // Keep the large alarm numerals visually subordinate to the editor.
        scrimColor = androidx.compose.material3.MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
        dragHandle = null,
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
    ) {
        AlarmEditorDragHandle(sheetState, onDismiss)
        AlarmPickerSheetContent(
            modal = true,
            title = "Edit Alarm",
            hour = hour,
            minute = minute,
            is24Hour = is24Hour,
            pm = pm,
            editingHour = editingHour,
            label = label,
            days = days,
            weekOrder = weekStart.order(systemFirstDay()),
            vibrate = vibrate,
            soundLabel = soundLabel,
            specificDate = specificDate,
            invalidSpecificDate = dateText != null && specificDate == null,
            onSpecificDateChange = { date ->
                dateText = date?.toString()
                if (date != null || initial.hasSpecificDate) dayMask = 0
            },
            showDelete = onDelete != null,
            onHourChange = { hour = it },
            onMinuteChange = { minute = it },
            onPmChange = { pm = it },
            onEditingHourChange = { editingHour = it },
            onLabelChange = { label = it },
            onToggleDay = { day ->
                dayMask = if (dateText != null) (1 shl day.ordinal) else dayMask xor (1 shl day.ordinal)
                dateText = null
            },
            onVibrateChange = { vibrate = it },
            onSoundClick = openSoundSelector,
            onCancel = {
                // Clear the card marker immediately, while retaining this sheet until hide ends.
                onCancelStarted()
                scope.launch {
                    sheetState.hide()
                }.invokeOnCompletion {
                    onDismiss()
                }
            },
            onSave = {
                val saveHour = if (is24Hour) hour else if (pm) (hour % 12) + 12 else hour % 12
                val updated = initial.copy(
                    time = LocalTime.of(saveHour, minute),
                    label = label.trim(),
                    days = days,
                    specificDate = specificDate,
                    invalidSpecificDate = dateText?.takeIf { specificDate == null },
                    enabled = true,
                    vibrate = vibrate,
                    soundUri = soundUri,
                )
                if (updated.dateIssue(java.time.ZonedDateTime.now()) == null) {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onSave(updated) }
                }
            },
            onDelete = {
                scope.launch {
                    sheetState.hide()
                }.invokeOnCompletion {
                    onDelete?.invoke(initial.id)
                    onDismiss()
                }
            },
        )
    }
}
