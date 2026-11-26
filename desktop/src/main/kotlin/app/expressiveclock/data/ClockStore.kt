package app.expressiveclock.data

import app.expressiveclock.core.Alarm
import app.expressiveclock.core.ClockTimer
import app.expressiveclock.core.Lap
import app.expressiveclock.core.Stopwatch
import app.expressiveclock.core.TimerState
import app.expressiveclock.core.WorldCity
import app.expressiveclock.ui.DesktopDisplayScale
import app.expressiveclock.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong

/* ── Serializable DTOs (Zero-Touch Upstream: upstream models have no @Serializable) ── */

@Serializable
private data class ClockSettingsDto(
    val alarms: AlarmSettingsDto = AlarmSettingsDto(),
    val world: WorldClockSettingsDto = WorldClockSettingsDto(),
    val timers: TimerSettingsDto = TimerSettingsDto(),
    val theme: ThemeSettingsDto = ThemeSettingsDto(),
    val defaultStartupPage: String = DefaultStartupPage.ALARMS.name,
    val hasCompletedInitialSetup: Boolean = true,
    val uiScale: Float = 1.0f,
)

@Serializable
private data class AlarmSettingsDto(
    val silenceAfterMinutes: Int = 10,
    val snoozeMinutes: Int = 10,
    val cards24Hour: Boolean = false,
    val weekStart: String = WeekStart.SYSTEM.name,
)

@Serializable
private data class WorldClockSettingsDto(
    val defaultStyle: String = WorldClockStyle.ANALOGUE.name,
    val deleteTutorialShown: Boolean = false,
    val showSeconds: Boolean = false,
    val hourFormat: String = HourFormat.SYSTEM.name,
)

@Serializable
private data class TimerSettingsDto(
    val soundUri: String? = null,
    val vibrate: Boolean = true,
    val presets: List<Int> = DEFAULT_TIMER_PRESETS,
)

@Serializable
private data class ThemeSettingsDto(
    val dynamicColor: Boolean = true,
    val palette: String = Palette.CONCEPT.name,
    val darkMode: String = DarkMode.SYSTEM.name,
    val amoledBlack: Boolean = false,
)

@Serializable
private data class AlarmDto(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val days: List<String> = emptyList(),
    val enabled: Boolean = true,
    val vibrate: Boolean = true,
    val soundUri: String? = null,
    val snoozedUntilMillis: Long? = null,
    val skippedOccurrenceMillis: Long? = null,
    val skippedOccurrenceDate: String? = null,
)

@Serializable
private data class WorldCityDto(
    val zoneId: String,
    val city: String,
    val region: String,
)

@Serializable
private data class ClockTimerDto(
    val id: Long,
    val label: String,
    val totalMillis: Long,
    val state: String = TimerState.IDLE.name,
    val deadlineEpochMs: Long = 0L,
    val pausedRemainingMs: Long = totalMillis,
)

@Serializable
private data class LapDto(
    val index: Int,
    val splitMillis: Long,
    val totalMillis: Long,
)

@Serializable
private data class StopwatchDto(
    val running: Boolean = false,
    val startedAtEpochMs: Long = 0L,
    val accumulatedMillis: Long = 0L,
    val laps: List<LapDto> = emptyList(),
)

@Serializable
private data class ClockStateDto(
    val nextId: Long = 1L,
    val alarms: List<AlarmDto> = emptyList(),
    val cities: List<WorldCityDto> = emptyList(),
    val timer: ClockTimerDto? = null,
    val stopwatch: StopwatchDto = StopwatchDto(),
)

/* ── Domain <-> DTO Mappers ── */

