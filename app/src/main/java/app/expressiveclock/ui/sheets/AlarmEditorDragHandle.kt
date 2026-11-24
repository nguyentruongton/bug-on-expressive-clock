package app.expressiveclock.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.collapse
import kotlinx.coroutines.launch

/** Visual handle without pointer handlers; the sheet owns all drag gestures. */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun AlarmEditorDragHandle(
    sheetState: androidx.compose.material3.SheetState? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Box(
        Modifier.fillMaxWidth().height(32.dp).semantics {
            contentDescription = "Drag handle"
            if (sheetState != null) {
                dismiss {
                    scope.launch {
                        sheetState.hide()
                        if (!sheetState.isVisible) onDismiss?.invoke()
                    }
                    true
                }
                if (sheetState.currentValue == androidx.compose.material3.SheetValue.PartiallyExpanded) {
                    expand { scope.launch { sheetState.expand() }; true }
                } else if (sheetState.hasPartiallyExpandedState) {
                    collapse { scope.launch { sheetState.partialExpand() }; true }
                }
            }
        },
        Alignment.Center,
    ) {
        Box(Modifier.size(32.dp, 4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f), CircleShape))
    }
}
