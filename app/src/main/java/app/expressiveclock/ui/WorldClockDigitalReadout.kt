package app.expressiveclock.ui

import android.animation.ValueAnimator
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import app.expressiveclock.ui.theme.ClockFace
import app.expressiveclock.ui.theme.Numerals
import kotlinx.coroutines.delay
import kotlin.math.abs

internal const val WORLD_CLOCK_READOUT_WIDTH_FRACTION = .48f

private data class ClockReelTarget(val char: Char, val location: String)

/** Timer setup's ink-tight rounded numerals and exact horizontal layout, fitted to this header. */
@Composable
internal fun WorldClockDigitalReadout(text: String, locationKey: String,
    foreground: Color, accent: Color, modifier: Modifier = Modifier, widthFraction: Float = WORLD_CLOCK_READOUT_WIDTH_FRACTION) {
    var previousText by remember { mutableStateOf(text) }
    var previousLocation by remember { mutableStateOf(locationKey) }
    val selection = remember(text, locationKey) { previousLocation != locationKey }
    val changes = remember(text, locationKey) { mechanicalChanges(previousText, text) }
    SideEffect { previousText = text; previousLocation = locationKey }
    BoxWithConstraints(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = text }) {
        val displayWidth = maxWidth * widthFraction.coerceIn(.1f, 1f)
        val metrics = timerReadoutGeometry(displayWidth, text)
        Row(Modifier.width(displayWidth).align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(metrics.spacing),
            verticalAlignment = Alignment.CenterVertically) {
            text.forEachIndexed { index, char ->
                val rank = if (selection) index else changes.indexOf(index).coerceAtLeast(0)
                WorldClockReel(char, locationKey, rank, selection,
                    modifier = Modifier.width(if (char == ':') metrics.colonWidth else metrics.digitWidth)
                        .height(metrics.capHeight + 8.dp)
                        .wrapContentHeight(unbounded = true).requiredHeight(metrics.capHeight + 32.dp)
                        .featheredVerticalViewport(active = true,
                            fadeHeightFraction = 12f / (metrics.capHeight.value + 32f))) { digit ->
                    val color = if (index < 2) foreground else accent
                    if (digit == ':') Text(":", fontFamily = ClockFace.family(36f, 100f, 550),
                        fontSize = (metrics.capHeight.value * .65f).sp, color = color)
                    else Numerals(text = digit.toString(), capHeight = metrics.capHeight, color = color,
                        width = 82f, weight = 600, tracking = .008f, slashedZero = false, roundness = ClockFace.ROUNDED)
                }
            }
        }
    }
}


/** Card typography and natural tabular advances, sharing the header's reel/blur motion. */
@Composable
internal fun WorldClockCardTime(text: String, locationKey: String, style: TextStyle,
    color: Color, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    var previous by remember { mutableStateOf(text) }
    val oldText = remember(text) { previous }
    val changes = remember(text) { mechanicalChanges(previous, text) }
    SideEffect { previous = text }
    val layout = remember(text, style) { measurer.measure(text, style) }
    val widths = remember(text, style, layout) { text.indices.map { index ->
        abs(layout.getHorizontalPosition(index + 1, true) - layout.getHorizontalPosition(index, true))
    } }
    val height = with(density) { style.lineHeight.toDp() }
    Row(modifier.clearAndSetSemantics { contentDescription = text }, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { index, char ->
            WorldClockReel(char, locationKey, changes.indexOf(index).coerceAtLeast(0), false,
                initialChar = oldText.getOrNull(index) ?: ' ', blurRadius = 4.dp,
                modifier = Modifier.width(with(density) { widths[index].toDp() }).height(height)
                    .wrapContentHeight(unbounded = true).requiredHeight(height + 16.dp)
                    .featheredVerticalViewport(active = true, fadeHeightFraction = 8f / (height.value + 16f))) { digit ->
                Text(digit.toString(), style = style, color = color, maxLines = 1)
            }
        }
    }
}

/** Shared target identity keeps unchanged digits stationary and safely interrupts moving reels. */
@Composable
private fun WorldClockReel(char: Char, locationKey: String, rank: Int, selection: Boolean,
    modifier: Modifier, initialChar: Char = char, blurRadius: Dp = 6.dp,
    content: @Composable (Char) -> Unit) {
    val animate = ValueAnimator.areAnimatorsEnabled()
    var staged by remember { mutableStateOf(ClockReelTarget(initialChar, locationKey)) }
    LaunchedEffect(char, locationKey) {
        val next = ClockReelTarget(char, locationKey)
        if (staged != next) {
            if (animate) delay((if (selection) (rank * 26).coerceAtMost(182) else (rank * 45).coerceAtMost(225)).toLong())
            staged = next
        }
    }
    AnimatedContent(staged, modifier = modifier, contentAlignment = Alignment.Center,
        transitionSpec = {
            val locationTransition = initialState.location != targetState.location
            if (!animate) EnterTransition.None togetherWith ExitTransition.None
            else ((slideInVertically(spring(dampingRatio = 1f, stiffness = 420f)) {
                if (locationTransition) -it else it
            } + fadeIn(tween(100))) togetherWith
                (slideOutVertically(tween(180, easing = FastOutSlowInEasing)) {
                    if (locationTransition) it else -it
                } + fadeOut(tween(90)))).using(SizeTransform(clip = false))
        }, label = "worldTimerDigit") { reel ->
        val blurProgress = if (animate) {
            val travel by transition.animateFloat(transitionSpec = {
                if (targetState == EnterExitState.Visible) spring(dampingRatio = 1f, stiffness = 420f)
                else tween(180, easing = FastOutSlowInEasing)
            }, label = "digitEdgeBlur") { state ->
                when (state) {
                    EnterExitState.PreEnter -> 1f
                    EnterExitState.Visible -> 0f
                    EnterExitState.PostExit -> -1f
                }
            }
            val edge = (abs(travel) / .75f).coerceIn(0f, 1f)
            edge * edge * (3f - 2f * edge)
        } else 0f
        Box(Modifier.fillMaxSize().blur(blurRadius * blurProgress,
            edgeTreatment = BlurredEdgeTreatment.Unbounded), contentAlignment = Alignment.Center) {
            content(reel.char)
        }
    }
}