private fun ClockSettingsDto.toDomain(): ClockSettings = ClockSettings(
    alarms = AlarmSettings(
        silenceAfterMinutes = alarms.silenceAfterMinutes,
        snoozeMinutes = alarms.snoozeMinutes,
        cards24Hour = alarms.cards24Hour,
        weekStart = runCatching { WeekStart.valueOf(alarms.weekStart) }.getOrDefault(WeekStart.SYSTEM),
    ),
    world = WorldClockSettings(
        defaultStyle = runCatching { WorldClockStyle.valueOf(world.defaultStyle) }.getOrDefault(WorldClockStyle.ANALOGUE),
        deleteTutorialShown = world.deleteTutorialShown,
        showSeconds = world.showSeconds,
        hourFormat = runCatching { HourFormat.valueOf(world.hourFormat) }.getOrDefault(HourFormat.SYSTEM),
    ),
    timers = TimerSettings(
        soundUri = timers.soundUri,
        vibrate = timers.vibrate,
        presets = timers.presets.ifEmpty { DEFAULT_TIMER_PRESETS },
    ),
    theme = ThemeSettings(
        dynamicColor = theme.dynamicColor,
        palette = runCatching { Palette.valueOf(theme.palette) }.getOrDefault(Palette.CONCEPT),
        darkMode = runCatching { DarkMode.valueOf(theme.darkMode) }.getOrDefault(DarkMode.SYSTEM),
        amoledBlack = theme.amoledBlack,
    ),
    defaultStartupPage = DefaultStartupPage.fromName(defaultStartupPage),
    hasCompletedInitialSetup = hasCompletedInitialSetup,
)

private fun ClockSettings.toDto(uiScale: Float = 1.0f): ClockSettingsDto = ClockSettingsDto(
    alarms = AlarmSettingsDto(
        silenceAfterMinutes = alarms.silenceAfterMinutes,
        snoozeMinutes = alarms.snoozeMinutes,
        cards24Hour = alarms.cards24Hour,
        weekStart = alarms.weekStart.name,
    ),
    world = WorldClockSettingsDto(
        defaultStyle = world.defaultStyle.name,
        deleteTutorialShown = world.deleteTutorialShown,
        showSeconds = world.showSeconds,
        hourFormat = world.hourFormat.name,
    ),
    timers = TimerSettingsDto(
        soundUri = timers.soundUri,
        vibrate = timers.vibrate,
        presets = timers.presets,
    ),
    theme = ThemeSettingsDto(
        dynamicColor = theme.dynamicColor,
        palette = theme.palette.name,
        darkMode = theme.darkMode.name,
        amoledBlack = theme.amoledBlack,
    ),
    defaultStartupPage = defaultStartupPage.name,
    hasCompletedInitialSetup = hasCompletedInitialSetup,
    uiScale = DesktopDisplayScale.normalizeUiScale(uiScale),
)

private fun AlarmDto.toDomain(): Alarm = Alarm(
    id = id,
    time = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)),
    label = label,
    days = days.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }.toSet(),
    enabled = enabled,
    vibrate = vibrate,
    soundUri = soundUri,
    snoozedUntilMillis = snoozedUntilMillis,
    skippedOccurrenceMillis = skippedOccurrenceMillis,
    skippedOccurrenceDate = skippedOccurrenceDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
)

private fun Alarm.toDto(): AlarmDto = AlarmDto(
    id = id,
    hour = time.hour,
    minute = time.minute,
    label = label,
    days = days.map { it.name },
    enabled = enabled,
    vibrate = vibrate,
    soundUri = soundUri,
    snoozedUntilMillis = snoozedUntilMillis,
    skippedOccurrenceMillis = skippedOccurrenceMillis,
    skippedOccurrenceDate = skippedOccurrenceDate?.toString(),
)

private fun WorldCityDto.toDomain(): WorldCity? = runCatching {
    WorldCity(zone = ZoneId.of(zoneId), city = city, region = region)
}.getOrNull()

private fun WorldCity.toDto(): WorldCityDto = WorldCityDto(
    zoneId = zone.id,
    city = city,
    region = region,
)

private fun ClockTimerDto.toDomain(): ClockTimer = ClockTimer(
    id = id,
    label = label,
    total = Duration.ofMillis(totalMillis.coerceAtLeast(0L)),
    state = runCatching { TimerState.valueOf(state) }.getOrDefault(TimerState.IDLE),
    deadlineElapsedMillis = deadlineEpochMs,
    pausedRemaining = Duration.ofMillis(pausedRemainingMs.coerceAtLeast(0L)),
)

