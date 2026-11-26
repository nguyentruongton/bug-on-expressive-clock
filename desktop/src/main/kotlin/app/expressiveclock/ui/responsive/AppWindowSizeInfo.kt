package app.expressiveclock.ui.responsive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val TWO_PANE_MIN_WIDTH = 600.dp
private val TWO_PANE_MIN_HEIGHT = 600.dp

enum class AppWindowWidthClass {
    COMPACT,
    EXPANDED,
}

data class AppWindowSizeInfo(
    val widthClass: AppWindowWidthClass,
    val screenWidthDp: Dp,
    val screenHeightDp: Dp,
) {
    val isHeightCompact: Boolean
        get() = screenHeightDp < TWO_PANE_MIN_HEIGHT

    val isExpanded: Boolean
        get() = widthClass == AppWindowWidthClass.EXPANDED

    val isCompact: Boolean
        get() = widthClass == AppWindowWidthClass.COMPACT
}

@Composable
fun rememberAppWindowSizeInfo(): AppWindowSizeInfo {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val sizePx = windowInfo.containerSize
    val widthDp = with(density) { sizePx.width.toDp() }.coerceAtLeast(360.dp)
    val heightDp = with(density) { sizePx.height.toDp() }.coerceAtLeast(480.dp)

    val widthClass = if (widthDp >= TWO_PANE_MIN_WIDTH && heightDp >= TWO_PANE_MIN_HEIGHT) {
        AppWindowWidthClass.EXPANDED
    } else {
        AppWindowWidthClass.COMPACT
    }

    return AppWindowSizeInfo(
        widthClass = widthClass,
        screenWidthDp = widthDp,
        screenHeightDp = heightDp,
    )
}

val LocalAppWindowSizeInfo = staticCompositionLocalOf {
    AppWindowSizeInfo(
        widthClass = AppWindowWidthClass.EXPANDED,
        screenWidthDp = 960.dp,
        screenHeightDp = 720.dp,
    )
}
