package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

object AsisMotion {
    fun <T> bouncySpring() = spring<T>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    fun <T> smoothSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    fun <T> snappySpring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    fun offsetSpring() = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}

/**
 * Applies a subtle, organic multi-axis "float and sway" motion using [rememberInfiniteTransition]
 * on a GPU [graphicsLayer], creating a calm floating-depth sensation without triggering relayouts.
 */
fun Modifier.asisFloatAndSway(
    phaseIndex: Int = 0,
    floatAmplitudeDp: Float = 2.8f,
    swayAmplitudeDp: Float = 1.4f,
    tiltDegrees: Float = 0.35f,
    baseDurationMs: Int = 4400
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "asisFloatAndSway_$phaseIndex")
    val safePeriod = baseDurationMs.coerceAtLeast(1800)

    val floatDuration = safePeriod + (phaseIndex % 5) * 320
    val swayDuration = (safePeriod * 1.31f).toInt() + (phaseIndex % 4) * 390
    val tiltDuration = (safePeriod * 1.57f).toInt() + (phaseIndex % 3) * 410

    val floatOffsetDp by transition.animateFloat(
        initialValue = -floatAmplitudeDp,
        targetValue = floatAmplitudeDp,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = floatDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(phaseIndex * 430, StartOffsetType.FastForward)
        ),
        label = "floatY_$phaseIndex"
    )

    val swayOffsetDp by transition.animateFloat(
        initialValue = -swayAmplitudeDp,
        targetValue = swayAmplitudeDp,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = swayDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(phaseIndex * 610 + 270, StartOffsetType.FastForward)
        ),
        label = "swayX_$phaseIndex"
    )

    val swayRollDeg by transition.animateFloat(
        initialValue = -tiltDegrees,
        targetValue = tiltDegrees,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = tiltDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(phaseIndex * 510 + 160, StartOffsetType.FastForward)
        ),
        label = "tiltZ_$phaseIndex"
    )

    val pitchDeg by transition.animateFloat(
        initialValue = -(tiltDegrees * 1.35f),
        targetValue = tiltDegrees * 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = floatDuration + 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(phaseIndex * 340 + 490, StartOffsetType.FastForward)
        ),
        label = "pitchX_$phaseIndex"
    )

    val depthScale by transition.animateFloat(
        initialValue = 0.9965f,
        targetValue = 1.0035f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = floatDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(phaseIndex * 430, StartOffsetType.FastForward)
        ),
        label = "depthScale_$phaseIndex"
    )

    graphicsLayer {
        translationY = floatOffsetDp * density
        translationX = swayOffsetDp * density
        rotationZ = swayRollDeg
        rotationX = pitchDeg
        scaleX = depthScale
        scaleY = depthScale
        cameraDistance = 14f * density
    }
}

@Composable
fun AsisElevatedCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    cornerRadiusDp: Int = 22,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadiusDp.dp)
    val animatedContainerColor by animateColorAsState(
        targetValue = containerColor,
        animationSpec = AsisMotion.smoothSpring(),
        label = "cardContainerColor"
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = borderColor,
        animationSpec = AsisMotion.smoothSpring(),
        label = "cardBorderColor"
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = AsisMotion.smoothSpring()),
        shape = shape,
        color = animatedContainerColor,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.10f),
                    animatedBorderColor.copy(alpha = 0.75f)
                )
            )
        ),
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.025f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content
            )
        }
    }
}

@Composable
fun SectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null
) {
    val animatedTint by animateColorAsState(
        targetValue = tint,
        animationSpec = AsisMotion.smoothSpring(),
        label = "sectionHeaderTint"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                animatedTint.copy(alpha = 0.22f),
                                animatedTint.copy(alpha = 0.08f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = animatedTint.copy(alpha = 0.30f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = animatedTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun LivePulseDot(
    isActive: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier
) {
    val dotColor by animateColorAsState(
        targetValue = if (isActive) activeColor else inactiveColor,
        animationSpec = AsisMotion.smoothSpring(),
        label = "pulseDotColor"
    )
    val scale = if (isActive) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val anim by transition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
        anim
    } else {
        val restScale by animateFloatAsState(
            targetValue = 1f,
            animationSpec = AsisMotion.snappySpring(),
            label = "restScale"
        )
        restScale
    }

    Box(
        modifier = modifier.size(14.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = 0.32f))
            )
        }
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
    }
}

