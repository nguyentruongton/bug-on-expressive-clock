package app.expressiveclock.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.input.key.Key
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import java.awt.GraphicsEnvironment
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

sealed interface UiScaleShortcutAction {
    data class Step(val delta: Float) : UiScaleShortcutAction
    data object Reset : UiScaleShortcutAction
}

data class DesktopUiScaleState(
    val uiScale: Float = 1.0f,
    val baseScale: Float = 1.0f,
    val effectiveScale: Float = 1.0f,
    val onUiScaleChange: (Float) -> Unit = {},
)

val LocalDesktopUiScale = compositionLocalOf { DesktopUiScaleState() }

object DesktopDisplayScale {
    const val MIN_UI_SCALE = 0.8f
    const val MAX_UI_SCALE = 1.5f
    const val UI_SCALE_STEP = 0.1f

    val UI_SCALE_OPTIONS: List<Float> = listOf(0.8f, 0.9f, 1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f)

    private val json = Json { ignoreUnknownKeys = true }
    private val xrdbDpiRegex = Regex("""Xft\.dpi:\s*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)

    private val cachedHyprMonitorsJson: String? by lazy { runCommandWithTimeout(250L, "hyprctl", "monitors", "-j") }
    private val cachedXrdbOutput: String? by lazy { runCommandWithTimeout(250L, "xrdb", "-query") }

    internal fun runCommandWithTimeout(timeoutMs: Long = 250L, vararg command: String): String? = runCatching {
        val process = ProcessBuilder(*command)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        val stdoutFuture = CompletableFuture.supplyAsync {
            runCatching { process.inputStream.bufferedReader().use { it.readText() } }.getOrNull()
        }
        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            stdoutFuture.cancel(true)
            return null
        }
        if (process.exitValue() != 0) {
            stdoutFuture.cancel(true)
            return null
        }
        runCatching { stdoutFuture.get(100, TimeUnit.MILLISECONDS) }.getOrNull()
    }.getOrNull()

    fun queryHyprctlMonitorsJson(): String? = cachedHyprMonitorsJson

    fun queryXrdb(): String? = cachedXrdbOutput

    fun normalizeUiScale(scale: Float): Float {
        if (!scale.isFinite()) return 1.0f
        val clamped = scale.coerceIn(MIN_UI_SCALE, MAX_UI_SCALE)
        return (clamped * 20f).roundToInt() / 20f
    }

    fun parseHyprlandScale(monitorsJson: String?): Float? {
        if (monitorsJson.isNullOrBlank()) return null
        return runCatching {
            val array = json.parseToJsonElement(monitorsJson) as? JsonArray ?: return null
            var firstValid: Float? = null
            for (item in array) {
                val obj = item as? JsonObject ?: continue
                val scale = (obj["scale"] as? JsonPrimitive)?.floatOrNull
                if (scale != null && scale.isFinite() && scale in 0.5f..4.0f) {
                    if (firstValid == null) firstValid = scale
                    val focused = (obj["focused"] as? JsonPrimitive)?.booleanOrNull == true
                    if (focused) return@runCatching scale
                }
            }
            firstValid
        }.getOrNull()
    }

    fun parseXrdbScale(xrdbOutput: String?): Float? {
        if (xrdbOutput.isNullOrBlank()) return null
        val match = xrdbDpiRegex.find(xrdbOutput) ?: return null
        val dpi = match.groupValues.getOrNull(1)?.toFloatOrNull() ?: return null
        val scale = dpi / 96f
        return scale.takeIf { it.isFinite() && it in 0.5f..4.0f }
    }

    fun resolveBaseScale(
        awtDensity: Float,
        env: Map<String, String> = System.getenv(),
        hyprMonitorsJsonProvider: () -> String? = ::queryHyprctlMonitorsJson,
        xrdbOutputProvider: () -> String? = ::queryXrdb,
    ): Float {
        val safeAwt = if (awtDensity.isFinite() && awtDensity >= 0.5f) awtDensity else 1.0f

        env["EXPRESSIVE_CLOCK_SCALE"]
            ?.trim()
            ?.toFloatOrNull()
            ?.takeIf { it.isFinite() && it in 0.5f..4.0f }
            ?.let { return it }

        if (safeAwt > 1.05f) {
            return safeAwt
        }

        for (envKey in arrayOf("GDK_SCALE", "QT_SCALE_FACTOR")) {
            env[envKey]
                ?.trim()
                ?.toFloatOrNull()
                ?.takeIf { it.isFinite() && it > 1.05f && it <= 4.0f }
                ?.let { return it }
        }

        parseHyprlandScale(hyprMonitorsJsonProvider())
            ?.takeIf { it in 0.5f..4.0f }
            ?.let { return it }

        parseXrdbScale(xrdbOutputProvider())
            ?.takeIf { it in 0.5f..4.0f }
            ?.let { return it }

        return safeAwt
    }

    fun compensationFactor(awtDensity: Float, baseScale: Float): Float {
        val safeAwt = if (awtDensity.isFinite() && awtDensity >= 0.5f) awtDensity else 1.0f
        val safeBase = if (baseScale.isFinite() && baseScale >= 0.5f) baseScale else safeAwt
        return (safeBase / safeAwt).coerceIn(0.5f, 4.0f)
    }

    fun effectiveDensity(awtDensity: Float, baseScale: Float, uiScale: Float): Float {
        val safeAwt = if (awtDensity.isFinite() && awtDensity >= 0.5f) awtDensity else 1.0f
        val comp = compensationFactor(safeAwt, baseScale)
        return (safeAwt * comp * normalizeUiScale(uiScale)).coerceIn(0.75f, 4.0f)
    }

    fun detectInitialAwtScale(): Float = runCatching {
        if (GraphicsEnvironment.isHeadless()) return 1.0f
        val scale = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice
            .defaultConfiguration
            .defaultTransform
            .scaleX
            .toFloat()
        if (scale.isFinite() && scale >= 0.5f) scale else 1.0f
    }.getOrDefault(1.0f)

    fun shortcutActionForKey(
        isCtrlOrMetaPressed: Boolean,
        key: Key,
    ): UiScaleShortcutAction? {
        if (!isCtrlOrMetaPressed) return null
        return when (key) {
            Key.Equals, Key.Plus, Key.NumPadAdd -> UiScaleShortcutAction.Step(+UI_SCALE_STEP)
            Key.Minus, Key.NumPadSubtract -> UiScaleShortcutAction.Step(-UI_SCALE_STEP)
            Key.Zero, Key.NumPad0 -> UiScaleShortcutAction.Reset
            else -> null
        }
    }

    fun nextUiScaleForShortcut(
        currentScale: Float,
        isCtrlOrMetaPressed: Boolean,
        key: Key,
    ): Float? = when (val action = shortcutActionForKey(isCtrlOrMetaPressed, key)) {
        is UiScaleShortcutAction.Step -> normalizeUiScale(currentScale + action.delta)
        UiScaleShortcutAction.Reset -> 1.0f
        null -> null
    }
}
