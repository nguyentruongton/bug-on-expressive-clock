package app.expressiveclock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import app.expressiveclock.data.DarkMode
import app.expressiveclock.data.ThemeSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import kotlin.concurrent.thread

private val matugenJson = Json { ignoreUnknownKeys = true }

internal fun caelestiaSchemePath(): Path {
    val xdgState = System.getenv("XDG_STATE_HOME")?.takeIf { it.isNotBlank() }
        ?: "${System.getProperty("user.home")}/.local/state"
    return Path.of(xdgState, "caelestia", "scheme.json")
}

internal fun matugenColorsPath(): Path {
    val xdg = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
        ?: "${System.getProperty("user.home")}/.config"
    return Path.of(xdg, "matugen", "colors.json")
}

private fun parseHexColor(hex: String?): Color? {
    val clean = hex?.trim()?.removePrefix("#") ?: return null
    return runCatching {
        when (clean.length) {
            6 -> Color((0xFF000000L or clean.toLong(16)).toInt())
            8 -> Color(clean.toLong(16).toInt())
            else -> null
        }
    }.getOrNull()
}

private fun snakeToCamel(name: String): String =
    name.split('_').mapIndexed { index, part ->
        if (index == 0) part else part.replaceFirstChar { it.uppercaseChar() }
    }.joinToString("")

