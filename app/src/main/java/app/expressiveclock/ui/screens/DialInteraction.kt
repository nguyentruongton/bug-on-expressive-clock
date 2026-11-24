package app.expressiveclock.ui.screens

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.expressiveclock.ui.luminousElasticClickable

/** The Stopwatch’s state-change bounce and platform haptics, shared by both dials. */
@Composable
internal fun rememberDialRunningFeedback(running: Boolean?, identity: Any? = Unit): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val scale = remember(identity) { Animatable(1f) }
    var previous by remember(identity) { mutableStateOf<Boolean?>(null) }
    val view = LocalView.current
    LaunchedEffect(identity, running) {
        val before = previous
        previous = running
        if (before != null && running != null && before != running) {
            view.performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (running) HapticFeedbackConstants.GESTURE_START else HapticFeedbackConstants.GESTURE_END
            } else HapticFeedbackConstants.KEYBOARD_TAP)
            scale.snapTo(.95f)
            scale.animateTo(1f, spring(dampingRatio = .58f, stiffness = 380f))
        } else if (running == null) scale.snapTo(1f)
    }
    return scale
}

@Composable
internal fun Modifier.timerDialTap(enabled: Boolean, running: Boolean, onToggle: () -> Unit): Modifier =
    if (!enabled) this else semantics {
        role = Role.Button
        stateDescription = if (running) "Running timer. Tap to pause" else "Paused timer. Tap to resume"
    }.luminousElasticClickable(
        shape = CircleShape,
        maxStretchPercent = .04f,
        maxTranslateDp = 2.dp,
        luminousRadiusScale = .58f,
        onClick = onToggle,
    )

/** Keep the original 410dp face wherever it fits with 16dp side clearance.
 * Clearance includes the inside border and small elastic overshoot; the existing diffuse
 * lighting may feather beyond it, as before. Do not reserve its entire transparent tail.
 */
internal fun stopwatchFaceDiameter(width: Dp): Dp =
    minOf(410.dp, (width - 32.dp).coerceAtLeast(1.dp))
