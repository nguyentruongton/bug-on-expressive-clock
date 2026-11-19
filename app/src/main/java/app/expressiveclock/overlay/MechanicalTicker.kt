package app.expressiveclock.overlay

import android.animation.ValueAnimator
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Don't roll through invented intermediate values after a reset/resync or format change. */
internal fun tickerCanRoll(old: String, new: String): Boolean {
    fun seconds(s: String): Long? {
        val fields = s.trimEnd('m', 's').split(':')
        return fields.fold(0L) { value, field -> value * 60 + (field.toLongOrNull() ?: return null) }
    }
    return old.length == new.length && old.filter { !it.isDigit() } == new.filter { !it.isDigit() } &&
        seconds(old)?.let { a -> seconds(new)?.let { b -> kotlin.math.abs(b - a) == 1L } } == true
}
internal fun tickerDelay(index: Int, length: Int, down: Boolean): Int =
    ((if (down) length - 1 - index else index).coerceAtLeast(0) * 38).coerceAtMost(190)

@Composable
internal fun mechanicalRollEnabled(text: String): Boolean {
    var previous by remember { mutableStateOf(text) }
    val enabled = tickerCanRoll(previous, text) && ValueAnimator.areAnimatorsEnabled()
    SideEffect { previous = text }
    // Remember the decision for this update; recomposition during the animation must not disable it.
    return remember(text) { enabled }
}

/** Adapted from Expressive Clock TimersScreen.TickerCell: full-cell slide, 380/.68 detent,
 * 180ms outgoing movement, 38ms sequential offsets and per-digit progressive blur. */
@Composable
internal fun MechanicalDigit(
    char: Char, index: Int, length: Int, down: Boolean, roll: Boolean,
    modifier: Modifier = Modifier, content: @Composable (Char) -> Unit,
) {
    var displayed by remember { mutableStateOf(char) }
    LaunchedEffect(char, roll) {
        if (roll) delay(tickerDelay(index, length, down).toLong())
        displayed = char
    }
    AnimatedContent(
        targetState = displayed,
        modifier = modifier.clipToBounds(),
        transitionSpec = {
            if (!roll) EnterTransition.None togetherWith ExitTransition.None
            else {
                val direction = if (down) -1 else 1
                (slideInVertically(spring(stiffness = 380f, dampingRatio = 0.68f)) { it * direction } + fadeIn(tween(90))) togetherWith
                    (slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { -it * direction } + fadeOut(tween(70)))
            } using SizeTransform(clip = true)
        }, label = "mechanicalDigit$index",
    ) { digit ->
        val blur by transition.animateFloat(transitionSpec = { tween(180, easing = FastOutSlowInEasing) }, label = "mechanicalBlur") {
            if (it == EnterExitState.Visible) 0f else 1f
        }
        Box(Modifier.blur((blur * 3.5f).dp).padding(vertical = 4.dp), contentAlignment = Alignment.Center) { content(digit) }
    }
}
