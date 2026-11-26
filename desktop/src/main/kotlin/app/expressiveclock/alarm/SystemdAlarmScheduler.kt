package app.expressiveclock.alarm

import app.expressiveclock.core.Alarm
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

/**
 * Daemonless alarm scheduling backed by `systemd-run --user` transient timers.
 * Eliminates `daemon-reload` stalls and leaves 0 MB RAM footprint when the app is closed.
 */
object SystemdAlarmScheduler {
    private val log = Logger.getLogger("SystemdAlarmScheduler")
    private val calendarFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val stagedJarRegex = Regex("""/build/tmp/runClasspath/[^/]+/([^/]+\.jar)$""")

    private fun unitName(id: Long): String = "expressive-clock-alarm-$id"

    private fun ringUnitName(id: Long): String = "expressive-clock-ring-alarm-$id"

    internal fun notifyUnitName(id: Long): String = "expressive-clock-notify-alarm-$id"

    internal fun resolveTriggerScriptPath(
        env: Map<String, String> = System.getenv(),
        rawClasspath: String = System.getProperty("java.class.path").orEmpty(),
        installedScript: File = File("/usr/lib/expressive-clock/alarm-trigger.sh"),
        cwd: File = File("."),
    ): String {
        val fromEnv = env["EXPRESSIVE_CLOCK_TRIGGER_SCRIPT"]?.takeIf { it.isNotBlank() }?.let(::File)
        if (fromEnv != null && fromEnv.exists()) return fromEnv.absolutePath
        if (installedScript.canExecute()) return installedScript.absolutePath
        val local = File(cwd, "scripts/alarm-trigger.sh")
        if (local.exists()) return local.absolutePath
        val fromRoot = File(cwd, "desktop/scripts/alarm-trigger.sh")
        if (fromRoot.exists()) return fromRoot.absolutePath
        val normalizedCp = normalizeDevClasspath(rawClasspath)
        for (entry in normalizedCp.split(File.pathSeparator)) {
            val idx = entry.indexOf("/build/libs/expressive-clock-desktop")
            if (idx > 0) {
                val candidate = File(entry.substring(0, idx), "scripts/alarm-trigger.sh")
                if (candidate.exists()) return candidate.absolutePath
            }
        }
        return installedScript.absolutePath
    }

    private fun triggerScriptPath(): String = resolveTriggerScriptPath()

    internal fun buildScheduleCommand(
        alarm: Alarm,
        now: ZonedDateTime,
        scriptPath: String,
    ): List<String>? {
        if (!alarm.enabled) return null
        val next = alarm.nextFire(now) ?: return null
        val onCalendar = next.format(calendarFormat)
        val label = alarm.label.ifBlank { "Alarm" }
        val sound = alarm.soundUri ?: ""

        return listOf(
            "systemd-run",
            "--user",
            "--collect",
            "--unit=${unitName(alarm.id)}",
            "--on-calendar=$onCalendar",
            "--timer-property=AccuracySec=1s",
            "--timer-property=WakeSystem=false",
            "--description=Expressive Clock Alarm #${alarm.id} ($label)",
            scriptPath,
            alarm.id.toString(),
            "alarm",
            label,
            sound,
        )
    }

    internal fun normalizeDevClasspath(
        rawClasspath: String,
        pathSep: String = File.pathSeparator,
    ): String {
        if (rawClasspath.isBlank()) return rawClasspath
        return rawClasspath.split(pathSep).joinToString(pathSep) { entry ->
            stagedJarRegex.replace(entry) { match ->
                "/build/libs/${match.groupValues[1]}"
            }
        }
    }

    private fun shellSingleQuote(value: String): String =
        "'${value.replace("'", "'\"'\"'")}'"

    internal fun buildDevLauncherScript(
        javaBin: String,
        rawClasspath: String,
        triggerScriptPath: String = "",
    ): String {
        val normalizedCp = normalizeDevClasspath(rawClasspath)
        return buildString {
            appendLine("#!/usr/bin/env bash")
            appendLine("set -euo pipefail")
            if (triggerScriptPath.isNotBlank()) {
                append("export EXPRESSIVE_CLOCK_TRIGGER_SCRIPT=")
                appendLine(shellSingleQuote(triggerScriptPath))
            }
            append("exec ")
            append(shellSingleQuote(javaBin))
            append(" -Xms32m -Xmx160m -XX:+UseSerialGC -cp ")
            append(shellSingleQuote(normalizedCp))
            appendLine(" app.expressiveclock.DesktopMainKt \"\$@\"")
        }
    }

    private fun ensureDevLauncherScript(resolvedTriggerScript: String) {
        if (File("/usr/bin/expressive-clock").canExecute()) return
        runCatching {
            val runtimeDir = System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() } ?: "/tmp"
            val launcher = Path.of(runtimeDir, "expressive-clock-dev-launch.sh")
            val javaBin = ProcessHandle.current().info().command().orElse("java")
            val rawCp = System.getProperty("java.class.path").orEmpty()
            if (rawCp.isBlank()) return
            Files.createDirectories(launcher.parent)
            Files.writeString(launcher, buildDevLauncherScript(javaBin, rawCp, resolvedTriggerScript))
            runCatching {
                Files.setPosixFilePermissions(launcher, PosixFilePermissions.fromString("rwx------"))
            }
        }
    }

    fun schedule(alarm: Alarm) {
        cancel(alarm.id)
        if (!alarm.enabled) return
        val script = triggerScriptPath()
        ensureDevLauncherScript(script)
        val cmd = buildScheduleCommand(alarm, ZonedDateTime.now(), script) ?: return
        runCommand(cmd)
    }

    /**
     * Cancels only the scheduled timer unit (`expressive-clock-alarm-<id>.timer`), never the
     * detached ring session (`expressive-clock-ring-alarm-<id>.service`), so `--ring` can safely
     * reschedule itself on Snooze without terminating mid-flight.
     */
    fun cancel(id: Long) {
        val unit = "${unitName(id)}.timer"
        runCommand(listOf("systemctl", "--user", "stop", unit), quiet = true)
        runCommand(listOf("systemctl", "--user", "reset-failed", "${unitName(id)}.service"), quiet = true)
    }

    fun stopRingSession(id: Long) {
        runCommand(listOf("systemctl", "--user", "stop", "${ringUnitName(id)}.service"), quiet = true)
    }

    fun stopNotifySession(id: Long) {
        runCommand(listOf("systemctl", "--user", "stop", "${notifyUnitName(id)}.service"), quiet = true)
    }

    fun scheduleAll(alarms: List<Alarm>) {
        alarms.forEach { alarm ->
            if (alarm.enabled) schedule(alarm) else cancel(alarm.id)
        }
    }

    private fun runCommand(cmd: List<String>, quiet: Boolean = false) {
        runCatching {
            val proc = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
            val exit = proc.waitFor()
            if (exit != 0 && !quiet) {
                val out = proc.inputStream.bufferedReader().readText().trim()
                log.warning("Command failed ($exit): ${cmd.joinToString(" ")} -> $out")
            }
        }.onFailure { err ->
            if (!quiet) log.warning("Failed to execute ${cmd.firstOrNull()}: ${err.message}")
        }
    }
}
