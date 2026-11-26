package androidx.activity.compose

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

class BackEventCompat(
    val touchX: Float = 0f,
    val touchY: Float = 0f,
    val progress: Float = 0f,
    val swipeEdge: Int = 0,
)

@Composable
fun PredictiveBackHandler(
    enabled: Boolean = true,
    onBack: suspend (progress: Flow<BackEventCompat>) -> Unit = {},
) {
    // No-op on desktop; window back navigation is handled via buttons/Escape.
}

@Composable
fun BackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
) {
    // No-op on desktop.
}
