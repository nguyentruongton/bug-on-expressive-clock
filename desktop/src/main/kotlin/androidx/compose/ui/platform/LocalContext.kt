package androidx.compose.ui.platform

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

private object DesktopContext : Context

val LocalContext = staticCompositionLocalOf<Context> { DesktopContext }
