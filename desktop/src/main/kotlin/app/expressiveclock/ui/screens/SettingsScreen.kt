package app.expressiveclock.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.expressiveclock.data.ClockSettings
import app.expressiveclock.data.DarkMode
import app.expressiveclock.data.DefaultStartupPage
import app.expressiveclock.data.HourFormat
import app.expressiveclock.data.SILENT
import app.expressiveclock.data.WeekStart
import app.expressiveclock.data.WorldClockStyle
import app.expressiveclock.ui.DesktopDisplayScale
import app.expressiveclock.ui.LocalDesktopUiScale
import app.expressiveclock.ui.sheets.AboutRows
import app.expressiveclock.ui.sheets.NavigateRow
import app.expressiveclock.ui.sheets.SectionLabel
import app.expressiveclock.ui.sheets.rememberAlarmSoundSelector
import app.expressiveclock.ui.theme.Palette
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Linux Desktop Settings screen replacing the Android SettingsScreen.
 * Removes Google Play In-App Updates, Dynamic Island calibration, and Android RingtoneManager.
 */
@Composable
fun SettingsScreen(
    settings: ClockSettings,
    onChange: ((ClockSettings) -> ClockSettings) -> Unit,
    onBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onReopenOnboarding: () -> Unit = {},
) {
    val openTimerSoundPicker = rememberAlarmSoundSelector(settings.timers.soundUri) { uri ->
        onChange { it.copy(timers = it.timers.copy(soundUri = uri)) }
    }

    val uiScaleState = LocalDesktopUiScale.current

    var showPaletteDialog by remember { mutableStateOf(false) }
    var showUiScaleDialog by remember { mutableStateOf(false) }
    var showStartupPageDialog by remember { mutableStateOf(false) }
    var showWeekStartDialog by remember { mutableStateOf(false) }
    var showHourFormatDialog by remember { mutableStateOf(false) }
    var showSnoozeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionLabel("Appearance")

                SwitchRow(
                    title = "Dynamic Colour (System / matugen)",
                    subtitle = "Follow caelestia/scheme.json or matugen/colors.json with live reload",
                    checked = settings.theme.dynamicColor,
                    onCheckedChange = { enabled ->
                        onChange { it.copy(theme = it.theme.copy(dynamicColor = enabled)) }
                    },
                )

                NavigateRow(
                    title = "Fallback Colour Palette",
                    subtitle = settings.theme.palette.displayName,
                    onClick = { showPaletteDialog = true },
                )

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("Theme Mode", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DarkMode.entries.forEach { mode ->
                            DesktopFilterChip(
                                selected = settings.theme.darkMode == mode,
                                label = mode.label,
                                onClick = {
                                    onChange { it.copy(theme = it.theme.copy(darkMode = mode)) }
                                },
                            )
                        }
                    }
                }

                SwitchRow(
                    title = "AMOLED Black",
                    subtitle = "Pure black surfaces in dark mode",
                    checked = settings.theme.amoledBlack,
                    onCheckedChange = { enabled ->
                        onChange { it.copy(theme = it.theme.copy(amoledBlack = enabled)) }
                    },
                )

                val effectiveScaleText = "%.2f".format(Locale.US, uiScaleState.effectiveScale)
                    .trimEnd('0')
                    .trimEnd('.')
                NavigateRow(
                    title = "Interface Scale",
                    subtitle = "${(uiScaleState.uiScale * 100).roundToInt()}% (${effectiveScaleText}× display density · Ctrl +/-/0)",
                    onClick = { showUiScaleDialog = true },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionLabel("Alarms")

                NavigateRow(
                    title = "Snooze Duration",
                    subtitle = "${settings.alarms.snoozeMinutes} minutes",
                    onClick = { showSnoozeDialog = true },
                )

                SwitchRow(
                    title = "24-hour Alarm Cards",
                    subtitle = "Always display alarm times in 24-hour format",
                    checked = settings.alarms.cards24Hour,
                    onCheckedChange = { enabled ->
                        onChange { it.copy(alarms = it.alarms.copy(cards24Hour = enabled)) }
                    },
                )

                NavigateRow(
                    title = "Start Week On",
                    subtitle = settings.alarms.weekStart.label,
                    onClick = { showWeekStartDialog = true },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionLabel("World Clock")

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("Default Clock Style", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WorldClockStyle.entries.forEach { style ->
                            DesktopFilterChip(
                                selected = settings.world.defaultStyle == style,
                                label = style.label,
                                onClick = {
                                    onChange { it.copy(world = it.world.copy(defaultStyle = style)) }
                                },
                            )
                        }
                    }
                }

                SwitchRow(
                    title = "Show Seconds",
                    subtitle = "Display seconds on the world clock",
                    checked = settings.world.showSeconds,
                    onCheckedChange = { enabled ->
                        onChange { it.copy(world = it.world.copy(showSeconds = enabled)) }
                    },
                )

                NavigateRow(
                    title = "Time Format",
                    subtitle = settings.world.hourFormat.label,
                    onClick = { showHourFormatDialog = true },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionLabel("Timers & General")

                val timerSoundSubtitle = when (val uri = settings.timers.soundUri) {
                    null -> "Default (Bundled Chime)"
                    SILENT -> "Silent"
                    else -> File(uri.removePrefix("file://")).name
                }
                NavigateRow(
                    title = "Timer Sound",
                    subtitle = timerSoundSubtitle,
                    onClick = openTimerSoundPicker,
                )

                NavigateRow(
                    title = "Default Startup Page",
                    subtitle = settings.defaultStartupPage.label,
                    onClick = { showStartupPageDialog = true },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                AboutRows()
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showPaletteDialog) {
        OptionPickerDialog(
            title = "Fallback Colour Palette",
            options = Palette.entries,
            selected = settings.theme.palette,
            labelOf = { it.displayName },
            onSelect = { palette ->
                onChange { it.copy(theme = it.theme.copy(palette = palette)) }
                showPaletteDialog = false
            },
            onDismiss = { showPaletteDialog = false },
        )
    }

    if (showUiScaleDialog) {
        val currentOption = DesktopDisplayScale.UI_SCALE_OPTIONS
            .minByOrNull { abs(it - uiScaleState.uiScale) } ?: 1.0f
        OptionPickerDialog(
            title = "Interface Scale",
            options = DesktopDisplayScale.UI_SCALE_OPTIONS,
            selected = currentOption,
            labelOf = { option ->
                val pct = (option * 100).roundToInt()
                if (abs(option - 1.0f) < 0.01f) "$pct% (Default)" else "$pct%"
            },
            onSelect = { scale ->
                uiScaleState.onUiScaleChange(scale)
                showUiScaleDialog = false
            },
            onDismiss = { showUiScaleDialog = false },
        )
    }

    if (showStartupPageDialog) {
        OptionPickerDialog(
            title = "Default Startup Page",
            options = DefaultStartupPage.entries,
            selected = settings.defaultStartupPage,
            labelOf = { it.label },
            onSelect = { page ->
                onChange { it.copy(defaultStartupPage = page) }
                showStartupPageDialog = false
            },
            onDismiss = { showStartupPageDialog = false },
        )
    }

    if (showWeekStartDialog) {
        OptionPickerDialog(
            title = "Start Week On",
            options = WeekStart.entries,
            selected = settings.alarms.weekStart,
            labelOf = { it.label },
            onSelect = { weekStart ->
                onChange { it.copy(alarms = it.alarms.copy(weekStart = weekStart)) }
                showWeekStartDialog = false
            },
            onDismiss = { showWeekStartDialog = false },
        )
    }

    if (showHourFormatDialog) {
        OptionPickerDialog(
            title = "Time Format",
            options = HourFormat.entries,
            selected = settings.world.hourFormat,
            labelOf = { it.label },
            onSelect = { format ->
                onChange { it.copy(world = it.world.copy(hourFormat = format)) }
                showHourFormatDialog = false
            },
            onDismiss = { showHourFormatDialog = false },
        )
    }

    if (showSnoozeDialog) {
        val options = listOf(5, 10, 15, 20, 30)
        OptionPickerDialog(
            title = "Snooze Duration",
            options = options,
            selected = settings.alarms.snoozeMinutes,
            labelOf = { "$it minutes" },
            onSelect = { mins ->
                onChange { it.copy(alarms = it.alarms.copy(snoozeMinutes = mins)) }
                showSnoozeDialog = false
            },
            onDismiss = { showSnoozeDialog = false },
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DesktopFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(8.dp)

    val baseContainer = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    val overlayColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val stateAlpha = when {
        pressed -> 0.12f
        hovered -> 0.08f
        else -> 0f
    }
    val targetColor = if (stateAlpha > 0f) {
        overlayColor.copy(alpha = stateAlpha).compositeOver(baseContainer)
    } else {
        baseContainer
    }
    val containerColor by animateColorAsState(targetColor)
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .clip(shape)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = 32.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun <T> OptionPickerDialog(
    title: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                options.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(item) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = item == selected, onClick = { onSelect(item) })
                        Text(labelOf(item), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
internal fun SinglePresetEditDialog(
    title: String,
    initialSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var minText by remember { mutableStateOf((initialSeconds / 60).toString()) }
    var secText by remember { mutableStateOf((initialSeconds % 60).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = minText,
                        onValueChange = { minText = it.filter(Char::isDigit).take(3) },
                        label = { Text("Min") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = secText,
                        onValueChange = { secText = it.filter(Char::isDigit).take(2) },
                        label = { Text("Sec") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val m = minText.toIntOrNull() ?: 0
                    val s = secText.toIntOrNull() ?: 0
                    val total = (m * 60 + s).coerceIn(1, 99 * 3600)
                    onConfirm(total)
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
