package app.expressiveclock.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GlassBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.background)) { content() }
}

@Composable
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color = Color.White.copy(alpha = .88f),
    borderAlpha: Float = .82f,
    accent: Color? = null,
    shadowElevation: Dp = 10.dp,
): Modifier {
    val isDark = tint.luminance() < .5f
    val resolvedAccent = accent ?: MaterialTheme.colorScheme.primary
    val shadow = if (shadowElevation > 0.dp) {
        Modifier.shadow(
            elevation = shadowElevation,
            shape = shape,
            ambientColor = if (isDark) Color.Black.copy(alpha = .40f) else resolvedAccent.copy(alpha = .10f),
            spotColor = if (isDark) Color.Black.copy(alpha = .55f) else resolvedAccent.copy(alpha = .18f),
        )
    } else {
        Modifier
    }
    return this
        .then(shadow)
        .background(
            Brush.linearGradient(
                colors = if (isDark) {
                    listOf(tint.copy(alpha = .94f), tint.copy(alpha = .88f), tint.copy(alpha = .84f))
                } else {
                    listOf(tint.copy(alpha = .96f), tint.copy(alpha = .88f), tint.copy(alpha = .82f))
                },
                start = Offset(0f, 0f),
                end = Offset(600f, 600f),
            ),
            shape,
        )
        .border(
            1.25.dp,
            if (isDark) Color.White.copy(alpha = .14f) else Color.White.copy(alpha = borderAlpha),
            shape,
        )
}

@Composable
fun FloatingCircle(
    size: Dp,
    modifier: Modifier = Modifier,
    fill: Color,
    border: Color = Color.White.copy(alpha = .82f),
    shadowColor: Color = Color(0xFF1687D0),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.size(size + 56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0.0f to shadowColor.copy(alpha = 0.22f),
                        0.68f to shadowColor.copy(alpha = 0.08f),
                        1.0f to Color.Transparent,
                    ),
                    CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(fill, CircleShape)
                .border(1.25.dp, border, CircleShape),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

@Composable
fun Modifier.blueGlow(
    shape: Shape = RoundedCornerShape(32.dp),
    color: Color = MaterialTheme.colorScheme.primary,
): Modifier = this.shadow(
    elevation = 24.dp,
    shape = shape,
    clip = false,
    ambientColor = color.copy(alpha = .20f),
    spotColor = color.copy(alpha = .32f),
)

@Composable
fun Modifier.elasticClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = this.luminousElasticClickable(
    enabled = enabled,
    shape = CircleShape,
    onClick = onClick,
)

/**
 * Desktop-optimized pointer & iGPU elastic interaction modifier.
 * Avoids heavy offscreen compositing passes on Intel Iris Plus G7 while preserving crisp
 * press/hover spring feedback for mouse clicks.
 */
@Composable
fun Modifier.luminousElasticClickable(
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    maxStretchPercent: Float = 0.092f,
    maxTranslateDp: Dp = 2.6.dp,
    luminousRadiusScale: Float = 1.0f,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            !enabled -> 1f
            isPressed -> 1f - (maxStretchPercent * 0.45f).coerceIn(0.02f, 0.06f)
            isHovered -> 1.015f
            else -> 1f
        },
        animationSpec = spring(stiffness = 520f, dampingRatio = 0.72f),
        label = "desktopElasticScale",
    )

    val clickModifier = when {
        onLongClick != null -> Modifier.combinedClickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick,
        )
        onClick != null -> Modifier.clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
        else -> Modifier
    }

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .then(clickModifier)
}
