package app.expressiveclock.ui

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopDisplayScaleTest {

    @Test
    fun hyprlandForceZeroScalingCompensatesTruncatedAwtDensity() {
        val monitorsJson = """
            [
              {"name": "HDMI-A-1", "scale": 1.0, "focused": false},
              {"name": "eDP-1", "scale": 1.6, "focused": true}
            ]
        """.trimIndent()

        val baseScale = DesktopDisplayScale.resolveBaseScale(
            awtDensity = 1.0f,
            env = mapOf("HYPRLAND_INSTANCE_SIGNATURE" to "abc"),
            hyprMonitorsJsonProvider = { monitorsJson },
            xrdbOutputProvider = { null },
        )

        assertEquals(1.6f, baseScale, 0.001f)
        assertEquals(1.6f, DesktopDisplayScale.compensationFactor(1.0f, baseScale), 0.001f)
        assertEquals(1.6f, DesktopDisplayScale.effectiveDensity(1.0f, baseScale, 1.0f), 0.001f)
        assertEquals(1.92f, DesktopDisplayScale.effectiveDensity(1.0f, baseScale, 1.2f), 0.001f)
    }

    @Test
    fun doesNotDoubleScaleWhenAwtAlreadyReportsHiDpi() {
        val baseScale = DesktopDisplayScale.resolveBaseScale(
            awtDensity = 1.6f,
            env = mapOf("HYPRLAND_INSTANCE_SIGNATURE" to "abc", "GDK_SCALE" to "2"),
            hyprMonitorsJsonProvider = { """[{"name":"eDP-1","scale":1.6,"focused":true}]""" },
            xrdbOutputProvider = { "Xft.dpi:\t192" },
        )

        assertEquals(1.6f, baseScale, 0.001f)
        assertEquals(1.0f, DesktopDisplayScale.compensationFactor(1.6f, baseScale), 0.001f)
        assertEquals(1.6f, DesktopDisplayScale.effectiveDensity(1.6f, baseScale, 1.0f), 0.001f)
    }

    @Test
    fun explicitEnvOverrideWinsOverAwtAndHyprctl() {
        val baseScale = DesktopDisplayScale.resolveBaseScale(
            awtDensity = 1.6f,
            env = mapOf("EXPRESSIVE_CLOCK_SCALE" to "2.0"),
            hyprMonitorsJsonProvider = { """[{"name":"eDP-1","scale":1.6,"focused":true}]""" },
            xrdbOutputProvider = { null },
        )

        assertEquals(2.0f, baseScale, 0.001f)
        assertEquals(2.0f, DesktopDisplayScale.effectiveDensity(1.6f, baseScale, 1.0f), 0.001f)
    }

    @Test
    fun malformedHyprctlFallsBackToGdkScaleThenXrdbThenOne() {
        val fromGdk = DesktopDisplayScale.resolveBaseScale(
            awtDensity = 1.0f,
            env = mapOf("GDK_SCALE" to "1.5"),
            hyprMonitorsJsonProvider = { "{bad json" },
            xrdbOutputProvider = { "Xft.dpi:\t192" },
        )
        assertEquals(1.5f, fromGdk, 0.001f)

        val fromXrdb = DesktopDisplayScale.resolveBaseScale(
            awtDensity = 1.0f,
            env = emptyMap(),
            hyprMonitorsJsonProvider = { "{bad json" },
            xrdbOutputProvider = { "Xft.dpi:\t144\n" },
        )
        assertEquals(1.5f, fromXrdb, 0.001f)

        val fallback = DesktopDisplayScale.resolveBaseScale(
            awtDensity = 1.0f,
            env = emptyMap(),
            hyprMonitorsJsonProvider = { null },
            xrdbOutputProvider = { null },
        )
        assertEquals(1.0f, fallback, 0.001f)
    }

    @Test
    fun gdkOrQtScaleOfOneDoesNotShadowHyprctlScale() {
        val baseScale = DesktopDisplayScale.resolveBaseScale(
            awtDensity = 1.0f,
            env = mapOf("GDK_SCALE" to "1", "QT_SCALE_FACTOR" to "1"),
            hyprMonitorsJsonProvider = { """[{"name":"eDP-1","scale":1.6,"focused":true}]""" },
            xrdbOutputProvider = { null },
        )
        assertEquals(1.6f, baseScale, 0.001f)
    }

    @Test
    fun parseHyprlandScaleSkipsMalformedMonitorEntries() {
        val mixedJson = """
            [
              "unexpected-string",
              {"name": "bad-null", "scale": null, "focused": true},
              {"name": "bad-obj", "scale": {"x": 1}, "focused": false},
              {"name": "eDP-1", "scale": 1.6, "focused": true}
            ]
        """.trimIndent()
        assertEquals(1.6f, DesktopDisplayScale.parseHyprlandScale(mixedJson))
    }

    @Test
    fun runCommandWithTimeoutDrainsLargeStdoutWithoutDeadlock() {
        val output = DesktopDisplayScale.runCommandWithTimeout(
            timeoutMs = 1500L,
            "sh",
            "-c",
            "printf '%0.s#padding-line-for-pipe-buffer-drain-test\\n' \$(seq 1 3500); echo 'Xft.dpi: 144'",
        )
        assertTrue(output != null && output.length > 100_000, "Expected >100 KB drained stdout, got ${output?.length}")
        assertEquals(1.5f, DesktopDisplayScale.parseXrdbScale(output))
    }

    @Test
    fun normalizeUiScaleClampsAndEliminatesFloatDrift() {
        assertEquals(1.0f, DesktopDisplayScale.normalizeUiScale(Float.NaN))
        assertEquals(1.0f, DesktopDisplayScale.normalizeUiScale(Float.POSITIVE_INFINITY))
        assertEquals(0.8f, DesktopDisplayScale.normalizeUiScale(0.3f))
        assertEquals(1.5f, DesktopDisplayScale.normalizeUiScale(3.0f))
        val drifted = 1.0f + 0.1f + 0.1f
        assertTrue(abs(DesktopDisplayScale.normalizeUiScale(drifted) - 1.2f) < 0.0001f)
    }
}
