package app.expressiveclock.ui.sheets

import app.expressiveclock.ui.responsive.persistentScrollEdges
import app.expressiveclock.ui.screens.progressiveListEdgeMask
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.disabled
import app.expressiveclock.core.dateIssue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import app.expressiveclock.ui.elasticClickable
import app.expressiveclock.ui.glass
import app.expressiveclock.ui.theme.ClockFace
import app.expressiveclock.ui.theme.Numerals
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.DayOfWeek
import java.time.format.TextStyle as DayTextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs

/** Special sound URI value used when an alarm/timer is explicitly silent. */
internal const val SILENT = "silent"

/** Returns the locale/system-defined first day of the week. */
internal fun systemFirstDay(): DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

internal val AlarmSheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
private val AlarmCardShape = RoundedCornerShape(22.dp)
private val AlarmControlShape = RoundedCornerShape(18.dp)
private val TickerItemHeight = 72.dp
private val TickerViewportHeight = 184.dp
private val CompactTickerViewportHeight = 120.dp
private val TickerHighlightHeight = 58.dp
private val TickerFrameShape = RoundedCornerShape(26.dp)

/** Shared Material-card presentation used by both the create and edit alarm sheets. */
@Composable
fun AlarmPickerSheetContent(
    title: String,
    hour: Int,
    minute: Int,
    is24Hour: Boolean,
    pm: Boolean,
    editingHour: Boolean,
    label: String,
    days: Set<DayOfWeek>,
    weekOrder: List<DayOfWeek>,
    vibrate: Boolean,
    soundLabel: String,
    showDelete: Boolean,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onPmChange: (Boolean) -> Unit,
    onEditingHourChange: (Boolean) -> Unit,
    onLabelChange: (String) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onVibrateChange: (Boolean) -> Unit,
    onSoundClick: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modal: Boolean = false,
    specificDate: java.time.LocalDate? = null,
    invalidSpecificDate: Boolean = false,
    onSpecificDateChange: (java.time.LocalDate?) -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    // Choose once from the full screen, not the changing IME viewport. Shorter
    // phone wheels leave room for AM/PM and Name while the whole sheet glides.
    val tickerViewportHeight = if (configuration.smallestScreenWidthDp < 600 &&
        configuration.screenHeightDp < 900) CompactTickerViewportHeight else TickerViewportHeight
    val wallTime by app.expressiveclock.ui.rememberWallTicker()
    val dateDraft = app.expressiveclock.core.Alarm(0, java.time.LocalTime.of(
        if (is24Hour) hour else (hour % 12) + if (pm) 12 else 0, minute),
        specificDate = specificDate, invalidSpecificDate = if (invalidSpecificDate) "invalid" else null)
    val dateNow = java.time.Instant.ofEpochMilli(wallTime).atZone(java.time.ZoneId.systemDefault())
    val dateIssue = dateDraft.dateIssue(dateNow)
    // Keep the body height independent of the IME: the modal surface lifts as a
    // whole instead of remeasuring its header and scroll viewport during keyboard entry.
    val bottomInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val maxBodyHeight = (LocalConfiguration.current.screenHeightDp.dp * .82f - bottomInset - 32.dp)
        .coerceAtLeast(96.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AlarmSheetShape)
            .then(if (modal) Modifier.heightIn(max = maxBodyHeight) else Modifier.imePadding().navigationBarsPadding())
            .padding(horizontal = 22.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(4.dp))

        val compactHeader = LocalConfiguration.current.screenWidthDp < 360 || density.fontScale > 1.1f
        val heading: @Composable () -> Unit = {
            Text(title, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
        }
        if (compactHeader) heading()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SheetHeaderButton(
                text = "Cancel",
                primary = false,
                onClick = onCancel,
            )
            if (!compactHeader) Text(
                title,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
            SheetHeaderButton(
                text = "Save",
                primary = true,
                onClick = onSave,
                enabled = dateIssue == null,
                icon = Icons.Rounded.Check,
            )
        }

        val bodyScroll = rememberScrollState()
        CompositionLocalProvider(LocalBringIntoViewSpec provides AlarmNameBringIntoViewSpec) {
        Column(Modifier.weight(1f, fill = false)
            .persistentScrollEdges(bodyScroll),
            horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TickerFrameShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = .055f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .075f), TickerFrameShape)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlarmWheel(
                    modifier = Modifier.weight(1f),
                    viewportHeight = tickerViewportHeight,
                    values = if (is24Hour) (0..23).toList() else (1..12).toList(),
                    selected = if (is24Hour) hour else hour12(hour),
                    format = { "%02d".format(it) },
                    onSelected = {
                        if (is24Hour) onHourChange(it) else {
                            val next = if (pm) {
                                if (it == 12) 12 else it + 12
                            } else {
                                if (it == 12) 0 else it
                            }
                            onHourChange(next)
                        }
                        onEditingHourChange(true)
                    },
                )
                AlarmWheel(
                    modifier = Modifier.weight(1f),
                    viewportHeight = tickerViewportHeight,
                    values = (0..59).toList(),
                    selected = minute,
                    format = { "%02d".format(it) },
                    onSelected = {
                        onMinuteChange(it)
                        onEditingHourChange(false)
                    },
                )
            }
        }

        if (!is24Hour) {
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                MeridiemTextButton("AM", selected = !pm, onClick = { onPmChange(false); onEditingHourChange(true) })
                Spacer(Modifier.width(8.dp))
                MeridiemTextButton("PM", selected = pm, onClick = { onPmChange(true); onEditingHourChange(true) })
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            weekOrder.forEach { day ->
                DayChip(
                    label = day.getDisplayName(DayTextStyle.NARROW, locale),
                    selected = day in days && specificDate == null && !invalidSpecificDate,
                    modifier = Modifier.weight(1f),
                    onClick = { onToggleDay(day) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        AlarmDateControls(dateDraft, dateNow, dateIssue, onSpecificDateChange)

        AlarmNameCard(label = label, onLabelChange = onLabelChange, clearDefault = title == "New Alarm")

        Spacer(Modifier.height(8.dp))

        AlarmOptionsCard(
            soundLabel = soundLabel,
            vibrate = vibrate,
            onSoundClick = onSoundClick,
            onVibrateChange = onVibrateChange,
        )


        if (showDelete) {
            Spacer(Modifier.height(8.dp))
            DeleteAlarmCard(onDelete = onDelete)
        }

        Spacer(Modifier.height(2.dp))
        }
        }
    }
}

