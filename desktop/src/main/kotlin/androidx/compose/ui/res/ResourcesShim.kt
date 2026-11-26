package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import app.expressiveclock.DesktopResources

@Composable
fun stringResource(id: Int): String = DesktopResources.strings[id] ?: ""

@Composable
fun stringResource(id: Int, vararg formatArgs: Any): String {
    val template = DesktopResources.strings[id] ?: return ""
    return runCatching { template.format(*formatArgs) }.getOrDefault(template)
}
