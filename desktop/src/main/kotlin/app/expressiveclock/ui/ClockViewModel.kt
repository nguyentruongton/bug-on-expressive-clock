package app.expressiveclock.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.expressiveclock.alarm.DesktopAlarmCoordinator
import app.expressiveclock.alarm.DesktopAudioPlayer
import app.expressiveclock.alarm.SystemdAlarmScheduler
import app.expressiveclock.alarm.SystemdTimerScheduler
import app.expressiveclock.core.Alarm
import app.expressiveclock.core.ClockTimer
import app.expressiveclock.core.Lap
import app.expressiveclock.core.Stopwatch
import app.expressiveclock.core.TimerState
import app.expressiveclock.core.WorldCity
import app.expressiveclock.core.canEnable
import app.expressiveclock.core.dateIssue
import app.expressiveclock.core.normalizedSchedule
import app.expressiveclock.core.preservingDismissal
import app.expressiveclock.core.withAddedSeconds
import app.expressiveclock.data.ClockSettings
import app.expressiveclock.data.ClockStore
import app.expressiveclock.data.DesktopStateExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@Composable
fun rememberElapsedTicker(active: Boolean = true): State<Long> =
    produceState(initialValue = SystemClock.elapsedRealtime(), active) {
        while (active) {
            withFrameMillis { }
            value = SystemClock.elapsedRealtime()
        }
    }

@Composable
fun rememberWallTicker(periodMillis: Long = 1000L): State<Long> =
    produceState(initialValue = System.currentTimeMillis(), periodMillis) {
        while (true) {
            val now = System.currentTimeMillis()
            value = now
            delay(periodMillis - (now % periodMillis))
        }
    }

