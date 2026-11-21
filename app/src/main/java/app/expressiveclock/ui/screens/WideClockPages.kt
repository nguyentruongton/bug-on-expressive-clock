package app.expressiveclock.ui.screens

import android.animation.ValueAnimator
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.LayerOutsets
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.expressiveclock.core.WorldCity
import app.expressiveclock.ui.WorldClockDigitalReadout
import app.expressiveclock.ui.MechanicalText
import app.expressiveclock.ui.rememberSystem24HourFormat
import app.expressiveclock.ui.rememberWallTicker
import app.expressiveclock.ui.sheets.prettify
import app.expressiveclock.ui.theme.WorldClockType
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Enough room for a comfortable clock pane and two real city-card columns. */
internal fun useWideClockPages(width: Float, height: Float, fontScale: Float): Boolean =
    width >= maxOf(720f, 2f * (132f * fontScale * 2f + 26f) + 56f) && height >= 520f

/** A retained two-pane clock destination, with only its active functional pane accepting input. */
@Composable
internal fun WideClockPages(
    worldSelected: Boolean,
    clockOnly: Boolean = false,
    panesOnly: Boolean = false,
    clockProgress: (() -> Float)? = null,
    cities: List<WorldCity>, selectedZoneId: String?,
    onSelectCity: (WorldCity) -> Unit, onShowLocalTime: () -> Unit,
    alarmContent: @Composable (Modifier) -> Unit,
    showAlarmEditor: Boolean,
    alarmEditor: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val now by rememberWallTicker()
    val local = prettify(ZoneId.systemDefault().id).copy(region = "Local Time")
    val selected = cities.firstOrNull { it.zone.id == selectedZoneId } ?: local
    val clockCity = if (worldSelected) selected else local
    val motion = remember { Animatable(if (worldSelected) 1f else 0f) }
    LaunchedEffect(worldSelected) {
        val target = if (worldSelected) 1f else 0f
        if (motion.value == target && !motion.isRunning) return@LaunchedEffect
        if (ValueAnimator.areAnimatorsEnabled())
            motion.animateTo(target, spring(dampingRatio = 1f, stiffness = 300f, visibilityThreshold = .001f))
        else motion.snapTo(target)
    }
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val use24h = rememberSystem24HourFormat()
    BoxWithConstraints(modifier.fillMaxSize().then(if (clockOnly) Modifier else Modifier.worldClockAmbient(scheme.primary, scheme.surface.luminance() < .5f))) {
        val pane = (maxWidth - 24.dp) / 2
        val cityFadeBounds = remember(pane) { cityLightLayerOutsets((pane - 26.dp) / 2) }
        val alarmFadeBounds = remember { LayerOutsets(32.dp) }
        val dial = minOf(pane - 24.dp, maxHeight - 148.dp, 320.dp).coerceAtLeast(100.dp)
        val top = ((maxHeight - dial - 124.dp) / 2).coerceAtLeast(8.dp)
        val leftClock = (pane - dial) / 2
        val rightClock = pane + 24.dp + leftClock
        val editorVisible = showAlarmEditor && !worldSelected
        val clockAlpha = animateFloatAsState(if (editorVisible) 0f else 1f, tween(200), label = "clockEditorVisibility")

        // Pager destinations retain their own two-pane content. Do not leave an invisible
        // outgoing pane over the clock: its touch blocker would swallow page swipes.
        if (!clockOnly && !worldSelected) Box(Modifier.width(pane).fillMaxHeight().graphicsLayer {
            outsets = alarmFadeBounds
            alpha = (1f - motion.value * 1.65f).coerceIn(0f, 1f)
            translationX = -24.dp.toPx() * motion.value
        }.inactiveClockPane(worldSelected), contentAlignment = Alignment.TopCenter) {
            alarmContent(Modifier.width(minOf(pane - 16.dp, 520.dp)).fillMaxHeight())
        }
        if (!clockOnly && worldSelected) Box(Modifier.offset(x = pane + 24.dp).width(pane).fillMaxHeight()
            .graphicsLayer {
                outsets = cityFadeBounds
                // Each card and its halo share one staggered entrance below. A second
                // pane fade compounds opacity and makes faint lighting arrive late.
            }
            .inactiveClockPane(!worldSelected), contentAlignment = Alignment.Center) {
            SavedLocationGrid(cities = cities, localCity = local,
                onShowLocalTime = onShowLocalTime, selectedZoneId = selectedZoneId,
                nowUtcMillis = now, onSelectCity = onSelectCity,
                state = LocalWorldClockPresentation.current.grid,
                entranceVisible = worldSelected, centreShortContent = true, modifier = Modifier.fillMaxSize())
        }
        // No AnimatedContent or keys around the clock: the hands and second-hand frame source
        // survive every Alarm/World switch, including interrupted position animations.
        if (!panesOnly) {
            AnalogueWorldClock(clockCity.zone, now, Modifier.offset {
                IntOffset(with(density) { (rightClock + (leftClock - rightClock) * (clockProgress?.invoke() ?: motion.value)).roundToPx() },
                    with(density) { top.roundToPx() })
            }.size(dial).graphicsLayer { alpha = clockAlpha.value }
                .then(if (editorVisible) Modifier.clearAndSetSemantics {} else Modifier), active = !editorVisible)

            Box(Modifier.offset {
                IntOffset(with(density) { ((pane + 24.dp) * (1f - (clockProgress?.invoke() ?: motion.value))).roundToPx() },
                    with(density) { (top + dial + 24.dp).roundToPx() })
            }.width(pane).graphicsLayer { alpha = clockAlpha.value }
                .then(if (editorVisible) Modifier.clearAndSetSemantics {} else Modifier)) {
                AnimatedContent(worldSelected, transitionSpec = {
                    (fadeIn(tween(250, delayMillis = 100)) + slideInVertically(tween(250)) { it / 10 }) togetherWith
                        fadeOut(tween(160))
                }, label = "sharedClockDetails") { world ->
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (world) {
                            MechanicalText(selected.city, WorldClockType.city(24.sp), scheme.onSurface,
                                modifier = Modifier.fillMaxWidth(), selectionKey = selected.zone.id, progressiveBlur = true)
                            val description = selected.timeAt(now).format(DateTimeFormatter.ofPattern("zzzz · EEE, d MMM", locale))
                            WorldClockSubtitle(description)
                        } else {
                            val localTime = local.timeAt(now)
                            val digits = localTime.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm:ss" else "hh:mm:ss"))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                WorldClockDigitalReadout(digits, local.zone.id,
                                    scheme.onSurface, scheme.primary, modifier = Modifier.weight(1f))
                                if (!use24h) Text(localTime.format(DateTimeFormatter.ofPattern("a", locale)),
                                    style = WorldClockType.secondary(14.sp), color = scheme.onSurface)
                            }
                            Text(local.timeAt(now).format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)),
                                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (!clockOnly && showAlarmEditor) Box(Modifier.offset(x = pane + 24.dp).width(pane).fillMaxHeight()
            .graphicsLayer { alpha = if (editorVisible) 1f else 0f }
            .inactiveClockPane(!editorVisible)) { alarmEditor() }
    }
}

/** Invisible outgoing panes neither intercept touches nor expose duplicate accessibility content. */
private fun Modifier.inactiveClockPane(inactive: Boolean): Modifier = if (!inactive) this else
    clearAndSetSemantics {}.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
