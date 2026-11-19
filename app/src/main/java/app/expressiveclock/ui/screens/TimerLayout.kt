package app.expressiveclock.ui.screens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spend spare height on the keypad; preserve comfortable targets and scroll on short screens. */
internal fun timerKeyHeight(viewport: Dp, readout: Dp, presets: Dp, spacing: Dp): Dp =
    ((viewport - readout - presets - spacing) / 4).coerceIn(60.dp, 76.dp)