@Composable
fun StatusPill(
    label: String,
    color: Color,
    icon: ImageVector? = null,
    isPulsing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = AsisMotion.smoothSpring(),
        label = "statusPillColor"
    )
    Surface(
        modifier = modifier.animateContentSize(animationSpec = AsisMotion.snappySpring()),
        shape = RoundedCornerShape(50),
        color = animatedColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, animatedColor.copy(alpha = 0.32f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isPulsing) {
                LivePulseDot(isActive = true, activeColor = animatedColor, inactiveColor = animatedColor)
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = animatedColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            AnimatedContent(
                targetState = label,
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically(AsisMotion.offsetSpring()) { it / 3 })
                        .togetherWith(fadeOut(tween(120)) + slideOutVertically { -it / 3 })
                },
                label = "statusPillText"
            ) { currentLabel ->
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = animatedColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AnimatedSpringCounter(
    count: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = count,
        transitionSpec = {
            if (targetState >= initialState) {
                (fadeIn(tween(200)) + slideInVertically(AsisMotion.offsetSpring()) { -it / 2 } +
                    scaleIn(initialScale = 0.86f, animationSpec = AsisMotion.snappySpring()))
                    .togetherWith(fadeOut(tween(140)) + slideOutVertically { it / 2 })
            } else {
                (fadeIn(tween(200)) + slideInVertically(AsisMotion.offsetSpring()) { it / 2 } +
                    scaleIn(initialScale = 0.86f, animationSpec = AsisMotion.snappySpring()))
                    .togetherWith(fadeOut(tween(140)) + slideOutVertically { -it / 2 })
            }
        },
        modifier = modifier,
        label = "springCounter"
    ) { value ->
        Text(
            text = value.toString(),
            style = style,
            color = color
        )
    }
}

/**
 * Unified spring-based modal container providing smooth scale + slide + fade entry & exit animations.
 */
@Composable
fun AsisSpringModal(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    cornerRadiusDp: Int = 24,
    widthFraction: Float = 0.92f,
    content: @Composable ColumnScope.(triggerClose: (() -> Unit) -> Unit) -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    var pendingCloseAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(Unit) {
        visible = true
    }

    LaunchedEffect(pendingCloseAction) {
        val action = pendingCloseAction ?: return@LaunchedEffect
        visible = false
        delay(160)
        action()
    }

    val triggerClose: (() -> Unit) -> Unit = { afterExit ->
        if (pendingCloseAction == null) {
            pendingCloseAction = afterExit
        }
    }

    Dialog(
        onDismissRequest = { triggerClose(onDismissRequest) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(210)) +
                scaleIn(
                    initialScale = 0.90f,
                    animationSpec = AsisMotion.bouncySpring()
                ) +
                slideInVertically(
                    animationSpec = AsisMotion.offsetSpring()
                ) { it / 10 },
            exit = fadeOut(tween(160)) +
                scaleOut(
                    targetScale = 0.90f,
                    animationSpec = tween(160)
                ) +
                slideOutVertically(animationSpec = tween(160)) { it / 10 }
        ) {
            Surface(
                shape = RoundedCornerShape(cornerRadiusDp.dp),
                color = containerColor,
                border = BorderStroke(1.dp, borderColor),
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                modifier = modifier
                    .fillMaxWidth(widthFraction)
                    .animateContentSize(animationSpec = AsisMotion.smoothSpring())
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    content = { content(triggerClose) }
                )
            }
        }
    }
}

