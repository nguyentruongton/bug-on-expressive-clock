package androidx.compose.ui.platform

import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf

val LocalView = staticCompositionLocalOf { View() }
