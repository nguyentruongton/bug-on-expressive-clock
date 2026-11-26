package app.expressiveclock.alarm

import app.expressiveclock.core.Alarm
import java.io.File
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SystemdAlarmSchedulerTest {

    @Test
    fun buildScheduleCommandIncludesCollectFlagAndExactSecondOnCalendar() {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val now = ZonedDateTime.of(2026, 11, 24, 10, 6, 0, 0, zone)
        val snoozeAt = ZonedDateTime.of(2026, 11, 24, 10, 16, 0, 0, zone)
        val alarm = Alarm(
            id = 2L,
            time = LocalTime.of(10, 6),
            label = "",
            enabled = true,
            snoozedUntilMillis = snoozeAt.toInstant().toEpochMilli(),
        )

        val cmd = SystemdAlarmScheduler.buildScheduleCommand(
            alarm = alarm,
            now = now,
            scriptPath = "/usr/lib/expressive-clock/alarm-trigger.sh",
        )
        assertNotNull(cmd)
        assertTrue("--collect" in cmd, "Expected --collect in $cmd")
        assertTrue("--unit=expressive-clock-alarm-2" in cmd, "Expected unit flag in $cmd")
        assertTrue("--on-calendar=2026-11-24 10:16:00" in cmd, "Expected exact second OnCalendar in $cmd")
        assertEquals(
            listOf("/usr/lib/expressive-clock/alarm-trigger.sh", "2", "alarm", "Alarm", ""),
            cmd.takeLast(5),
        )
    }

    @Test
    fun normalizeDevClasspathMapsStagedRunJarBackToBuildLibs() {
        val raw = listOf(
            "/repo/desktop/build/tmp/runClasspath/179548-12345/expressive-clock-desktop-1.0.0.jar",
            "/home/user/.gradle/caches/dep.jar",
        ).joinToString(":")

        val normalized = SystemdAlarmScheduler.normalizeDevClasspath(raw, pathSep = ":")
        assertEquals(
            "/repo/desktop/build/libs/expressive-clock-desktop-1.0.0.jar:/home/user/.gradle/caches/dep.jar",
            normalized,
        )
    }

    @Test
    fun buildDevLauncherScriptQuotesPathsWithSpacesSafely() {
        val javaBin = "/opt/jdk 21/bin/java"
        val rawCp = "/home/user/My Repo/desktop/build/tmp/runClasspath/99-42/expressive-clock-desktop-1.0.0.jar:/cache/a.jar"
        val triggerScript = "/home/user/My Repo/desktop/scripts/alarm-trigger.sh"
        val script = SystemdAlarmScheduler.buildDevLauncherScript(javaBin, rawCp, triggerScript)

        assertTrue(script.startsWith("#!/usr/bin/env bash\n"))
        assertTrue(
            "export EXPRESSIVE_CLOCK_TRIGGER_SCRIPT='/home/user/My Repo/desktop/scripts/alarm-trigger.sh'" in script,
            "Launcher must export resolved trigger script path for CWD=\$HOME: $script",
        )
        assertTrue("'/opt/jdk 21/bin/java'" in script, "Java binary should be single-quoted: $script")
        assertTrue(
            "-Xms32m -Xmx160m -XX:+UseSerialGC" in script,
            "Launcher should include lightweight JVM memory flags: $script",
        )
        assertTrue(
            "'/home/user/My Repo/desktop/build/libs/expressive-clock-desktop-1.0.0.jar:/cache/a.jar'" in script,
            "Classpath should rewrite staged jar and be single-quoted: $script",
        )
        assertTrue(script.trimEnd().endsWith("app.expressiveclock.DesktopMainKt \"\$@\""))
    }

    @Test
    fun resolveTriggerScriptPathPrefersEnvAndDerivesFromClasspathWhenNotInRepoCwd() {
        val tempRepo = java.nio.file.Files.createTempDirectory("ec-trigger-resolve").toFile()
        try {
            val scriptFile = File(tempRepo, "desktop/scripts/alarm-trigger.sh").apply {
                parentFile.mkdirs()
                writeText("#!/usr/bin/env bash\n")
            }
            val envResolved = SystemdAlarmScheduler.resolveTriggerScriptPath(
                env = mapOf("EXPRESSIVE_CLOCK_TRIGGER_SCRIPT" to scriptFile.absolutePath),
                rawClasspath = "",
                installedScript = File(tempRepo, "nonexistent-installed.sh"),
                cwd = File(tempRepo, "unrelated-home"),
            )
            assertEquals(scriptFile.absolutePath, envResolved)

            val cpJar = File(tempRepo, "desktop/build/libs/expressive-clock-desktop-1.0.0.jar")
            val cpResolved = SystemdAlarmScheduler.resolveTriggerScriptPath(
                env = emptyMap(),
                rawClasspath = "${cpJar.absolutePath}:/cache/other.jar",
                installedScript = File(tempRepo, "nonexistent-installed.sh"),
                cwd = File(tempRepo, "unrelated-home"),
            )
            assertEquals(scriptFile.absolutePath, cpResolved)
            assertEquals("expressive-clock-notify-alarm-4", SystemdAlarmScheduler.notifyUnitName(4L))
        } finally {
            tempRepo.deleteRecursively()
        }
    }

    @Test
    fun alarmTriggerScriptUsesNativeNotificationWithoutRingWindow() {
        val script = listOf(File("scripts/alarm-trigger.sh"), File("desktop/scripts/alarm-trigger.sh"))
            .firstOrNull { it.exists() }
        assertNotNull(script, "alarm-trigger.sh must exist")
        val content = script.readText()
        assertTrue("--ring" !in content, "alarm-trigger.sh must not launch --ring window")
        assertTrue("EXPRESSIVE_CLOCK_IN_RING_SESSION=1" in content, "alarm-trigger.sh must mark ring session env")
        assertTrue("--category=alarm" in content, "alarm-trigger.sh must tag notifications with --category=alarm")
        assertTrue("pkill -TERM -P" in content, "stop_audio must terminate child pw-play/aplay processes immediately")
        assertTrue("-- \"Expressive Clock\" \"\$LABEL\"" in content, "notify-send must use -- before summary/body")
        val confirmSection = content.substringAfter("Snoozed until")
        assertTrue(
            "--category=alarm" !in confirmSection.substringBefore("else"),
            "Low-urgency snooze confirmation toast must not carry --category=alarm",
        )

        val proc = ProcessBuilder("bash", "-n", script.absolutePath)
            .redirectErrorStream(true)
            .start()
        val output = proc.inputStream.bufferedReader().readText()
        assertEquals(0, proc.waitFor(), "bash -n failed: $output")
    }
}
