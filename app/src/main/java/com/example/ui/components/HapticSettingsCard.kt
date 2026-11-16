package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.FailedSyncHapticPattern
import com.example.data.GatewaySettings
import com.example.data.HandshakeHapticPattern
import com.example.data.HapticIntensity
import com.example.data.HapticWaveformSpec

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HapticSettingsCard(
    settings: GatewaySettings,
    onToggleMaster: (Boolean) -> Unit,
    onToggleHandshake: (Boolean) -> Unit,
    onSelectHandshakePattern: (HandshakeHapticPattern) -> Unit,
    onSelectHandshakeIntensity: (HapticIntensity) -> Unit,
    onTestHandshake: () -> Unit,
    onToggleFailedSync: (Boolean) -> Unit,
    onSelectFailedSyncPattern: (FailedSyncHapticPattern) -> Unit,
    onSelectFailedSyncIntensity: (HapticIntensity) -> Unit,
    onTestFailedSync: () -> Unit,
    onToggleCooldown: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val greenAccent = Color(0xFF10B981)
    val errorAccent = MaterialTheme.colorScheme.error

    val activeEventsCount = listOf(
        settings.hapticOnServerHandshake,
        settings.hapticOnFailedSync
    ).count { it }

    val statusColor by animateColorAsState(
        targetValue = when {
            !settings.hapticMasterEnabled || activeEventsCount == 0 -> MaterialTheme.colorScheme.outline
            activeEventsCount == 2 -> MaterialTheme.colorScheme.primary
            else -> Color(0xFF06B6D4)
        },
        animationSpec = AsisMotion.smoothSpring(),
        label = "hapticCardStatusColor"
    )

    val statusLabel = when {
        !settings.hapticMasterEnabled -> "خاموش"
        activeEventsCount == 0 -> "بدون رویداد"
        activeEventsCount == 2 -> "هر دو رویداد فعال"
        else -> "۱ رویداد فعال"
    }

    AsisElevatedCard(
        modifier = modifier
            .animateContentSize(animationSpec = AsisMotion.smoothSpring())
            .testTag("haptic_settings_card"),
        borderColor = if (settings.hapticMasterEnabled) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                SectionHeader(
                    icon = Icons.Default.Vibration,
                    title = "بازخورد لمسی و لرزش (Haptic Feedback)",
                    subtitle = if (settings.hapticMasterEnabled) {
                        "شخصی‌سازی الگو و شدت لرزش برای هندشیک موفق و خطاهای همگام‌سازی"
                    } else {
                        "تمام لرزش‌های بازخورد لمسی برنامه غیرفعال هستند"
                    },
                    tint = statusColor,
                    trailing = {
                        StatusPill(
                            label = statusLabel,
                            color = statusColor,
                            isPulsing = settings.hapticMasterEnabled && activeEventsCount > 0
                        )
                    }
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = settings.hapticMasterEnabled,
                onCheckedChange = onToggleMaster,
                modifier = Modifier.testTag("haptic_master_switch")
            )
        }

        AnimatedVisibility(
            visible = settings.hapticMasterEnabled,
            enter = fadeIn(tween(220)) +
                expandVertically(animationSpec = AsisMotion.smoothSpring()) +
                scaleIn(initialScale = 0.96f, animationSpec = AsisMotion.bouncySpring()),
            exit = fadeOut(tween(150)) +
                shrinkVertically(animationSpec = AsisMotion.smoothSpring()) +
                scaleOut(targetScale = 0.96f, animationSpec = tween(150))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Successful Server Handshakes Section
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        if (settings.hapticOnServerHandshake) {
                            greenAccent.copy(alpha = 0.36f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(animationSpec = AsisMotion.smoothSpring())
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable { onToggleHandshake(!settings.hapticOnServerHandshake) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = greenAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "لرزش هندشیک موفق سرور",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "هنگام برقراری اتصال ایمن، تست موفق یا بازیابی ارتباط",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = settings.hapticOnServerHandshake,
                                onCheckedChange = onToggleHandshake,
                                modifier = Modifier.testTag("haptic_handshake_switch")
                            )
                        }

                        AnimatedVisibility(
                            visible = settings.hapticOnServerHandshake,
                            enter = fadeIn(tween(200)) + expandVertically(animationSpec = AsisMotion.smoothSpring()),
                            exit = fadeOut(tween(140)) + shrinkVertically(animationSpec = AsisMotion.smoothSpring())
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                )
                                HapticWaveformPreviewRow(
                                    label = "الگوی لرزش: ${settings.hapticHandshakePattern.titleFa}",
                                    spec = HapticWaveformSpec.forHandshake(
                                        settings.hapticHandshakePattern,
                                        settings.hapticHandshakeIntensity
                                    ),
                                    accentColor = greenAccent
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    HandshakeHapticPattern.entries.forEach { preset ->
                                        val selected = settings.hapticHandshakePattern == preset
                                        FilterChip(
                                            selected = selected,
                                            onClick = { onSelectHandshakePattern(preset) },
                                            label = { Text(preset.titleFa) },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = greenAccent.copy(alpha = 0.2f),
                                                selectedLabelColor = greenAccent
                                            ),
                                            modifier = Modifier
                                                .defaultMinSize(minHeight = 48.dp)
                                                .testTag("handshake_pattern_${preset.storageKey}")
                                        )
                                    }
                                }

                                Text(
                                    text = settings.hapticHandshakePattern.subtitleFa,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                IntensitySelectorRow(
                                    selected = settings.hapticHandshakeIntensity,
                                    accentColor = greenAccent,
                                    testTagPrefix = "handshake_intensity",
                                    onSelect = onSelectHandshakeIntensity
                                )

                                OutlinedButton(
                                    onClick = onTestHandshake,
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("test_handshake_haptic_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = "تست لرزش هندشیک موفق",
                                        tint = greenAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "تست لرزش هندشیک موفق",
                                        color = greenAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Failed Sync Attempts Section
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        if (settings.hapticOnFailedSync) {
                            errorAccent.copy(alpha = 0.36f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(animationSpec = AsisMotion.smoothSpring())
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable { onToggleFailedSync(!settings.hapticOnFailedSync) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = errorAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "لرزش خطای همگام‌سازی",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "هنگام قطع اتصال، تایم‌اوت سرور یا ناموفق بودن ارسال دسته‌ای",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = settings.hapticOnFailedSync,
                                onCheckedChange = onToggleFailedSync,
                                modifier = Modifier.testTag("haptic_failed_sync_switch")
                            )
                        }

                        AnimatedVisibility(
                            visible = settings.hapticOnFailedSync,
                            enter = fadeIn(tween(200)) + expandVertically(animationSpec = AsisMotion.smoothSpring()),
                            exit = fadeOut(tween(140)) + shrinkVertically(animationSpec = AsisMotion.smoothSpring())
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                )
                                HapticWaveformPreviewRow(
                                    label = "الگوی خطا: ${settings.hapticFailedSyncPattern.titleFa}",
                                    spec = HapticWaveformSpec.forFailedSync(
                                        settings.hapticFailedSyncPattern,
                                        settings.hapticFailedSyncIntensity
                                    ),
                                    accentColor = errorAccent
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FailedSyncHapticPattern.entries.forEach { preset ->
                                        val selected = settings.hapticFailedSyncPattern == preset
                                        FilterChip(
                                            selected = selected,
                                            onClick = { onSelectFailedSyncPattern(preset) },
                                            label = { Text(preset.titleFa) },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = errorAccent.copy(alpha = 0.2f),
                                                selectedLabelColor = errorAccent
                                            ),
                                            modifier = Modifier
                                                .defaultMinSize(minHeight = 48.dp)
                                                .testTag("failed_pattern_${preset.storageKey}")
                                        )
                                    }
                                }

                                Text(
                                    text = settings.hapticFailedSyncPattern.subtitleFa,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                IntensitySelectorRow(
                                    selected = settings.hapticFailedSyncIntensity,
                                    accentColor = errorAccent,
                                    testTagPrefix = "failed_intensity",
                                    onSelect = onSelectFailedSyncIntensity
                                )

                                OutlinedButton(
                                    onClick = onTestFailedSync,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, errorAccent.copy(alpha = 0.45f)),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("test_failed_sync_haptic_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = "تست لرزش خطای همگام‌سازی",
                                        tint = errorAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "تست لرزش خطای همگام‌سازی",
                                        color = errorAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Anti-Spam Cooldown Toggle
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clickable { onToggleCooldown(!settings.hapticCooldownEnabled) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "فاصلهٔ اطمینان ۳ ثانیه‌ای در همگام‌سازی پس‌زمینه",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "جلوگیری از لرزش‌های پیاپی هنگام تلاش مجدد دسته‌ای یا نوسان مکرر شبکه",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = settings.hapticCooldownEnabled,
                            onCheckedChange = onToggleCooldown,
                            modifier = Modifier.testTag("haptic_cooldown_switch")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntensitySelectorRow(
    selected: HapticIntensity,
    accentColor: Color,
    testTagPrefix: String,
    onSelect: (HapticIntensity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "شدت لرزش: ${selected.titleFa}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HapticIntensity.entries.forEach { level ->
                val isSelected = selected == level
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) {
                        accentColor.copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(level) }
                        .testTag("${testTagPrefix}_${level.storageKey}")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = level.titleFa,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HapticWaveformPreviewRow(
    label: String,
    spec: HapticWaveformSpec,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            spec.timingsMs.forEachIndexed { idx, durationMs ->
                if (idx % 2 == 1 && durationMs > 0L) {
                    val amp = spec.amplitudes.getOrElse(idx) { 180 }
                    val widthDp = ((durationMs / 4.5f).coerceIn(8f, 34f)).dp
                    val heightDp = ((amp / 14f).coerceIn(8f, 18f)).dp
                    Box(
                        modifier = Modifier
                            .width(widthDp)
                            .height(heightDp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(accentColor.copy(alpha = (amp / 255f).coerceIn(0.4f, 1f)))
                    )
                }
            }
        }
    }
}