private fun ClockTimer.toDto(): ClockTimerDto = ClockTimerDto(
    id = id,
    label = label,
    totalMillis = total.toMillis(),
    state = state.name,
    deadlineEpochMs = deadlineElapsedMillis,
    pausedRemainingMs = pausedRemaining.toMillis(),
)

private fun StopwatchDto.toDomain(): Stopwatch = Stopwatch(
    running = running,
    startedAtElapsed = startedAtEpochMs,
    accumulated = Duration.ofMillis(accumulatedMillis.coerceAtLeast(0L)),
    laps = laps.map {
        Lap(
            index = it.index,
            split = Duration.ofMillis(it.splitMillis.coerceAtLeast(0L)),
            total = Duration.ofMillis(it.totalMillis.coerceAtLeast(0L)),
        )
    },
)

private fun Stopwatch.toDto(): StopwatchDto = StopwatchDto(
    running = running,
    startedAtEpochMs = startedAtElapsed,
    accumulatedMillis = accumulated.toMillis(),
    laps = laps.map {
        LapDto(index = it.index, splitMillis = it.split.toMillis(), totalMillis = it.total.toMillis())
    },
)

/**
 * Desktop XDG-compliant persistence store replacing Android Preferences DataStore.
 *
 * - Settings: `${XDG_CONFIG_HOME:-~/.config}/expressive-clock/settings.json`
 * - State:    `${XDG_DATA_HOME:-~/.local/share}/expressive-clock/state.json`
 *
 * All writes are atomic (`.tmp` + rename) to prevent corruption on power loss or concurrent reads.
 */