/** Glide the unchanged sheet toward the IME destination, stopping below the status bar. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun Modifier.alarmEditorImeLift(): Modifier {
    val density = LocalDensity.current
    val screenHeight = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val ime = WindowInsets.imeAnimationTarget
    val navigationBars = WindowInsets.navigationBars
    val statusBars = WindowInsets.statusBars
    var sheetHeight by remember { mutableIntStateOf(0) }
    val keyboardLift = (ime.getBottom(density) - navigationBars.getBottom(density)).coerceAtLeast(0)
    val availableLift = (screenHeight - sheetHeight - statusBars.getTop(density)).coerceAtLeast(0f)
    // Animate one destination rather than snapping to inset updates or restarting
    // independent animations for the sheet's children.
    val lift by animateFloatAsState(
        targetValue = minOf(keyboardLift.toFloat(), availableLift),
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "AlarmSheetKeyboardLift",
    )
    return onSizeChanged { sheetHeight = it.height }
        .graphicsLayer {
            translationY = -lift
        }
}

/** The entire modal surface handles the IME; focus must not independently scroll its body. */
private object AlarmNameBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

@Composable
private fun AlarmWheel(
    modifier: Modifier,
    viewportHeight: Dp,
    values: List<Int>,
    selected: Int,
    format: (Int) -> String,
    onSelected: (Int) -> Unit,
) {
    if (values.isEmpty()) return

    /*
     * Present the wheel as a very large repeating list rather than a finite 0..59 / 1..12 list.
     * This gives the picker the expected "infinite" feel in both directions. A minute wheel can
     * therefore go from 05 -> 59 with one short upward swipe instead of forcing the user to travel
     * all the way to the end of the list.
     *
     * We intentionally keep a generous number of cycles on either side of the starting point so
     * normal flings never reach an edge. Items are lightweight and LazyColumn only composes what is
     * visible, so this does not materially increase composition work.
     */
    val cycleCount = 1001
    val middleCycle = cycleCount / 2
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
    val initialVirtualIndex = middleCycle * values.size + selectedIndex

    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialVirtualIndex)
    val fling = rememberSnapFlingBehavior(state)
    val view = LocalView.current
    val isInitial = remember { mutableStateOf(true) }

    LaunchedEffect(state, values) {
        snapshotFlow {
            val viewportCenter =
                (state.layoutInfo.viewportStartOffset + state.layoutInfo.viewportEndOffset) / 2
            state.layoutInfo.visibleItemsInfo
                .minByOrNull { item ->
                    abs((item.offset + item.size / 2) - viewportCenter)
                }
                ?.index
        }
            .distinctUntilChanged()
            .collect { centeredIndex ->
                centeredIndex ?: return@collect
                val valueIndex = centeredIndex.floorMod(values.size)
                values[valueIndex].let(onSelected)
                if (isInitial.value) {
                    isInitial.value = false
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    } else {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                }
            }
    }

    /*
     * If the selected value changes externally (for example when AM/PM is changed), gently move
     * the current wheel to the nearest occurrence of that value rather than jumping hundreds of
     * rows. During an active fling the wheel itself is the source of truth, so we leave it alone.
     */
    LaunchedEffect(selected) {
        if (state.isScrollInProgress) return@LaunchedEffect

        val centeredIndex = state.layoutInfo.visibleItemsInfo
            .minByOrNull { item ->
                val viewportCenter =
                    (state.layoutInfo.viewportStartOffset + state.layoutInfo.viewportEndOffset) / 2
                abs((item.offset + item.size / 2) - viewportCenter)
            }
            ?.index
            ?: return@LaunchedEffect

        val targetIndex = values.indexOf(selected)
        if (targetIndex < 0) return@LaunchedEffect

        val currentRemainder = centeredIndex.floorMod(values.size)
        var delta = targetIndex - currentRemainder
        val size = values.size
        if (delta > size / 2) delta -= size
        if (delta < -size / 2) delta += size

        if (delta != 0) {
            state.animateScrollToItem(centeredIndex + delta)
        }
    }

    Box(
        modifier = modifier.height(viewportHeight),
        contentAlignment = Alignment.Center,
    ) {
        // Stationary selection window. The wheel itself moves underneath this frame.
        Box(
            modifier = Modifier
                .fillMaxWidth(.82f)
                .height(TickerHighlightHeight)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .92f))
                .align(Alignment.Center),
        )

        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(
                vertical = (viewportHeight - TickerItemHeight) / 2,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxSize().progressiveListEdgeMask(1f, 1f),
        ) {
            items(
                count = values.size * cycleCount,
                key = { it },
            ) { virtualIndex ->
                val value = values[virtualIndex.floorMod(values.size)]
                val centered = virtualIndex == state.firstVisibleItemIndex

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TickerItemHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Numerals(
                        text = format(value),
                        capHeight = 48.dp,
                        color = if (centered) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .52f)
                        },
                        width = ClockFace.CONDENSED,
                        weight = ClockFace.WEIGHT_ON,
                        tabular = true,
                        tracking = ClockFace.CONDENSED_TRACKING,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }

        // Fade the list into the sheet background at the top and bottom while leaving the fixed
        // selection frame untouched.
        Box(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.surface,
                        1f to MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                    )
                ),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                        1f to MaterialTheme.colorScheme.surface,
                    )
                ),
        )
    }
}

