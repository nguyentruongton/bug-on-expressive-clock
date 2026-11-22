package app.expressiveclock.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Cancels superseded requests, including requests still waiting for the host mutex. */
internal class AlarmConfirmationPresenter(private val scope: CoroutineScope) {
    val hostState = SnackbarHostState()
    private var request: Job? = null
    fun show(message: String) {
        request?.cancel()
        request = scope.launch {
            hostState.currentSnackbarData?.dismiss()
            hostState.showSnackbar(message)
        }
    }
}

/** Uses SnackbarHost's accessible announcement, timeout and enter/exit motion. */
@Composable
internal fun AlarmConfirmation(data: SnackbarData) {
    // Each overlapping host item has the same anchor and enough room for wrapped text.
    Box(Modifier.fillMaxWidth().heightIn(min = 96.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)),
        contentAlignment = Alignment.BottomCenter) {
    Surface(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).widthIn(max = 480.dp),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Alarm, contentDescription = null, modifier = Modifier.size(20.dp))
            // Wrap rather than clip if accessibility text cannot fit on one line.
            Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
    }
}
