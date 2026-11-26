package androidx.compose.ui.text.font

import androidx.compose.ui.text.platform.Font as SkikoFont
import app.expressiveclock.DesktopResources
import java.util.concurrent.ConcurrentHashMap

private val fontBytesCache = ConcurrentHashMap<String, ByteArray>()

fun Font(
    resId: Int,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    loadingStrategy: FontLoadingStrategy = FontLoadingStrategy.Blocking,
    variationSettings: FontVariation.Settings = FontVariation.Settings(),
): Font {
    val path = DesktopResources.fonts[resId]
        ?: error("Desktop font resource not found for id=$resId")
    val bytes = fontBytesCache.computeIfAbsent(path) { key ->
        val stream = DesktopResources::class.java.classLoader.getResourceAsStream(key)
            ?: error("Bundled font file missing on classpath: $key")
        stream.use { it.readBytes() }
    }
    val variationKey = variationSettings.settings.joinToString(",") { "${it.axisName}=${it.toVariationValue(null)}" }
    val identity = "$path:${weight.weight}:$style:$variationKey"
    return SkikoFont(
        identity = identity,
        data = bytes,
        weight = weight,
        style = style,
        variationSettings = variationSettings,
    )
}
