package androidx.compose.foundation.layout

import androidx.compose.runtime.Composable

@ExperimentalLayoutApi
val WindowInsets.Companion.imeAnimationTarget: WindowInsets
    @Composable get() = WindowInsets(0, 0, 0, 0)

@ExperimentalLayoutApi
val WindowInsets.Companion.imeAnimationSource: WindowInsets
    @Composable get() = WindowInsets(0, 0, 0, 0)
