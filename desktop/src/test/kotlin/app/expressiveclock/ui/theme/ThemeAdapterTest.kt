package app.expressiveclock.ui.theme

import androidx.compose.ui.graphics.Color
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThemeAdapterTest {

    @Test
    fun parseCaelestiaSchemeExtractsCamelCaseColoursAndMode() {
        val json = """
            {
              "name": "dynamic",
              "mode": "light",
              "colours": {
                "primary": "745c00",
                "onPrimary": "fff8ee",
                "primaryContainer": "f7d155",
                "onPrimaryContainer": "5a4700",
                "surfaceContainerHigh": "fee6a6",
                "background": "fff8f0"
              }
            }
        """.trimIndent()

        assertFalse(parseCaelestiaMode(json)!!)
        val scheme = parseDynamicColorScheme(json, Palette.CONCEPT.light, dark = false)
        assertNotNull(scheme)
        assertEquals(Color(0xFF745C00), scheme.primary)
        assertEquals(Color(0xFFFFF8EE), scheme.onPrimary)
        assertEquals(Color(0xFFF7D155), scheme.primaryContainer)
        assertEquals(Color(0xFFFEE6A6), scheme.surfaceContainerHigh)
        assertEquals(Color(0xFFFFF8F0), scheme.background)
        // Missing roles preserve base fallback
        assertEquals(Palette.CONCEPT.light.error, scheme.error)
    }

    @Test
    fun parseCaelestiaModeHandlesDarkAndInvalidValues() {
        assertTrue(parseCaelestiaMode("""{"mode": "DARK"}""")!!)
        assertNull(parseCaelestiaMode("""{"mode": "auto"}"""))
        assertNull(parseCaelestiaMode("""not json"""))
    }

    @Test
    fun parseMatugenColorsJsonExtractsSnakeCaseDarkAndLight() {
        val json = """
            {
              "colors": {
                "primary": { "light": "#6750a4", "dark": "#d0bcff" },
                "on_primary": { "default": "#ffffff" },
                "surface_container_high": "#2b2930"
              }
            }
        """.trimIndent()

        val darkScheme = parseDynamicColorScheme(json, Palette.CONCEPT.dark, dark = true)
        assertNotNull(darkScheme)
        assertEquals(Color(0xFFD0BCFF), darkScheme.primary)
        assertEquals(Color(0xFFFFFFFF), darkScheme.onPrimary)
        assertEquals(Color(0xFF2B2930), darkScheme.surfaceContainerHigh)
    }

    @Test
    fun loadDynamicColorSchemePrefersCaelestiaThenMatugenThenNull() {
        val tmp = Files.createTempDirectory("theme-adapter-test")
        try {
            val caelestia = tmp.resolve("scheme.json")
            val matugen = tmp.resolve("colors.json")
            Files.writeString(matugen, """{"colors":{"primary":"#112233"}}""")

            val fromMatugen = loadDynamicColorScheme(Palette.CONCEPT.dark, true, caelestia, matugen)
            assertEquals(Color(0xFF112233), fromMatugen?.primary)

            Files.writeString(caelestia, """{"colours":{"primary":"445566"}}""")
            val fromCaelestia = loadDynamicColorScheme(Palette.CONCEPT.dark, true, caelestia, matugen)
            assertEquals(Color(0xFF445566), fromCaelestia?.primary)

            Files.delete(caelestia)
            Files.delete(matugen)
            assertNull(loadDynamicColorScheme(Palette.CONCEPT.dark, true, caelestia, matugen))
        } finally {
            tmp.toFile().deleteRecursively()
        }
    }

    @Test
    fun malformedJsonOrInvalidHexReturnsNullOrPreservesBase() {
        assertNull(parseDynamicColorScheme("{broken", Palette.CONCEPT.dark, dark = true))
        val scheme = parseDynamicColorScheme(
            """{"colours":{"primary":"zzzzzz","onPrimary":"123456"}}""",
            Palette.CONCEPT.dark,
            dark = true,
        )
        assertNotNull(scheme)
        assertEquals(Palette.CONCEPT.dark.primary, scheme.primary)
        assertEquals(Color(0xFF123456), scheme.onPrimary)
    }

    @Test
    fun caelestiaSchemeModeMismatchReturnsNullAndFallsBack() {
        val lightSchemeJson = """{"mode":"light","colours":{"primary":"745c00","background":"fff8f0"}}"""
        val darkSchemeJson = """{"mode":"dark","colours":{"primary":"f7d155","background":"19150b"}}"""

        assertNull(parseDynamicColorScheme(lightSchemeJson, Palette.CONCEPT.dark, dark = true))
        assertNull(parseDynamicColorScheme(darkSchemeJson, Palette.CONCEPT.light, dark = false))

        val tmp = Files.createTempDirectory("theme-mode-mismatch-test")
        try {
            val caelestia = tmp.resolve("scheme.json")
            val matugen = tmp.resolve("colors.json")
            Files.writeString(caelestia, lightSchemeJson)

            // Without matugen/colors.json, dark mode returns null so ClockTheme falls back to Palette.dark
            assertNull(loadDynamicColorScheme(Palette.CONCEPT.dark, dark = true, caelestia, matugen))

            // With matugen/colors.json present, dark mode falls through to matugen dark colors
            Files.writeString(matugen, """{"colors":{"primary":{"light":"#111111","dark":"#222222"}}}""")
            val darkFromMatugen = loadDynamicColorScheme(Palette.CONCEPT.dark, dark = true, caelestia, matugen)
            assertEquals(Color(0xFF222222), darkFromMatugen?.primary)
        } finally {
            tmp.toFile().deleteRecursively()
        }
    }
}
