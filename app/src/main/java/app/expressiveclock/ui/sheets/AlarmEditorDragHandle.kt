package app.expressiveclock.ui.sheets

import androidx.compose.runtime.Composable

/** Keep the alarm editor’s existing compact handle appearance and accessibility actions. */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun AlarmEditorDragHandle(
    sheetState: androidx.compose.material3.SheetState? = null,
    onDismiss: (() -> Unit)? = null,
) = SheetDragHandle(sheetState, onDismiss, compact = true)