internal fun parseCaelestiaMode(rawJson: String): Boolean? = runCatching {
    val root = matugenJson.parseToJsonElement(rawJson).jsonObject
    val mode = (root["mode"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.lowercase()
    when (mode) {
        "dark" -> true
        "light" -> false
        else -> null
    }
}.getOrNull()

internal fun parseDynamicColorScheme(rawJson: String, base: ColorScheme, dark: Boolean): ColorScheme? = runCatching {
    val root = matugenJson.parseToJsonElement(rawJson).jsonObject
    val caelestiaColours = root["colours"]?.jsonObject
    if (caelestiaColours != null) {
        val schemeMode = parseCaelestiaMode(rawJson)
        if (schemeMode != null && schemeMode != dark) return@runCatching null
    }
    val colorsNode = caelestiaColours ?: root["colors"]?.jsonObject ?: root
    val modeKey = if (dark) "dark" else "light"

    fun role(snakeName: String): Color? {
        val entry = colorsNode[snakeName] ?: colorsNode[snakeToCamel(snakeName)] ?: return null
        val hex = when (entry) {
            is JsonObject -> {
                ((entry[modeKey] ?: entry["default"]) as? JsonPrimitive)?.takeIf { it.isString }?.content
            }
            is JsonPrimitive -> entry.takeIf { it.isString }?.content
            else -> null
        }
        return parseHexColor(hex)
    }

    base.copy(
        primary = role("primary") ?: base.primary,
        onPrimary = role("on_primary") ?: base.onPrimary,
        primaryContainer = role("primary_container") ?: base.primaryContainer,
        onPrimaryContainer = role("on_primary_container") ?: base.onPrimaryContainer,
        inversePrimary = role("inverse_primary") ?: base.inversePrimary,
        secondary = role("secondary") ?: base.secondary,
        onSecondary = role("on_secondary") ?: base.onSecondary,
        secondaryContainer = role("secondary_container") ?: base.secondaryContainer,
        onSecondaryContainer = role("on_secondary_container") ?: base.onSecondaryContainer,
        tertiary = role("tertiary") ?: base.tertiary,
        onTertiary = role("on_tertiary") ?: base.onTertiary,
        tertiaryContainer = role("tertiary_container") ?: base.tertiaryContainer,
        onTertiaryContainer = role("on_tertiary_container") ?: base.onTertiaryContainer,
        background = role("background") ?: base.background,
        onBackground = role("on_background") ?: base.onBackground,
        surface = role("surface") ?: base.surface,
        onSurface = role("on_surface") ?: base.onSurface,
        surfaceVariant = role("surface_variant") ?: base.surfaceVariant,
        onSurfaceVariant = role("on_surface_variant") ?: base.onSurfaceVariant,
        surfaceTint = role("surface_tint") ?: base.surfaceTint,
        inverseSurface = role("inverse_surface") ?: base.inverseSurface,
        inverseOnSurface = role("inverse_on_surface") ?: base.inverseOnSurface,
        surfaceDim = role("surface_dim") ?: base.surfaceDim,
        surfaceBright = role("surface_bright") ?: base.surfaceBright,
        surfaceContainerLowest = role("surface_container_lowest") ?: base.surfaceContainerLowest,
        surfaceContainerLow = role("surface_container_low") ?: base.surfaceContainerLow,
        surfaceContainer = role("surface_container") ?: base.surfaceContainer,
        surfaceContainerHigh = role("surface_container_high") ?: base.surfaceContainerHigh,
        surfaceContainerHighest = role("surface_container_highest") ?: base.surfaceContainerHighest,
        outline = role("outline") ?: base.outline,
        outlineVariant = role("outline_variant") ?: base.outlineVariant,
        scrim = role("scrim") ?: base.scrim,
        error = role("error") ?: base.error,
        onError = role("on_error") ?: base.onError,
        errorContainer = role("error_container") ?: base.errorContainer,
        onErrorContainer = role("on_error_container") ?: base.onErrorContainer,
    )
}.getOrNull()

internal fun loadDynamicColorScheme(
    base: ColorScheme,
    dark: Boolean,
    caelestiaPath: Path = caelestiaSchemePath(),
    matugenPath: Path = matugenColorsPath(),
): ColorScheme? {
    for (path in listOf(caelestiaPath, matugenPath)) {
        if (!Files.isRegularFile(path)) continue
        val parsed = runCatching { Files.readString(path) }.getOrNull()
            ?.let { parseDynamicColorScheme(it, base, dark) }
        if (parsed != null) return parsed
    }
    return null
}

internal fun loadCaelestiaMode(caelestiaPath: Path = caelestiaSchemePath()): Boolean? {
    if (!Files.isRegularFile(caelestiaPath)) return null
    return runCatching { Files.readString(caelestiaPath) }.getOrNull()?.let(::parseCaelestiaMode)
}

@Composable
private fun rememberMatugenRevision(enabled: Boolean): Int {
    var revision by remember { mutableStateOf(0) }
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        val watchDirs = listOfNotNull(
            caelestiaSchemePath().parent,
            matugenColorsPath().parent,
        ).distinct().filter { Files.isDirectory(it) }
        if (watchDirs.isEmpty()) return@DisposableEffect onDispose {}

        val watcher = runCatching { FileSystems.getDefault().newWatchService() }.getOrNull()
            ?: return@DisposableEffect onDispose {}
        val watchedNames = setOf("scheme.json", "colors.json")
        watchDirs.forEach { dir ->
            runCatching {
                dir.register(
                    watcher,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                )
            }
        }
        val worker = thread(isDaemon = true, name = "matugen-theme-watcher") {
            while (!Thread.currentThread().isInterrupted) {
                val key = runCatching { watcher.take() }.getOrNull() ?: break
                var changed = false
                for (event in key.pollEvents()) {
                    val fileName = (event.context() as? Path)?.fileName?.toString()
                    if (fileName in watchedNames) changed = true
                }
                if (changed) revision++
                if (!key.reset()) break
            }
        }
        onDispose {
            worker.interrupt()
            runCatching { watcher.close() }
        }
    }
    return revision
}

@Composable
fun ClockTheme(
    settings: ThemeSettings = ThemeSettings(),
    content: @Composable () -> Unit,
) {
    val matugenRev = rememberMatugenRevision(
        enabled = settings.dynamicColor || settings.darkMode == DarkMode.SYSTEM,
    )
    val caelestiaMode = remember(matugenRev) { loadCaelestiaMode() }
    val dark = when (settings.darkMode) {
        DarkMode.SYSTEM -> caelestiaMode ?: isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }

    val fallback = if (dark) settings.palette.dark else settings.palette.light
    val base = remember(settings.dynamicColor, settings.palette, dark, matugenRev) {
        if (settings.dynamicColor) loadDynamicColorScheme(fallback, dark) ?: fallback else fallback
    }
    val amoled = dark && settings.amoledBlack
    val scheme = remember(base, amoled) { if (amoled) base.toAmoledBlack() else base }

    CompositionLocalProvider(
        LocalTonalElevationEnabled provides !amoled,
    ) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = remember { clockTypography() },
            content = content,
        )
    }
}

fun ColorScheme.toAmoledBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF141018),
    scrim = Color.Black,
)
