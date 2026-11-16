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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.GatewaySettings
import com.example.ui.rememberFormattedTime

@Composable
fun StorageOptimizationCard(
    settings: GatewaySettings,
    totalLogsCount: Int,
    oldLogs15DaysCount: Int,
    onToggleAutoClearOldLogs: (Boolean) -> Unit,
    onClearOldLogsNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = settings.autoClearOldLogsEnabled
    val accentColor by animateColorAsState(
        targetValue = if (enabled) Color(0xFF10B981) else MaterialTheme.colorScheme.secondary,
        animationSpec = AsisMotion.smoothSpring(),
        label = "storageOptAccentColor"
    )

    AsisElevatedCard(
        modifier = modifier
            .animateContentSize(animationSpec = AsisMotion.smoothSpring())
            .testTag("auto_clear_logs_card"),
        borderColor = if (enabled) {
            accentColor.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .clickable { onToggleAutoClearOldLogs(!enabled) },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                SectionHeader(
                    icon = Icons.Default.AutoDelete,
                    title = "بهینه‌سازی حافظه و پاکسازی خودکار",
                    subtitle = if (enabled) {
                        "فعال: لاگ‌های قدیمی‌تر از ۱۵ روز خودکار پاک می‌شوند (فاکتورهای تأییدشده حفظ می‌گردند)"
                    } else {
                        "غیرفعال: نگهداری پیش‌فرض تا ۳۰ روز • برای سبک ماندن دیتابیس فعال کنید"
                    },
                    tint = accentColor
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onToggleAutoClearOldLogs,
                modifier = Modifier.testTag("auto_clear_old_logs_switch")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "$totalLogsCount رکورد",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "کل لاگ‌های ذخیره‌شده",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = if (oldLogs15DaysCount > 0) {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.42f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                border = BorderStroke(
                    1.dp,
                    if (oldLogs15DaysCount > 0) {
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = if (oldLogs15DaysCount > 0) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "$oldLogs15DaysCount مورد",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (oldLogs15DaysCount > 0) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.testTag("old_logs_15d_count_text")
                        )
                        Text(
                            text = "قدیمی‌تر از ۱۵ روز",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = settings.lastAutoLogCleanupAtMs != null,
            enter = fadeIn(tween(200)) +
                expandVertically(animationSpec = AsisMotion.smoothSpring()) +
                scaleIn(initialScale = 0.96f, animationSpec = AsisMotion.bouncySpring()),
            exit = fadeOut(tween(150)) +
                shrinkVertically(animationSpec = AsisMotion.smoothSpring()) +
                scaleOut(targetScale = 0.96f, animationSpec = tween(150))
        ) {
            val lastAt = settings.lastAutoLogCleanupAtMs ?: 0L
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("last_log_cleanup_badge")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "آخرین پاکسازی ۱۵ روزه (${rememberFormattedTime(lastAt)}): ${settings.lastAutoLogClearedCount} لاگ حذف شد",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        FilledTonalButton(
            onClick = onClearOldLogsNow,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("clear_old_logs_now_button")
        ) {
            Icon(
                imageVector = Icons.Default.DeleteSweep,
                contentDescription = "پاکسازی فوری لاگ‌های قدیمی‌تر از ۱۵ روز",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (oldLogs15DaysCount > 0) {
                    "پاکسازی $oldLogs15DaysCount لاگ قدیمی‌تر از ۱۵ روز"
                } else {
                    "بررسی و پاکسازی لاگ‌های قدیمی‌تر از ۱۵ روز"
                },
                fontWeight = FontWeight.Bold
            )
        }
    }
}
