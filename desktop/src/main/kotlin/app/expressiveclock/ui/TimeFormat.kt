package app.expressiveclock.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal fun rememberSystem24HourFormat(): Boolean = remember {
    DateFormat.is24HourFormat(null)
}
