package com.example.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.GatewaySettings
import com.example.data.SmsParser
import com.example.data.SmsRecord
import com.example.ui.components.AsisElevatedCard
import com.example.ui.components.LivePulseDot
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    settings: GatewaySettings,
    isOnline: Boolean,
    hasSmsPermissions: Boolean,
    hasNotificationPermission: Boolean,
    queueCount: Int,
    sentCount: Int,
    failedCount: Int,
    lastSentLog: SmsRecord?,
    onToggleService: (Boolean) -> Unit,
    onRequestPermissions: () -> Unit,
    onFlushQueue: () -> Unit,
    onSimulateSms: (sender: String, body: String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showSimulateDialog by rememberSaveable { mutableStateOf(false) }

    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager
    val isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    } else {
        true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ServiceHeroCard(
            settings = settings,
            isOnline = isOnline,
            onToggleService = onToggleService,
            onNavigateToSettings = onNavigateToSettings
        )

        AnimatedVisibility(
            visible = !settings.lastTokenError.isNullOrBlank(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            TokenErrorAlertBanner(
                errorMessage = settings.lastTokenError.orEmpty(),
                onFixToken = onNavigateToSettings
            )
        }

        AnimatedVisibility(
            visible = !hasSmsPermissions || !hasNotificationPermission,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            PermissionsRequiredCard(
                hasSmsPermissions = hasSmsPermissions,
                hasNotificationPermission = hasNotificationPermission,
                onRequestPermissions = onRequestPermissions
            )
        }

        // Live Queue & Delivery Metrics Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                title = "در صف تطبیق",
                value = queueCount.toString(),
                subtitle = "NEW / RETRY",
                icon = Icons.Default.HourglassTop,
                accentColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("stat_queue_card")
                    .clickable(onClick = onNavigateToLogs)
            )
            StatCard(
                title = "تأییدشده",
                value = sentCount.toString(),
                subtitle = "MATCHED",
                icon = Icons.Default.CheckCircle,
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("stat_sent_card")
                    .clickable(onClick = onNavigateToLogs)
            )
            StatCard(
                title = "بررسی دستی",
                value = failedCount.toString(),
                subtitle = "MANUAL / مبهم",
                icon = Icons.Default.WarningAmber,
                accentColor = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .weight(1f)
                    .testTag("stat_failed_card")
                    .clickable(onClick = onNavigateToLogs)
            )
        }

        // Quick Actions Panel
        AsisElevatedCard {
            SectionHeader(
                icon = Icons.Default.AutoAwesome,
                title = "عملیات سریع درگاه",
                subtitle = "اجرای فوری بررسی سفارش‌های منتظر یا آزمایش محلی پارسر"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onFlushQueue,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("flush_queue_button"),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "تطبیق فوری با سرور",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "تطبیق فوری",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FilledTonalButton(
                    onClick = { showSimulateDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("simulate_sms_button"),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sms,
                        contentDescription = "شبیه‌سازی پیامک بانکی",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "پیامک تستی",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Last Matched Summary Card
        LastMatchedSummaryCard(
            lastMatchedLog = lastSentLog,
            onViewAllLogs = onNavigateToLogs
        )

        // Battery Optimization Card for background reliability
        if (!isIgnoringBatteryOptimizations) {
            AsisElevatedCard(
                modifier = Modifier.testTag("battery_optimization_card"),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                borderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
            ) {
                SectionHeader(
                    icon = Icons.Default.BatteryChargingFull,
                    title = "عدم محدودیت باتری پس‌زمینه",
                    subtitle = "توصیه‌شده برای Xiaomi / Samsung / Huawei",
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "برای اطمینان از اینکه در حالت صفحه خاموش، پیامک‌های واریز بلافاصله پارس و با سرور تطبیق داده شوند، محدودیت باتری را برای برنامه غیرفعال کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        runCatching {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }.onFailure {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("ignore_battery_opt_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("غیرفعال کردن بهینه‌سازی باتری")
                }
            }
        }

        // Security & Privacy Footer Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "امنیت",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.permission_privacy_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }

    if (showSimulateDialog) {
        SimulateBankSmsDialog(
            onDismiss = { showSimulateDialog = false },
            onSendSimulated = { sender, body ->
                showSimulateDialog = false
                onSimulateSms(sender, body)
            }
        )
    }
}

@Composable
private fun ServiceHeroCard(
    settings: GatewaySettings,
    isOnline: Boolean,
    onToggleService: (Boolean) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val serviceEnabled = settings.serviceEnabled
    val isConfigured = settings.isConfigured
    val isFullyActive = serviceEnabled && isConfigured

    val gradientColors = when {
        isFullyActive -> listOf(Color(0xFF064E3B), Color(0xFF0B253C), Color(0xFF091526))
        !isConfigured -> listOf(Color(0xFF422006), Color(0xFF1A253A), Color(0xFF0B1424))
        else -> listOf(Color(0xFF1E293B), Color(0xFF111C2D), Color(0xFF09111E))
    }

    val glowColor = when {
        isFullyActive -> Color(0xFF10B981)
        !isConfigured -> Color(0xFFF59E0B)
        else -> Color(0xFF64748B)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(gradientColors))
            .border(
                width = 1.dp,
                color = glowColor.copy(alpha = if (isFullyActive) 0.45f else 0.25f),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        // Subtle ambient radial glow & geometric circles in background
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.15f),
                    radius = size.width * 0.65f
                ),
                center = Offset(size.width * 0.15f, size.height * 0.15f),
                radius = size.width * 0.65f
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.04f),
                radius = size.height * 0.55f,
                center = Offset(size.width * 0.92f, size.height * 0.9f)
            )
        }

        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                        modifier = Modifier.size(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_asis_shield),
                                contentDescription = "لوگوی ASiS",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LivePulseDot(
                                isActive = isFullyActive,
                                activeColor = Color(0xFF34D399),
                                inactiveColor = glowColor
                            )
                            Text(
                                text = when {
                                    isFullyActive -> "درگاه آماده و فعال"
                                    !isConfigured -> "نیازمند تنظیم سرور"
                                    else -> "سرویس متوقف است"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = when {
                                !isConfigured -> "آدرس HTTPS پنل و توکن را در تنظیمات ثبت کنید"
                                settings.autoApproveInApp -> "تطبیق و تأیید خودکار (امضای HMAC-SHA256)"
                                else -> "تطبیق خودکار + تأیید تک‌کلیکی دستی"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.80f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Switch(
                    checked = serviceEnabled,
                    onCheckedChange = onToggleService,
                    modifier = Modifier.testTag("service_toggle_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF10B981),
                        uncheckedThumbColor = Color(0xFFCBD5E1),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = "وضعیت شبکه",
                            tint = if (isOnline) Color(0xFF34D399) else Color(0xFFFBBF24),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isOnline) {
                                "آنلاین • اختلاف ساعت: ${settings.lastClockOffsetMs / 1000}s"
                            } else {
                                "آفلاین (نگهداری امن در صف)"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }

                if (!isConfigured) {
                    FilledTonalButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("configure_gateway_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFFF59E0B),
                            contentColor = Color(0xFF1E1B18)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "تنظیمات",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("پیکربندی", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF10B981).copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (settings.effectiveMaxToman > 0) {
                                    "سقف: ${SmsParser.formatRial(settings.effectiveMaxToman)} ت"
                                } else {
                                    "HMAC فعال"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFA7F3D0)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenErrorAlertBanner(
    errorMessage: String,
    onFixToken: () -> Unit
) {
    AsisElevatedCard(
        modifier = Modifier.testTag("token_error_banner"),
        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
        borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "خطای توکن",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "خطای ۴۰۱: توکن یا امضای درگاه نامعتبر است",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            FilledTonalButton(
                onClick = onFixToken,
                modifier = Modifier.testTag("fix_token_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("اصلاح توکن")
            }
        }
    }
}

@Composable
private fun PermissionsRequiredCard(
    hasSmsPermissions: Boolean,
    hasNotificationPermission: Boolean,
    onRequestPermissions: () -> Unit
) {
    AsisElevatedCard(
        modifier = Modifier.testTag("permissions_card"),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
        borderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
    ) {
        SectionHeader(
            icon = Icons.Default.WarningAmber,
            title = "دسترسی‌های موردنیاز اندروید",
            subtitle = "برای دریافت و تطبیق خودکار پیامک‌های بانکی",
            tint = MaterialTheme.colorScheme.tertiary
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!hasSmsPermissions) {
                Text(
                    text = "• مجوز دریافت و خواندن پیامک (RECEIVE_SMS / READ_SMS) برای تشخیص واریزهای بانکی الزامی است.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (!hasNotificationPermission) {
                Text(
                    text = "• مجوز اعلان (POST_NOTIFICATIONS) برای هشدار تأیید دستی، موارد مبهم و خطای ۴۰۱ لازم است.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Button(
            onClick = onRequestPermissions,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("grant_permissions_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )
        ) {
            Text("اعطای دسترسی‌های برنامه", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LastMatchedSummaryCard(
    lastMatchedLog: SmsRecord?,
    onViewAllLogs: () -> Unit
) {
    AsisElevatedCard(modifier = Modifier.testTag("last_sent_card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "آخرین تأیید",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "آخرین واریز تأییدشده",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
            TextButton(
                onClick = onViewAllLogs,
                modifier = Modifier.testTag("view_all_logs_button")
            ) {
                Text("همه لاگ‌ها")
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "مشاهده همه لاگ‌ها",
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (lastMatchedLog == null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "هنوز واریزی با سفارش تطبیق و تأیید نشده است. پس از دریافت پیامک بانکی واقعی، خلاصهٔ رسید تأییدشده اینجا نمایش داده می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val timeStr = rememberFormattedTime(lastMatchedLog.decidedMs ?: lastMatchedLog.receivedMs)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            label = lastMatchedLog.matchedLabelFa() ?: "تأییدشده",
                            color = MaterialTheme.colorScheme.primary,
                            icon = Icons.Default.CheckCircle
                        )
                        Text(
                            text = timeStr,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (lastMatchedLog.amountMinor != null) {
                        Text(
                            text = "${SmsParser.formatToman(lastMatchedLog.amountMinor)} تومان",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${lastMatchedLog.bankTitle} • ${SmsParser.formatRial(lastMatchedLog.amountMinor)} ریال",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = lastMatchedLog.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun rememberFormattedTime(timestampMs: Long): String {
    return remember(timestampMs) {
        val sdf = SimpleDateFormat("yyyy/MM/dd - HH:mm:ss", Locale.US)
        sdf.format(Date(timestampMs))
    }
}
