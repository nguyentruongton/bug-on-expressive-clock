package app.expressiveclock.data

import app.expressiveclock.core.Alarm
import app.expressiveclock.core.ClockTimer
import app.expressiveclock.core.Stopwatch
import app.expressiveclock.core.TimerState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.ZonedDateTime

@Serializable
data class ExportedTimer(
    val id: Long,
    val label: String,
    val state: String,
    @SerialName("total_ms") val totalMs: Long,
    @SerialName("deadline_epoch_ms") val deadlineEpochMs: Long? = null,
    @SerialName("paused_remaining_ms") val pausedRemainingMs: Long? = null,
    val expired: Boolean = false,
)

@Serializable
data class ExportedStopwatch(
    val running: Boolean,
    @SerialName("started_epoch_ms") val startedEpochMs: Long? = null,
    @SerialName("accumulated_ms") val accumulatedMs: Long,
    @SerialName("lap_count") val lapCount: Int,
)

@Serializable
data class ExportedNextAlarm(
    val id: Long,
    val label: String,
    @SerialName("fire_epoch_ms") val fireEpochMs: Long,
)

@Serializable
data class DesktopIpcState(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("timestamp_ms") val timestampMs: Long,
    val timers: List<ExportedTimer>,
    val stopwatch: ExportedStopwatch,
    @SerialName("next_alarm") val nextAlarm: ExportedNextAlarm? = null,
)

/**
 * Event-driven IPC state exporter for Waybar / Eww / external Linux status bars.
 * Writes atomically to `${XDG_RUNTIME_DIR:-/run/user/$UID}/expressive-clock.json` only on state transitions.
 */
object DesktopStateExporter {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    val ipcPath: Path
        get() {
            val runtimeDir = System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() }
                ?: "/tmp"
            return Path.of(runtimeDir, "expressive-clock.json")
        }

    fun export(
        alarms: List<Alarm>,
        timer: ClockTimer?,
        stopwatch: Stopwatch,
        expiredTimerId: Long? = null,
    ) {
        val nowMs = System.currentTimeMillis()
        val nowZdt = ZonedDateTime.now()

        val nextAlarm = alarms
            .mapNotNull { alarm -> alarm.nextFire(nowZdt)?.let { alarm to it } }
            .minByOrNull { it.second }
            ?.let { (alarm, fireAt) ->
                ExportedNextAlarm(
                    id = alarm.id,
                    label = alarm.label.ifBlank { "Alarm" },
                    fireEpochMs = fireAt.toInstant().toEpochMilli(),
                )
            }

        val exportedTimers = if (timer != null) {
            val isExpired = expiredTimerId == timer.id ||
                (timer.state == TimerState.RUNNING && timer.deadlineElapsedMillis <= nowMs) ||
                timer.state == TimerState.FINISHED
            listOf(
                ExportedTimer(
                    id = timer.id,
                    label = timer.label,
                    state = if (isExpired) "EXPIRED" else timer.state.name,
                    totalMs = timer.total.toMillis(),
                    deadlineEpochMs = if (timer.state == TimerState.RUNNING) timer.deadlineElapsedMillis else null,
                    pausedRemainingMs = if (timer.state == TimerState.PAUSED) timer.pausedRemaining.toMillis() else null,
                    expired = isExpired,
                ),
            )
        } else {
            emptyList()
        }

        val exportedStopwatch = ExportedStopwatch(
            running = stopwatch.running,
            startedEpochMs = if (stopwatch.running) stopwatch.startedAtElapsed else null,
            accumulatedMs = stopwatch.accumulated.toMillis(),
            lapCount = stopwatch.laps.size,
        )

        val payload = DesktopIpcState(
            schemaVersion = 1,
            timestampMs = nowMs,
            timers = exportedTimers,
            stopwatch = exportedStopwatch,
            nextAlarm = nextAlarm,
        )

        runCatching {
            val target = ipcPath
            Files.createDirectories(target.parent)
            val tmp = target.resolveSibling("${target.fileName}.tmp")
            Files.writeString(tmp, json.encodeToString(payload))
            runCatching {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }.onFailure {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
