package app.expressiveclock.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset

/**
 * Fixed-stage dock. The add button never changes the measured width of the stage, so moving from
 * Alarms to another tab cannot make the dock jump/stutter. Instead the dock and add button animate
 * within the same centered stage.
 */
@Composable
fun ClockDock(
    destinations: List<Tab>,
    selected: Tab,
    onSelect: (Tab) -> Unit,
    showAdd: Boolean,
    addLabel: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    enableGlow: Boolean = true,
    pagePosition: (() -> Float)? = null,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val blue = MaterialTheme.colorScheme.primary
    val dockTint = if (dark) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .88f) else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = .84f)

    val addAlpha by animateFloatAsState(
        targetValue = if (showAdd) 1f else 0f,
        animationSpec = tween(180),
        label = "addAlpha",
    )
    val addScale by animateFloatAsState(
        targetValue = if (showAdd) 1f else .78f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 620f),
        label = "addScale",
    )

    BoxWithConstraints(
        modifier = modifier.widthIn(max = DOCK_STAGE_WIDTH + 16.dp).fillMaxWidth().padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val addSize = minOf(DOCK_HEIGHT, (maxWidth - 218.dp).coerceAtLeast(48.dp))
        val dockWidth = minOf(DOCK_WIDTH, (maxWidth - addSize - FAB_GAP).coerceAtLeast(0.dp))
        val dockShift by animateDpAsState(
            targetValue = if (showAdd) 0.dp else (addSize + FAB_GAP) / 2,
            animationSpec = spring(dampingRatio = 0.86f, stiffness = 560f),
            label = "dockShift",
        )

        Row(
            modifier = Modifier
                .width(dockWidth + FAB_GAP + addSize)
                .padding(horizontal = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FAB_GAP),
        ) {
            Box(
                modifier = Modifier
                    .width(dockWidth)
                    .height(DOCK_HEIGHT)
                    .graphicsLayer { translationX = dockShift.toPx() }
                    .luminousElasticClickable(
                        shape = RoundedCornerShape(DOCK_HEIGHT / 2),
                        maxStretchPercent = 0.06f,
                        maxTranslateDp = 1.8.dp,
                    )
                    .glass(
                        RoundedCornerShape(DOCK_HEIGHT / 2),
                        dockTint,
                        .88f,
                        shadowElevation = if (enableGlow) 18.dp else 0.dp,
                    )
                    .then(if (enableGlow) Modifier.blueGlow(RoundedCornerShape(DOCK_HEIGHT / 2)) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    val selectedIndex = destinations.indexOf(selected).coerceAtLeast(0)
                    val itemWidth = maxWidth / destinations.size
                    val indicatorSize = minOf(50.dp, ((dockWidth - 16.dp) / 4 - 4.dp).coerceAtLeast(28.dp))
                    val selectedOffset = if (pagePosition == null) animateDpAsState(
                        targetValue = itemWidth * selectedIndex + (itemWidth - indicatorSize) / 2,
                        animationSpec = spring(dampingRatio = 0.84f, stiffness = 600f),
                        label = "dockIndicator",
                    ) else null
                    Box(
                        modifier = Modifier
                            .offset {
                                // Read continuous pager progress during placement, not
                                // composition. No extra spring trails the user's finger.
                                val position = pagePosition?.invoke()?.coerceIn(0f, destinations.lastIndex.toFloat())
                                val x = if (position == null) selectedOffset!!.value
                                    else itemWidth * position + (itemWidth - indicatorSize) / 2
                                IntOffset(x.roundToPx(), ((DOCK_HEIGHT - indicatorSize) / 2).roundToPx())
                            }
                            .size(indicatorSize)
                            .clip(CircleShape)
                            .background(
                                if (dark) Color(0xFF24303B) else Color.White.copy(alpha = .99f)
                            ),
                    )
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        destinations.forEach { d ->
                            DockItem(
                                tab = d,
                                selected = d == selected,
                                iconColor = if (dark) Color.White.copy(alpha = .94f) else blue,
                                onClick = { onSelect(d) },
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(addSize)
                    .graphicsLayer {
                        alpha = addAlpha
                        scaleX = addScale
                        scaleY = addScale
                    }
                    .luminousElasticClickable(
                        enabled = showAdd,
                        shape = CircleShape,
                        maxStretchPercent = 0.08f,
                        maxTranslateDp = 2.0.dp,
                        onClick = onAdd,
                    )
                    .clip(CircleShape)
                    .glass(
                        CircleShape,
                        if (dark) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f)
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = .58f),
                        .92f,
                        shadowElevation = if (enableGlow) 18.dp else 0.dp,
                    )
                    .then(if (enableGlow) Modifier.blueGlow(CircleShape) else Modifier)
                    .clearAndSetSemantics {
                        if (showAdd) {
                            contentDescription = addLabel
                            role = Role.Button
                            onClick(label = addLabel) { onAdd(); true }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(31.dp))
            }
        }
    }
}

@Composable
private fun RowScope.DockItem(tab: Tab, selected: Boolean, iconColor: Color, onClick: () -> Unit) {
    var tapCount by remember { mutableIntStateOf(0) }
    var previouslySelected by remember { mutableStateOf(selected) }
    // A swipe selects a destination without invoking onClick. Use the same icon
    // animation for every new selection, without playing it on initial composition.
    LaunchedEffect(selected) {
        if (selected && !previouslySelected) tapCount++
        previouslySelected = selected
    }
    val rotation = remember { Animatable(0f) }
    val selectedScale by animateFloatAsState(
        targetValue = if (selected) 1f else .94f,
        animationSpec = tween(180),
        label = "dockScale",
    )

    LaunchedEffect(tapCount) {
        if (tapCount == 0) return@LaunchedEffect
        if (tab == Tab.TIMERS) {
            if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
                val target = ((rotation.value / 180f).toInt() + 1) * 180f
                rotation.animateTo(target, tween(320))
            }
            rotation.snapTo(0f)
            return@LaunchedEffect
        }
        if (tab == Tab.WORLD || tab == Tab.STOPWATCH) {
            if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
                val target = ((rotation.value / 360f).toInt() + 1) * 360f
                rotation.animateTo(target, tween(if (tab == Tab.WORLD) 600 else 420,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing))
            }
            rotation.snapTo(0f)
            return@LaunchedEffect
        }
        rotation.snapTo(0f)
        when (tab) {
            Tab.ALARMS -> {
                rotation.animateTo(-5f, tween(65))
                rotation.animateTo(5f, tween(85))
                rotation.animateTo(-3f, tween(65))
                rotation.animateTo(0f, tween(65))
            }
            Tab.WORLD -> Unit
            Tab.TIMERS -> Unit
            Tab.STOPWATCH -> Unit
        }
        rotation.snapTo(0f)
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .height(DOCK_HEIGHT)
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    // New selections animate via selected above. A repeated tap
                    // still replays the animation without double-triggering a new tab.
                    if (selected) tapCount++
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (tab == Tab.WORLD) {
            RotatingGlobe(
                phase = { rotation.value },
                tint = if (selected) MaterialTheme.colorScheme.onSurface else iconColor,
                modifier = Modifier.size(27.dp).graphicsLayer { scaleX = selectedScale; scaleY = selectedScale }
                    .clearAndSetSemantics { contentDescription = tab.label },
            )
        } else if (tab == Tab.STOPWATCH) {
            StopwatchDockIcon(
                phase = { rotation.value },
                tint = if (selected) MaterialTheme.colorScheme.onSurface else iconColor,
                modifier = Modifier.size(27.dp).graphicsLayer { scaleX = selectedScale; scaleY = selectedScale }
                    .clearAndSetSemantics { contentDescription = tab.label },
            )
        } else {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = if (selected) MaterialTheme.colorScheme.onSurface else iconColor,
            modifier = Modifier
                .size(27.dp)
                .graphicsLayer {
                    scaleX = selectedScale
                    scaleY = selectedScale
                    when (tab) {
                        Tab.WORLD -> rotationZ = rotation.value
                        Tab.ALARMS -> rotationZ = rotation.value
                        Tab.TIMERS -> rotationZ = rotation.value
                        Tab.STOPWATCH -> Unit
                    }
                    cameraDistance = 18f * density
                },
        )
        }
    }
}

val DOCK_HEIGHT = 72.dp
private val DOCK_WIDTH = 252.dp
private val FAB_GAP = 10.dp
private val DOCK_STAGE_WIDTH = DOCK_WIDTH + FAB_GAP + DOCK_HEIGHT