class ClockStore(
    @Suppress("UNUSED_PARAMETER") context: Any? = null,
    configDirOverride: Path? = null,
    dataDirOverride: Path? = null,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val configDir: Path = configDirOverride ?: run {
        val xdg = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
            ?: "${System.getProperty("user.home")}/.config"
        Path.of(xdg, "expressive-clock")
    }

    private val dataDir: Path = dataDirOverride ?: run {
        val xdg = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
            ?: "${System.getProperty("user.home")}/.local/share"
        Path.of(xdg, "expressive-clock")
    }

    val settingsPath: Path = configDir.resolve("settings.json")
    val statePath: Path = dataDir.resolve("state.json")

    private val mutex = Mutex()
    private val idCounter: AtomicLong

    private val _settings: MutableStateFlow<ClockSettings>
    private val _uiScale: MutableStateFlow<Float>
    private val _alarms: MutableStateFlow<List<Alarm>>
    private val _cities: MutableStateFlow<List<WorldCity>>
    private val _timer: MutableStateFlow<ClockTimer?>
    private val _stopwatch: MutableStateFlow<Stopwatch>

    val settings: Flow<ClockSettings>
    val uiScale: StateFlow<Float>
    val alarms: Flow<List<Alarm>>
    val cities: Flow<List<WorldCity>>
    val timer: Flow<ClockTimer?>
    val stopwatch: Flow<Stopwatch>

    init {
        val (initialSettings, initialUiScale) = readSettingsAndScaleSync()
        val initialState = readStateSync()

        idCounter = AtomicLong(initialState.nextId.coerceAtLeast(1L))
        _settings = MutableStateFlow(initialSettings)
        _uiScale = MutableStateFlow(initialUiScale)
        _alarms = MutableStateFlow(initialState.alarms.map { it.toDomain() })
        _cities = MutableStateFlow(initialState.cities.mapNotNull { it.toDomain() })
        _timer = MutableStateFlow(initialState.timer?.toDomain())
        _stopwatch = MutableStateFlow(initialState.stopwatch.toDomain())

        settings = _settings.asStateFlow()
        uiScale = _uiScale.asStateFlow()
        alarms = _alarms.asStateFlow()
        cities = _cities.asStateFlow()
        timer = _timer.asStateFlow()
        stopwatch = _stopwatch.asStateFlow()
    }

    private fun readSettingsAndScaleSync(): Pair<ClockSettings, Float> {
        if (!Files.exists(settingsPath)) {
            return ClockSettings(hasCompletedInitialSetup = true) to 1.0f
        }
        return runCatching {
            val dto = json.decodeFromString<ClockSettingsDto>(Files.readString(settingsPath))
            dto.toDomain() to DesktopDisplayScale.normalizeUiScale(dto.uiScale)
        }.getOrElse {
            ClockSettings(hasCompletedInitialSetup = true) to 1.0f
        }
    }

    private fun readStateSync(): ClockStateDto {
        if (!Files.exists(statePath)) return ClockStateDto()
        return runCatching {
            json.decodeFromString<ClockStateDto>(Files.readString(statePath))
        }.getOrElse {
            ClockStateDto()
        }
    }

    private fun writeAtomic(target: Path, content: String) {
        Files.createDirectories(target.parent)
        val tmp = target.resolveSibling("${target.fileName}.tmp")
        Files.writeString(tmp, content)
        runCatching {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.onFailure {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun persistSettingsSync(value: ClockSettings, uiScale: Float = _uiScale.value) {
        writeAtomic(settingsPath, json.encodeToString(value.toDto(uiScale)))
    }

    private fun persistStateSync() {
        val dto = ClockStateDto(
            nextId = idCounter.get(),
            alarms = _alarms.value.map { it.toDto() },
            cities = _cities.value.map { it.toDto() },
            timer = _timer.value?.toDto(),
            stopwatch = _stopwatch.value.toDto(),
        )
        writeAtomic(statePath, json.encodeToString(dto))
    }

    suspend fun settingsNow(): ClockSettings = _settings.value
    fun uiScaleNow(): Float = _uiScale.value
    suspend fun alarmsNow(): List<Alarm> = _alarms.value
    suspend fun citiesNow(): List<WorldCity> = _cities.value
    suspend fun timerNow(): ClockTimer? = _timer.value
    suspend fun stopwatchNow(): Stopwatch = _stopwatch.value

    suspend fun nextId(): Long = mutex.withLock {
        val id = idCounter.getAndIncrement()
        withContext(Dispatchers.IO) { persistStateSync() }
        id
    }

    suspend fun update(block: (ClockSettings) -> ClockSettings): ClockSettings = mutex.withLock {
        val next = block(_settings.value)
        _settings.value = next
        val scale = _uiScale.value
        withContext(Dispatchers.IO) { persistSettingsSync(next, scale) }
        next
    }

    suspend fun updateUiScale(scale: Float): Float = mutex.withLock {
        val normalized = DesktopDisplayScale.normalizeUiScale(scale)
        _uiScale.value = normalized
        val currentSettings = _settings.value
        withContext(Dispatchers.IO) { persistSettingsSync(currentSettings, normalized) }
        normalized
    }

    suspend fun stepUiScale(delta: Float): Float = mutex.withLock {
        val normalized = DesktopDisplayScale.normalizeUiScale(_uiScale.value + delta)
        _uiScale.value = normalized
        val currentSettings = _settings.value
        withContext(Dispatchers.IO) { persistSettingsSync(currentSettings, normalized) }
        normalized
    }

    suspend fun updateAlarms(block: (List<Alarm>) -> List<Alarm>): List<Alarm> = mutex.withLock {
        val next = block(_alarms.value)
        _alarms.value = next
        withContext(Dispatchers.IO) { persistStateSync() }
        next
    }

    suspend fun updateCities(block: (List<WorldCity>) -> List<WorldCity>): List<WorldCity> = mutex.withLock {
        val next = block(_cities.value)
        _cities.value = next
        withContext(Dispatchers.IO) { persistStateSync() }
        next
    }

    suspend fun putTimer(timer: ClockTimer?) = mutex.withLock {
        _timer.value = timer
        withContext(Dispatchers.IO) { persistStateSync() }
    }

    suspend fun putStopwatch(stopwatch: Stopwatch) = mutex.withLock {
        _stopwatch.value = stopwatch
        withContext(Dispatchers.IO) { persistStateSync() }
    }
}