private fun Int.floorMod(mod: Int): Int = ((this % mod) + mod) % mod

private fun hour12(hour: Int): Int = (hour % 12).let { if (it == 0) 12 else it }

@Composable
private fun DayChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shapeRadius by animateDpAsState(
        targetValue = if (selected) 13.dp else 20.dp,
        animationSpec = spring(dampingRatio = .78f, stiffness = 520f),
        label = "dayChipRadius",
    )
    val fill by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(180),
        label = "dayChipFill",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(160),
        label = "dayChipText",
    )

    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(shapeRadius))
            .background(fill)
            .elasticClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = textColor,
        )
    }
}

@Composable
private fun MeridiemTextButton(text: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow
            ),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AlarmNameCard(label: String, onLabelChange: (String) -> Unit, clearDefault: Boolean) {
    var defaultUntouched by remember { mutableStateOf(clearDefault && label == "Alarm") }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AlarmCardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .25f), AlarmCardShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Label,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Name",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.foundation.text.BasicTextField(
                    value = label,
                    onValueChange = { defaultUntouched = false; onLabelChange(it) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused && defaultUntouched) {
                                defaultUntouched = false
                                onLabelChange("")
                            }
                        },
                )
            }
        }
    }
}

@Composable
private fun AlarmOptionsCard(
    soundLabel: String,
    vibrate: Boolean,
    onSoundClick: () -> Unit,
    onVibrateChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AlarmCardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .25f), AlarmCardShape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .elasticClickable(onClick = onSoundClick)
                .padding(start = 16.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OptionIconBubble(Icons.Outlined.MusicNote)
            Spacer(Modifier.width(12.dp))
            Text("Sound", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(8.dp))
            Text(
                soundLabel,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .30f),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OptionIconBubble(Icons.Outlined.Vibration)
            Spacer(Modifier.width(12.dp))
            Text("Vibration", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = vibrate,
                onCheckedChange = onVibrateChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            )
        }
    }
}

@Composable
private fun OptionIconBubble(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DeleteAlarmCard(onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(AlarmCardShape)
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = .70f))
            .elasticClickable(onClick = onDelete)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Delete, contentDescription = "Delete alarm", tint = MaterialTheme.colorScheme.onErrorContainer)
        Spacer(Modifier.width(12.dp))
        Text("Delete Alarm", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
internal fun SheetHeaderButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .height(46.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (primary) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = .70f)
            )
            .then(if (enabled) Modifier.elasticClickable(onClick = onClick) else Modifier
                .semantics { disabled() }.alpha(0.38f))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