class ClockViewModel(
    private val store: ClockStore = ClockStore(),
) : ViewModel() {

    private val initialSettings: ClockSettings = runBlocking { store.settingsNow() }

    val settings: StateFlow<ClockSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, initialSettings)
    val uiScale: StateFlow<Float> = store.uiScale
    val alarms: StateFlow<List<Alarm>> =
        store.alarms.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val cities: StateFlow<List<WorldCity>> =
        store.cities.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val timer: StateFlow<ClockTimer?> =
        store.timer.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _stopwatch = MutableStateFlow(Stopwatch())
    val stopwatch: StateFlow<Stopwatch> = _stopwatch.asStateFlow()

    private val _isSettingsLoaded = MutableStateFlow(true)
    val isSettingsLoaded: StateFlow<Boolean> = _isSettingsLoaded.asStateFlow()

    /** Holds the ID of an alarm currently ringing in-process (when signaled via single-instance socket). */
    private val _ringingAlarmId = MutableStateFlow<Long?>(null)
    val ringingAlarmId: StateFlow<Long?> = _ringingAlarmId.asStateFlow()

    @Volatile private var ringGeneration: Long = 0L
    @Volatile private var activeFireJob: Job? = null

    val homeZone: ZoneId = ZoneId.systemDefault()

    init {
        // Warm-load coordinator classes eagerly so alarm actions never fail on lazy load.
        DesktopAlarmCoordinator.hashCode()
        viewModelScope.launch {
            store.stopwatch.collect { sw -> _stopwatch.value = sw }
        }
        viewModelScope.launch(Dispatchers.IO) {
            // Re-sync alarms with systemd user timers
            val currentAlarms = store.alarmsNow()
            SystemdAlarmScheduler.scheduleAll(currentAlarms)

            // Detect timers that expired while the machine was suspended or app was closed
            val currentTimer = store.timerNow()
            var expiredId: Long? = null
            if (currentTimer != null && currentTimer.state == TimerState.RUNNING &&
                currentTimer.deadlineElapsedMillis <= System.currentTimeMillis()
            ) {
                val finished = currentTimer.copy(
                    state = TimerState.FINISHED,
                    pausedRemaining = Duration.ZERO,
                )
                store.putTimer(finished)
                expiredId = finished.id
            }
            exportStateNow(expiredTimerId = expiredId)
        }
    }

    private fun exportStateNow(expiredTimerId: Long? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            DesktopStateExporter.export(
                alarms = store.alarmsNow(),
                timer = store.timerNow(),
                stopwatch = store.stopwatchNow(),
                expiredTimerId = expiredTimerId,
            )
        }
    }

    /* ── Settings ───────────────────────────────────────────────────────────────────────────── */

    fun updateSettings(block: (ClockSettings) -> ClockSettings) {
        viewModelScope.launch { store.update(block) }
    }

    fun setUiScale(scale: Float) {
        viewModelScope.launch { store.updateUiScale(scale) }
    }

    fun stepUiScale(delta: Float) {
        viewModelScope.launch { store.stepUiScale(delta) }
    }

    /* ── Alarms ─────────────────────────────────────────────────────────────────────────────── */

    fun triggerRingInApp(alarmId: Long) {
        val gen = ++ringGeneration
        _ringingAlarmId.value = alarmId
        activeFireJob = viewModelScope.launch(Dispatchers.IO) {
            val alarm = DesktopAlarmCoordinator.onAlarmFired(store, alarmId)
                ?: store.alarmsNow().firstOrNull { it.id == alarmId }
            if (ringGeneration == gen && _ringingAlarmId.value == alarmId) {
                DesktopAudioPlayer.play(alarm?.soundUri, looping = true)
            }
        }
    }

    fun dismissRingingAlarm(alarmId: Long) {
        val isCurrentRing = _ringingAlarmId.value == alarmId
        if (isCurrentRing || _ringingAlarmId.value == null) {
            ++ringGeneration
            DesktopAudioPlayer.stop()
        }
        if (isCurrentRing) _ringingAlarmId.value = null
        val pendingFire = activeFireJob
        viewModelScope.launch(Dispatchers.IO) {
            pendingFire?.join()
            DesktopAlarmCoordinator.dismiss(store, alarmId)
            SystemdAlarmScheduler.stopNotifySession(alarmId)
        }
    }

    fun snoozeRingingAlarm(alarmId: Long) {
        val isCurrentRing = _ringingAlarmId.value == alarmId
        if (isCurrentRing || _ringingAlarmId.value == null) {
            ++ringGeneration
            DesktopAudioPlayer.stop()
        }
        if (isCurrentRing) _ringingAlarmId.value = null
        val pendingFire = activeFireJob
        viewModelScope.launch(Dispatchers.IO) {
            pendingFire?.join()
            DesktopAlarmCoordinator.snooze(store, alarmId)
            SystemdAlarmScheduler.stopNotifySession(alarmId)
        }
    }

    fun silenceRingingAlarm(alarmId: Long) {
        if (_ringingAlarmId.value == alarmId) {
            ++ringGeneration
            DesktopAudioPlayer.stop()
            _ringingAlarmId.value = null
        }
        viewModelScope.launch(Dispatchers.IO) {
            SystemdAlarmScheduler.stopNotifySession(alarmId)
        }
    }

    fun toggleAlarm(id: Long, onEnabled: (Alarm) -> Unit = {}, onInvalid: (Alarm) -> Unit = {}) = viewModelScope.launch {
        var invalid: Alarm? = null
        val next = store.updateAlarms { list ->
            list.map {
                if (it.id != id) it else {
                    val now = java.time.ZonedDateTime.now()
                    val enabling = !it.enabled || (it.hasSpecificDate && it.nextFire(now) == null)
                    if (enabling && !it.canEnable(now)) {
                        invalid = it
                        it
                    } else {
                        it.copy(
                            enabled = enabling,
                            snoozedUntilMillis = null,
                            skippedOccurrenceMillis = null,
                            skippedOccurrenceDate = null,
                        ).normalizedSchedule()
                    }
                }
            }
        }
        if (invalid != null) {
            onInvalid(invalid!!)
            return@launch
        }
        next.firstOrNull { it.id == id }?.let { a ->
            if (a.enabled) {
                launch(Dispatchers.IO) { SystemdAlarmScheduler.schedule(a) }
                onEnabled(a)
            } else {
                launch(Dispatchers.IO) {
                    SystemdAlarmScheduler.cancel(id)
                    SystemdAlarmScheduler.stopRingSession(id)
                    SystemdAlarmScheduler.stopNotifySession(id)
                }
            }
        }
        exportStateNow()
    }

    fun saveAlarm(draft: Alarm, onSaved: (Alarm) -> Unit = {}, onInvalid: (Alarm) -> Unit = {}) = viewModelScope.launch {
        val id = if (draft.id == 0L) store.nextId() else draft.id
        var alarm = draft.copy(id = id)
        var invalid = false
        store.updateAlarms { current ->
            val proposed = draft.copy(id = id).preservingDismissal(current.firstOrNull { it.id == id })
            if (draft.enabled && proposed.dateIssue(java.time.ZonedDateTime.now()) != null) {
                invalid = true
                current
            } else {
                alarm = proposed.normalizedSchedule()
                (if (current.any { it.id == id }) current.map { if (it.id == id) alarm else it }
                else current + alarm).sortedWith(compareBy({ it.time.hour }, { it.time.minute }))
            }
        }
        if (invalid) {
            onInvalid(draft)
            return@launch
        }
        launch(Dispatchers.IO) { SystemdAlarmScheduler.schedule(alarm) }
        exportStateNow()
        onSaved(alarm)
    }

    fun deleteAlarm(id: Long) = viewModelScope.launch {
        launch(Dispatchers.IO) {
            SystemdAlarmScheduler.cancel(id)
            SystemdAlarmScheduler.stopRingSession(id)
            SystemdAlarmScheduler.stopNotifySession(id)
        }
        store.updateAlarms { list -> list.filterNot { it.id == id } }
        exportStateNow()
    }

    fun deleteAlarms(ids: Set<Long>) = viewModelScope.launch {
        launch(Dispatchers.IO) {
            ids.forEach {
                SystemdAlarmScheduler.cancel(it)
                SystemdAlarmScheduler.stopRingSession(it)
                SystemdAlarmScheduler.stopNotifySession(it)
            }
        }
        store.updateAlarms { list -> list.filterNot { it.id in ids } }
        exportStateNow()
    }

    /* ── World clock ────────────────────────────────────────────────────────────────────────── */

    var newlyAddedCity by mutableStateOf<String?>(null)
        private set

    fun finishCityEntrance() {
        newlyAddedCity = null
    }

    fun addCity(city: WorldCity) = viewModelScope.launch {
        try {
            store.updateCities { current ->
                if (current.none { it.zone == city.zone }) {
                    newlyAddedCity = city.zone.id
                    current + city
                } else current
            }
        } catch (error: Exception) {
            newlyAddedCity = null
            throw error
        }
    }

    fun removeCity(zone: ZoneId) = removeCities(setOf(zone))

    fun removeCities(zones: Set<ZoneId>) = viewModelScope.launch {
        store.updateCities { current -> current.filterNot { it.zone in zones } }
    }

    /* ── Timer ──────────────────────────────────────────────────────────────────────────────── */

    var timerDigits: String by mutableStateOf("")
        private set

    val draftDuration: Duration
        get() {
            val d = timerDigits.padStart(6, '0')
            return Duration.ofHours(d.substring(0, 2).toLong())
                .plusMinutes(d.substring(2, 4).toLong())
                .plusSeconds(d.substring(4, 6).toLong())
        }

    fun pressDigit(c: Char) {
        if (timerDigits.length >= 6) return
        if (timerDigits.isEmpty() && c == '0') return
        timerDigits += c
    }

    fun backspace() {
        timerDigits = timerDigits.dropLast(1)
    }

    fun resetTimerDraft() {
        timerDigits = ""
    }

    fun setTimerPreset(totalSeconds: Int, label: String = "Timer") {
        if (totalSeconds <= 0) {
            timerDigits = ""
            return
        }
        val total = totalSeconds.coerceAtMost(99 * 3600 + 59 * 60 + 59)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        timerDigits = "%02d%02d%02d".format(hours, minutes, seconds).trimStart('0')
        startTimer(label)
    }

    fun windToMinutes(minutes: Int) {
        timerDigits = if (minutes <= 0) "" else "%d%02d00".format(minutes / 60, minutes % 60).trimStart('0')
    }

    fun startTimer(label: String = "Timer") = viewModelScope.launch {
        val total = draftDuration
        if (total.isZero) return@launch
        val t = ClockTimer(
            id = store.nextId(),
            label = label,
            total = total,
            state = TimerState.RUNNING,
            deadlineElapsedMillis = SystemClock.elapsedRealtime() + total.toMillis(),
        )
        timerDigits = ""
        store.putTimer(t)
        val sound = store.settingsNow().timers.soundUri
        launch(Dispatchers.IO) { SystemdTimerScheduler.sync(t, sound) }
        exportStateNow()
    }

    fun pauseOrResumeTimer() = viewModelScope.launch {
        val t = store.timerNow() ?: return@launch
        val now = SystemClock.elapsedRealtime()
        val next = when (t.state) {
            TimerState.RUNNING -> t.copy(state = TimerState.PAUSED, pausedRemaining = t.remaining(now))
            else -> t.copy(state = TimerState.RUNNING, deadlineElapsedMillis = now + t.pausedRemaining.toMillis())
        }
        store.putTimer(next)
        val sound = store.settingsNow().timers.soundUri
        launch(Dispatchers.IO) { SystemdTimerScheduler.sync(next, sound) }
        exportStateNow()
    }

    fun addTenSeconds() = viewModelScope.launch {
        val t = store.timerNow() ?: return@launch
        val next = t.withAddedSeconds(10)
        store.putTimer(next)
        val sound = store.settingsNow().timers.soundUri
        launch(Dispatchers.IO) { SystemdTimerScheduler.sync(next, sound) }
        exportStateNow()
    }

    fun cancelTimer() = viewModelScope.launch {
        DesktopAudioPlayer.stop()
        store.putTimer(null)
        launch(Dispatchers.IO) { SystemdTimerScheduler.cancel() }
        exportStateNow()
    }

    /* ── Stopwatch ──────────────────────────────────────────────────────────────────────────── */

    fun toggleStopwatch() {
        val sw = _stopwatch.value
        val now = SystemClock.elapsedRealtime()
        val next = if (sw.running) {
            sw.copy(running = false, accumulated = sw.elapsed(now))
        } else {
            sw.copy(running = true, startedAtElapsed = now)
        }
        _stopwatch.value = next
        viewModelScope.launch {
            store.putStopwatch(next)
            exportStateNow()
        }
    }

    fun lap() {
        val sw = _stopwatch.value
        if (!sw.running) return
        val total = sw.elapsed(SystemClock.elapsedRealtime())
        val previous = sw.laps.firstOrNull()?.total ?: Duration.ZERO
        val next = sw.copy(laps = listOf(Lap(sw.laps.size + 1, total.minus(previous), total)) + sw.laps)
        _stopwatch.value = next
        viewModelScope.launch {
            store.putStopwatch(next)
            exportStateNow()
        }
    }

    fun resetStopwatch() {
        val next = Stopwatch()
        _stopwatch.value = next
        viewModelScope.launch {
            store.putStopwatch(next)
            exportStateNow()
        }
    }

    fun blankAlarm(): Alarm = Alarm(
        id = 0L,
        time = LocalTime.of(7, 0),
        label = "",
        days = emptySet<DayOfWeek>(),
    )
}
