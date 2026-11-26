package androidx.activity.compose

import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

interface ActivityResultLauncher<I> {
    fun launch(input: I)
}

@Composable
fun <I, O> rememberLauncherForActivityResult(
    contract: ActivityResultContract<I, O>,
    onResult: (O) -> Unit,
): ActivityResultLauncher<I> = remember(contract) {
    object : ActivityResultLauncher<I> {
        override fun launch(input: I) {
            // No-op on desktop.
        }
    }
}
