package com.example.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Service Status Card
        ServiceHeroCard(
            settings = settings,
            isOnline = isOnline,
            onToggleService = onToggleService,
            onNavigateToSettings = onNavigateToSettings
        )

        // Alert Banner if 401 Token/HMAC Error occurred recently
        AnimatedVisibility(visible = !settings.lastTokenError.isNullOrBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("token_error_banner"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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
                            text = settings.lastTokenError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    FilledTonalButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("fix_token_button")
                    ) {
                        Text("اصلاح توکن")
                    }
                }
            }
        }

        // Permissions Card
        if (!hasSmsPermissions || !hasNotificationPermission) {
            PermissionsRequiredCard(
                hasSmsPermissions = hasSmsPermissions,
                hasNotificationPermission = hasNotificationPermission,
                onRequestPermissions = onRequestPermissions
            )
        }

        // Live Queue & Delivery Metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "واریز منتظر (NEW)",
                value = queueCount.toString(),
                subtitle = "در صف تطبیق",
                accentColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("stat_queue_card")
                    .clickable { onNavigateToLogs() }
            )
            StatCard(
                title = "تأییدشده (MATCHED)",
                value = sentCount.toString(),
                subtitle = "تطبیق و تأیید موفق",
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("stat_sent_card")
                    .clickable { onNavigateToLogs() }
            )
            StatCard(
                title = "نیازمند بررسی دستی",
                value = failedCount.toString(),
                subtitle = "MANUAL / مبهم",
                accentColor = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .weight(1f)
                    .testTag("stat_failed_card")
                    .clickable { onNavigateToLogs() }
            )
        }

        // Quick Action Buttons (Run Matcher Now + Simulate Bank SMS)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "عملیات سریع تطبیق و شبیه‌سازی (App-Side Approval)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onFlushQueue,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("flush_queue_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "تطبیق با سرور",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تطبیق فوری (Sweep)")
                    }
                    OutlinedButton(
                        onClick = { showSimulateDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("simulate_sms_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sms,
                            contentDescription = "شبیه‌سازی پیامک بانکی",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("پیامک تستی")
                    }
                }
            }
        }

        // Last Matched SMS Summary Card
        LastMatchedSummaryCard(
            lastMatchedLog = lastSentLog,
            onViewAllLogs = onNavigateToLogs
        )

        // Battery Optimization Card for Xiaomi/Samsung/Huawei (Section 8)
        if (!isIgnoringBatteryOptimizations) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("battery_optimization_card"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = "بهینه‌سازی باتری",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "عدم محدودیت باتری (مخصوص Xiaomi / Samsung / Huawei)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "برای اطمینان از اینکه در حالت صفحه خاموش، پیامک‌های واریز بلافاصله پارس و با سرور تطبیق داده شوند، محدودیت باتری را برای ASiS pannel غیرفعال کنید.",
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
                            .testTag("ignore_battery_opt_button")
                    ) {
                        Text("غیرفعال کردن بهینه‌سازی باتری (Unrestricted)")
                    }
                }
            }
        }

        // Security & Privacy Footer Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "امنیت",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.permission_privacy_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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

    val gradientColors = if (serviceEnabled && isConfigured) {
        listOf(Color(0xFF064E3B), Color(0xFF0F2942))
    } else if (!isConfigured) {
        listOf(Color(0xFF451A03), Color(0xFF17263C))
    } else {
        listOf(Color(0xFF1E293B), Color(0xFF0F172A))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(gradientColors))
            .border(
                width = 1.dp,
                color = if (serviceEnabled && isConfigured) {
                    Color(0xFF10B981).copy(alpha = 0.45f)
                } else {
                    Color.White.copy(alpha = 0.12f)
                },
                shape = RoundedCornerShape(22.dp)
            )
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_asis_shield),
                                contentDescription = "لوگوی ASiS",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = if (serviceEnabled) "تطبیق و تأیید سمت اپ فعال است" else "سرویس متوقف است",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                !isConfigured -> "نیازمند ثبت آدرس سرور و توکن در تنظیمات"
                                settings.autoApproveInApp -> "حالت: تأیید خودکار (POST /sms-approve با امضای HMAC)"
                                else -> "حالت: تطبیق خودکار + تأیید با یک دکمه (پیش‌فرض امن)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }

                Switch(
                    checked = serviceEnabled,
                    onCheckedChange = onToggleService,
                    modifier = Modifier.testTag("service_toggle_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF10B981)
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
                    color = Color.White.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = "وضعیت شبکه",
                            tint = if (isOnline) Color(0xFF34D399) else Color(0xFFFBBF24),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isOnline) {
                                "آنلاین • اختلاف ساعت سرور: ${settings.lastClockOffsetMs / 1000}s"
                            } else {
                                "آفلاین (واریزها در صف NEW می‌مانند)"
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
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFFF59E0B),
                            contentColor = Color(0xFF1E1B18)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "تنظیمات",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تنظیم سرور و توکن")
                    }
                }
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("permissions_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = "مجوزهای لازم",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "دسترسی‌های موردنیاز اندروید",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Text(
                text = buildString {
                    if (!hasSmsPermissions) {
                        append("• مجوز دریافت و خواندن پیامک (RECEIVE_SMS / READ_SMS) برای خواندن و پارس پیامک‌های واریز بانکی الزامی است.\n")
                    }
                    if (!hasNotificationPermission) {
                        append("• مجوز اعلان (POST_NOTIFICATIONS) برای هشدار سفارش‌های منطبق، وضعیت AMBIGUOUS و خطای ۴۰۱ لازم است.")
                    }
                }.trim(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("grant_permissions_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            ) {
                Text("اعطای مجوزهای برنامه")
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("last_sent_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "آخرین تأیید",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "آخرین سفارش تأییدشده (MATCHED)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TextButton(
                    onClick = onViewAllLogs,
                    modifier = Modifier.testTag("view_all_logs_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "مشاهده لاگ‌ها",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("همه لاگ‌ها")
                }
            }

            if (lastMatchedLog == null) {
                Text(
                    text = "هنوز واریزی با سفارش تطبیق و تأیید نشده است. برای تست، از «پیامک تستی» یا تب «لاگ‌ها» استفاده کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val timeStr = rememberFormattedTime(lastMatchedLog.decidedMs ?: lastMatchedLog.receivedMs)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${lastMatchedLog.bankTitle} • ${lastMatchedLog.matchedLabelFa() ?: ""}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (lastMatchedLog.amountMinor != null) {
                    Text(
                        text = "مبلغ واریز: ${SmsParser.formatToman(lastMatchedLog.amountMinor)} تومان (${SmsParser.formatRial(lastMatchedLog.amountMinor)} ریال)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = lastMatchedLog.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SimulateBankSmsDialog(
    onDismiss: () -> Unit,
    onSendSimulated: (sender: String, body: String) -> Unit
) {
    var sender by rememberSaveable { mutableStateOf("98200033") }
    var body by rememberSaveable {
        mutableStateOf("بانک ملت\nواریز به حساب 0123456789\nمبلغ: 5,000,070 ریال\nمانده: 18,400,000 ریال\n1405/08/20-15:30")
    }

    val previewParsed = SmsParser.parseFull(body, sender)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "شبیه‌سازی پیامک بانکی (ورود به صف تطبیق)",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "این پیامک با SmsParser پارس شده، در صورت واریز بودن با وضعیت NEW ذخیره می‌شود و بلافاصله MatcherWorker لیست سفارش‌های منتظر (GET /sms-pending) را بررسی می‌کند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = sender,
                    onValueChange = { sender = it },
                    label = { Text("فرستنده (bank / originatingAddress)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("simulate_sender_input")
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("متن خام پیامک بانکی") },
                    minLines = 4,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("simulate_body_input")
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "خروجی SmsParser.parse:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (previewParsed.amountMinor != null) {
                                "status=NEW • amountMinor=${SmsParser.formatRial(previewParsed.amountMinor)} ریال (${SmsParser.formatToman(previewParsed.amountMinor)} تومان)"
                            } else {
                                "status=IGNORED • reason=${previewParsed.reason} (${SmsParser.reasonLabelFa(previewParsed.reason)})"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSendSimulated(sender, body) },
                modifier = Modifier.testTag("confirm_simulate_sms_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "ثبت و تطبیق",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("ثبت و اجرای تطبیق")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

@Composable
fun rememberFormattedTime(timestampMs: Long): String {
    val sdf = SimpleDateFormat("yyyy/MM/dd - HH:mm:ss", Locale.US)
    return sdf.format(Date(timestampMs))
}
