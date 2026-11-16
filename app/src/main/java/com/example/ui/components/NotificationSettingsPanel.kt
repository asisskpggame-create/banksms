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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.GatewaySettings
import com.example.data.SmsParser

private val SENSITIVE_TOMAN_PRESETS = listOf(
    0L to "همه واریزها",
    1_000_000L to "۱ م تومان",
    5_000_000L to "۵ م تومان",
    10_000_000L to "۱۰ م تومان",
    50_000_000L to "۵۰ م تومان"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationSettingsPanelCard(
    settings: GatewaySettings,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onToggleNotifyServerError: (Boolean) -> Unit,
    onToggleNotifySensitiveSms: (Boolean) -> Unit,
    onToggleNotifyAmbiguousManual: (Boolean) -> Unit,
    onToggleNotifyApprovedInvoice: (Boolean) -> Unit = {},
    onUpdateSensitiveSmsMinToman: (Long) -> Unit,
    onSendTestNotification: () -> Unit,
    modifier: Modifier = Modifier
) {
    var thresholdInput by rememberSaveable {
        mutableStateOf(
            if (settings.sensitiveSmsMinToman > 0L) settings.sensitiveSmsMinToman.toString() else "0"
        )
    }

    LaunchedEffect(settings.sensitiveSmsMinToman) {
        val desired = settings.sensitiveSmsMinToman.toString()
        if (thresholdInput != desired) {
            thresholdInput = desired
        }
    }

    val activeAlertsCount = listOf(
        settings.notifyOnServerConnectionError,
        settings.notifyOnSensitiveBankSms,
        settings.notifyOnAmbiguousOrManual,
        settings.notifyOnApprovedInvoice
    ).count { it }

    val statusColor by animateColorAsState(
        targetValue = when {
            !hasNotificationPermission -> MaterialTheme.colorScheme.error
            activeAlertsCount == 4 -> Color(0xFF10B981)
            activeAlertsCount > 0 -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = AsisMotion.smoothSpring(),
        label = "notifPanelStatusColor"
    )

    val statusLabel = when {
        !hasNotificationPermission -> "نیاز به مجوز اعلان"
        activeAlertsCount == 0 -> "خاموش"
        activeAlertsCount == 4 -> "تمام هشدارها فعال"
        else -> "$activeAlertsCount مورد فعال"
    }

    AsisElevatedCard(
        modifier = modifier
            .animateContentSize(animationSpec = AsisMotion.smoothSpring())
            .testTag("notification_settings_panel")
    ) {
        SectionHeader(
            icon = if (hasNotificationPermission && activeAlertsCount > 0) {
                Icons.Default.NotificationsActive
            } else {
                Icons.Default.NotificationsOff
            },
            title = "پنل تنظیمات اعلان‌های لحظه‌ای",
            subtitle = "دریافت Push Notification هنگام خطای سرور یا پیامک‌های بانکی حساس",
            trailing = {
                StatusPill(
                    label = statusLabel,
                    color = statusColor,
                    isPulsing = hasNotificationPermission && activeAlertsCount > 0
                )
            }
        )

        AnimatedVisibility(
            visible = !hasNotificationPermission,
            enter = fadeIn(tween(220)) +
                expandVertically(animationSpec = AsisMotion.smoothSpring()) +
                scaleIn(initialScale = 0.96f, animationSpec = AsisMotion.bouncySpring()),
            exit = fadeOut(tween(150)) +
                shrinkVertically(animationSpec = AsisMotion.smoothSpring()) +
                scaleOut(targetScale = 0.96f, animationSpec = tween(150))
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "هشدار مجوز اعلان",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "مجوز ارسال اعلان سیستم (POST_NOTIFICATIONS) غیرفعال است.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Button(
                        onClick = onRequestNotificationPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("grant_notification_permission_button")
                    ) {
                        Text("فعال‌سازی مجوز اعلان‌های اندروید", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 1. Server Connection Error Push Alerts
        NotificationToggleRow(
            icon = Icons.Default.CloudOff,
            iconTint = Color(0xFFEF4444),
            title = "اعلان خطای اتصال به سرور",
            subtitle = "قطع اینترنت، خطای ۵xx یا تایم‌اوت سرور، و خطای احراز هویت توکن (۴۰۱)",
            checked = settings.notifyOnServerConnectionError,
            onCheckedChange = onToggleNotifyServerError,
            switchTestTag = "notify_server_error_switch"
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        // 2. Sensitive Bank SMS Push Alerts
        NotificationToggleRow(
            icon = Icons.Default.Security,
            iconTint = Color(0xFFF59E0B),
            title = "اعلان پیامک‌های بانکی حساس",
            subtitle = "واریزهای کلان بالای آستانه، برداشت/خرید از حساب و پیامک‌های امنیتی یا رمز پویا",
            checked = settings.notifyOnSensitiveBankSms,
            onCheckedChange = onToggleNotifySensitiveSms,
            switchTestTag = "notify_sensitive_sms_switch"
        )

        // Configurable Toman Threshold for High-Value Deposits
        AnimatedVisibility(
            visible = settings.notifyOnSensitiveBankSms,
            enter = fadeIn(tween(220)) +
                expandVertically(animationSpec = AsisMotion.smoothSpring()) +
                scaleIn(initialScale = 0.96f, animationSpec = AsisMotion.bouncySpring()),
            exit = fadeOut(tween(150)) +
                shrinkVertically(animationSpec = AsisMotion.smoothSpring()) +
                scaleOut(targetScale = 0.96f, animationSpec = tween(150))
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val thresholdSummary = if (settings.sensitiveSmsMinToman <= 0L) {
                        "همه واریزهای بانکی"
                    } else {
                        "${SmsParser.formatRial(settings.sensitiveSmsMinToman)} تومان به بالا"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "آستانه مبلغ واریز کلان (تومان)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        StatusPill(
                            label = thresholdSummary,
                            color = Color(0xFFF59E0B)
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SENSITIVE_TOMAN_PRESETS.forEach { (presetToman, presetLabel) ->
                            val isSelected = settings.sensitiveSmsMinToman == presetToman
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    thresholdInput = presetToman.toString()
                                    onUpdateSensitiveSmsMinToman(presetToman)
                                },
                                label = {
                                    Text(
                                        text = presetLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("sensitive_threshold_chip_$presetToman")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = thresholdInput,
                            onValueChange = { raw ->
                                thresholdInput = raw.filter { ch -> ch.isDigit() }
                            },
                            label = { Text("آستانه دلخواه (تومان؛ ۰ = همه)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sensitive_sms_threshold_input")
                        )

                        FilledTonalButton(
                            onClick = {
                                val parsed = thresholdInput.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                                onUpdateSensitiveSmsMinToman(parsed)
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("apply_sensitive_threshold_button")
                        ) {
                            Text("ثبت", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        // 3. Ambiguous / Manual Deposit Push Alerts
        NotificationToggleRow(
            icon = Icons.AutoMirrored.Filled.Rule,
            iconTint = MaterialTheme.colorScheme.tertiary,
            title = "اعلان واریزهای مبهم و تأیید دستی",
            subtitle = "هشدار فوری هنگام تداخل مبلغ چند سفارش یا واریز ناشناس نیازمند بررسی",
            checked = settings.notifyOnAmbiguousOrManual,
            onCheckedChange = onToggleNotifyAmbiguousManual,
            switchTestTag = "notify_ambiguous_manual_switch"
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        // 4. Approved Invoice & Price Push Alerts
        NotificationToggleRow(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            iconTint = Color(0xFF10B981),
            title = "اعلان و اعلام قیمت فاکتورهای تأییدشده",
            subtitle = "نمایش اعلان لحظه‌ای با شناسه فاکتور و مبلغ دقیق (تومان و ریال) بلافاصله پس از تأیید",
            checked = settings.notifyOnApprovedInvoice,
            onCheckedChange = onToggleNotifyApprovedInvoice,
            switchTestTag = "notify_approved_invoice_switch"
        )

        // Instant Push Notification Test Button
        FilledTonalButton(
            onClick = {
                if (!hasNotificationPermission) {
                    onRequestNotificationPermission()
                } else {
                    onSendTestNotification()
                }
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("test_push_notification_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "ارسال اعلان آزمایشی",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (hasNotificationPermission) {
                    "ارسال اعلان لحظه‌ای آزمایشی"
                } else {
                    "درخواست مجوز و تست اعلان"
                },
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun NotificationToggleRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTestTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconTint.copy(alpha = 0.14f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(switchTestTag)
        )
    }
}
