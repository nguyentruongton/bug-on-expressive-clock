package app.expressiveclock.alarm

import app.expressiveclock.core.ClockTimer
import app.expressiveclock.core.TimerState
import java.io.File
import java.util.logging.Logger

/**
 * Schedules countdown timers via `systemd-run --user --on-active=<sec>s` transient timers.
 */
object SystemdTimerScheduler {
    private val log = Logger.getLogger("SystemdTimerScheduler")
    private const val UNIT_NAME = "expressive-clock-timer-active"

    private fun triggerScriptPath(): String {
        val installed = File("/usr/lib/expressive-clock/alarm-trigger.sh")
        if (installed.canExecute()) return installed.absolutePath
        val local = File("scripts/alarm-trigger.sh")
        if (local.exists()) return local.absolutePath
        val fromRoot = File("desktop/scripts/alarm-trigger.sh")
        if (fromRoot.exists()) return fromRoot.absolutePath
        return "/usr/lib/expressive-clock/alarm-trigger.sh"
    }

    fun sync(timer: ClockTimer?, soundUri: String? = null) {
        cancel()
        if (timer == null || timer.state != TimerState.RUNNING) return

        val remainingMs = (timer.deadlineElapsedMillis - System.currentTimeMillis()).coerceAtLeast(1000L)
        val remainingSec = (remainingMs + 999L) / 1000L
        val script = triggerScriptPath()
        val label = timer.label.ifBlank { "Timer" }

        val cmd = listOf(
            "systemd-run",
            "--user",
            "--unit=$UNIT_NAME",
            "--on-active=${remainingSec}s",
            "--timer-property=AccuracySec=1s",
            "--description=Expressive Clock Timer ($label)",
            script,
            timer.id.toString(),
            "timer",
            label,
            soundUri ?: "",
        )
        runCommand(cmd)
    }

    fun cancel() {
        runCommand(listOf("systemctl", "--user", "stop", "$UNIT_NAME.timer"), quiet = true)
        runCommand(listOf("systemctl", "--user", "reset-failed", "$UNIT_NAME.service"), quiet = true)
    }

    private fun runCommand(cmd: List<String>, quiet: Boolean = false) {
        runCatching {
            val proc = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
            val exit = proc.waitFor()
            if (exit != 0 && !quiet) {
                val out = proc.inputStream.bufferedReader().readText().trim()
                log.warning("Timer scheduler command failed ($exit): $out")
            }
        }.onFailure { err ->
            if (!quiet) log.warning("Failed to run systemd-run: ${err.message}")
        }
    }
}
