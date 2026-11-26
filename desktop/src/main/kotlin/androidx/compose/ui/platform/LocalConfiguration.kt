package androidx.compose.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

class DesktopLocaleList(private val primary: Locale = Locale.getDefault()) {
    operator fun get(index: Int): Locale = primary
    fun toLanguageTags(): String = primary.toLanguageTag()
}

data class DesktopConfiguration(
    val screenWidthDp: Int = 960,
    val screenHeightDp: Int = 720,
    val locales: DesktopLocaleList = DesktopLocaleList(),
) {
    val smallestScreenWidthDp: Int get() = minOf(screenWidthDp, screenHeightDp)
}

val LocalConfiguration = staticCompositionLocalOf { DesktopConfiguration() }
