package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.network.ConnectivityHealthTier
import com.example.network.DailyRetryHealthPoint
import com.example.network.RetryHealthAnalytics
import com.example.network.RetryHealthSummary
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun AutoRetryHealthChartCard(
    summary: RetryHealthSummary,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val defaultSelectedIdx = remember(summary.days) {
        summary.days.indexOfLast { it.totalAttempts > 0 }.takeIf { it >= 0 } ?: 6
    }
    var selectedDayIndex by rememberSaveable(summary.totalAttempts7d) {
        mutableIntStateOf(defaultSelectedIdx)
    }

    var revealTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealTriggered = true }
    val chartProgress by animateFloatAsState(
        targetValue = if (revealTriggered) 1f else 0f,
        animationSpec = AsisMotion.smoothSpring(),
        label = "retryChartEntryReveal"
    )

    val healthColor by animateColorAsState(
        targetValue = when (summary.healthTier) {
            ConnectivityHealthTier.NO_DATA -> MaterialTheme.colorScheme.primary
            ConnectivityHealthTier.EXCELLENT -> Color(0xFF10B981)
            ConnectivityHealthTier.GOOD -> Color(0xFF3B82F6)
            ConnectivityHealthTier.DEGRADED -> Color(0xFFF59E0B)
            ConnectivityHealthTier.CRITICAL -> Color(0xFFEF4444)
        },
        animationSpec = AsisMotion.smoothSpring(),
        label = "retryChartHealthColor"
    )

    val overallPercentText = summary.overallSuccessRatePercent?.let { "${it.roundToInt()}%" } ?: "—"
    val selectedDay = summary.days.getOrNull(selectedDayIndex) ?: summary.days.lastOrNull()

    AsisElevatedCard(
        modifier = modifier
            .animateContentSize(animationSpec = AsisMotion.smoothSpring())
            .testTag("retry_health_chart_card"),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        borderColor = healthColor.copy(alpha = 0.34f),
        cornerRadiusDp = 20
    ) {
        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(healthColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = "نمودار سلامت اتصال ۷ روزه",
                        tint = healthColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "نمودار نرخ موفقیت تلاش مجدد (۷ روز اخیر)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = summary.healthTier.labelFa,
                        style = MaterialTheme.typography.labelMedium,
                        color = healthColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatusPill(
                    label = overallPercentText,
                    color = healthColor
                )
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.testTag("toggle_retry_chart_expand")
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "بستن نمودار" else "نمایش نمودار"
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(220)) +
                expandVertically(animationSpec = AsisMotion.smoothSpring()) +
                scaleIn(initialScale = 0.97f, animationSpec = AsisMotion.bouncySpring()),
            exit = fadeOut(tween(150)) +
                shrinkVertically(animationSpec = AsisMotion.smoothSpring()) +
                scaleOut(targetScale = 0.97f, animationSpec = tween(150))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Connectivity Health Insight Strip + Trend Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = healthColor.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, healthColor.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = RetryHealthAnalytics.insightMessageFa(summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        summary.trendDeltaPercent?.let { delta ->
                            val improving = delta >= 0f
                            val trendColor = if (improving) Color(0xFF10B981) else Color(0xFFF59E0B)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = if (improving) {
                                        Icons.AutoMirrored.Filled.TrendingUp
                                    } else {
                                        Icons.AutoMirrored.Filled.TrendingDown
                                    },
                                    contentDescription = null,
                                    tint = trendColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${if (improving) "+" else "-"}${abs(delta).roundToInt()}%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    fontWeight = FontWeight.Bold,
                                    color = trendColor
                                )
                            }
                        }
                    }
                }

                // 7-Day Native Compose Canvas Chart (LTR chronological axis oldest -> today)
                val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                val emptyBarColor = MaterialTheme.colorScheme.surfaceContainerHighest
                val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                val trendLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                val nodeOutlineColor = MaterialTheme.colorScheme.surfaceContainerLow

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left Y-axis labels (100%, 50%, 0%)
                                Column(
                                    modifier = Modifier
                                        .width(34.dp)
                                        .height(140.dp),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = "100%",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = labelColor
                                    )
                                    Text(
                                        text = "50%",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = labelColor
                                    )
                                    Text(
                                        text = "0%",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = labelColor
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Canvas(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(140.dp)
                                        .pointerInput(summary.days) {
                                            detectTapGestures { offset ->
                                                val count = summary.days.size.coerceAtLeast(1)
                                                val slotW = size.width / count.toFloat()
                                                val tappedIdx = (offset.x / slotW).toInt().coerceIn(0, count - 1)
                                                selectedDayIndex = tappedIdx
                                            }
                                        }
                                        .testTag("retry_health_canvas")
                                ) {
                                    val w = size.width
                                    val h = size.height
                                    val topPad = 10.dp.toPx()
                                    val bottomPad = 6.dp.toPx()
                                    val plotH = (h - topPad - bottomPad).coerceAtLeast(1f)

                                    // Dashed horizontal grid lines at 0%, 50%, 100%
                                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                                    for (fraction in listOf(0f, 0.5f, 1f)) {
                                        val y = topPad + plotH * (1f - fraction)
                                        drawLine(
                                            color = gridColor,
                                            start = Offset(0f, y),
                                            end = Offset(w, y),
                                            strokeWidth = 1.dp.toPx(),
                                            pathEffect = dashEffect
                                        )
                                    }

                                    val days = summary.days
                                    val count = days.size.coerceAtLeast(1)
                                    val slotW = w / count.toFloat()
                                    val barW = (slotW * 0.44f).coerceIn(10.dp.toPx(), 26.dp.toPx())
                                    val linePoints = mutableListOf<Pair<Offset, Color>>()

                                    days.forEachIndexed { idx, day ->
                                        val centerX = slotW * (idx + 0.5f)
                                        val rate = day.successRatePercent
                                        val isSelected = idx == selectedDayIndex

                                        if (isSelected) {
                                            drawRoundRect(
                                                color = healthColor.copy(alpha = 0.09f),
                                                topLeft = Offset(centerX - slotW * 0.42f, 0f),
                                                size = Size(slotW * 0.84f, h),
                                                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                                            )
                                        }

                                        if (rate == null) {
                                            val stubH = 8.dp.toPx()
                                            drawRoundRect(
                                                color = emptyBarColor,
                                                topLeft = Offset(centerX - barW / 2f, topPad + plotH - stubH),
                                                size = Size(barW, stubH),
                                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                            )
                                        } else {
                                            val normalizedRate = (rate / 100f).coerceIn(0.05f, 1f) * chartProgress
                                            val barH = plotH * normalizedRate
                                            val barTop = topPad + plotH - barH
                                            val dayColor = when {
                                                rate >= 80f -> Color(0xFF10B981)
                                                rate >= 50f -> Color(0xFFF59E0B)
                                                else -> Color(0xFFEF4444)
                                            }
                                            drawRoundRect(
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        dayColor,
                                                        dayColor.copy(alpha = 0.42f)
                                                    ),
                                                    startY = barTop,
                                                    endY = topPad + plotH
                                                ),
                                                topLeft = Offset(centerX - barW / 2f, barTop),
                                                size = Size(barW, barH),
                                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                            )
                                            linePoints += Offset(centerX, barTop) to dayColor
                                        }
                                    }

                                    // Overlay smooth connecting trend line + glowing points across active days
                                    if (linePoints.size >= 2) {
                                        val path = Path().apply {
                                            moveTo(linePoints.first().first.x, linePoints.first().first.y)
                                            for (i in 1 until linePoints.size) {
                                                val prev = linePoints[i - 1].first
                                                val curr = linePoints[i].first
                                                val ctrlX = (prev.x + curr.x) / 2f
                                                cubicTo(ctrlX, prev.y, ctrlX, curr.y, curr.x, curr.y)
                                            }
                                        }
                                        drawPath(
                                            path = path,
                                            color = trendLineColor,
                                            style = Stroke(
                                                width = 2.dp.toPx(),
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round
                                            )
                                        )
                                    }

                                    for ((pt, ptColor) in linePoints) {
                                        drawCircle(
                                            color = ptColor.copy(alpha = 0.30f),
                                            radius = 7.dp.toPx(),
                                            center = pt
                                        )
                                        drawCircle(
                                            color = nodeOutlineColor,
                                            radius = 4.dp.toPx(),
                                            center = pt
                                        )
                                        drawCircle(
                                            color = ptColor,
                                            radius = 2.5.dp.toPx(),
                                            center = pt
                                        )
                                    }
                                }
                            }

                            // X-Axis day labels aligned under each of the 7 slots
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 40.dp)
                            ) {
                                summary.days.forEachIndexed { idx, day ->
                                    val isSelected = idx == selectedDayIndex
                                    Text(
                                        text = day.shortAxisLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) healthColor else labelColor,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedDayIndex = idx }
                                            .padding(vertical = 10.dp)
                                            .testTag("retry_chart_day_chip_$idx")
                                    )
                                }
                            }
                        }
                    }
                }

                // Interactive Selected Day Detail Strip
                if (selectedDay != null) {
                    AnimatedContent(
                        targetState = selectedDay,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "selectedDayBreakdown"
                    ) { dayPoint ->
                        SelectedDayDetailStrip(day = dayPoint, accentColor = healthColor)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                // 3 KPI Summary Pills for the 7-day window
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RetryKpiChip(
                        label = "کل تلاش‌های ۷ روز",
                        value = summary.totalAttempts7d.toString(),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    RetryKpiChip(
                        label = "موفقیت خودکار",
                        value = summary.succeededAttempts7d.toString(),
                        color = Color(0xFF10B981),
                        modifier = Modifier.weight(1f)
                    )
                    RetryKpiChip(
                        label = "ناموفق / خطا",
                        value = summary.failedAttempts7d.toString(),
                        color = Color(0xFFEF4444),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedDayDetailStrip(
    day: DailyRetryHealthPoint,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("retry_chart_selected_day_detail")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Text(
                    text = day.dayLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = if (day.totalAttempts == 0) {
                    "بدون تلاش مجدد در این روز"
                } else {
                    "${day.succeededAttempts} موفق • ${day.failedAttempts} ناموفق (${day.successRatePercent?.roundToInt() ?: 0}%)"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun RetryKpiChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.24f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
