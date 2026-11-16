package com.example.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.GatewaySettings
import com.example.data.SmsParser
import com.example.data.SmsRecord
import com.example.network.PostErrorSyncState
import com.example.network.ServerConnectionLevel
import com.example.network.ServerConnectionStatusUi
import com.example.ui.components.AnimatedSpringCounter
import com.example.ui.components.ApprovedInvoicesLedgerCard
import com.example.ui.components.AsisElevatedCard
import com.example.ui.components.AsisMotion
import com.example.ui.components.AsisSpringModal
import com.example.ui.components.LivePulseDot
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.asisFloatAndSway
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
    isIgnoringBatteryOptimizations: Boolean? = null,
    onRequestIgnoreBatteryOptimizations: (() -> Unit)? = null,
    errorLogs: List<SmsRecord> = emptyList(),
    onRetrySingleLog: (SmsRecord) -> Unit = {},
    onRetryAllFailed: () -> Unit = {},
    invoices: List<com.example.data.PendingInvoiceEntity> = emptyList(),
    onRegisterInvoice: (publicId: String, amountToman: Long, kind: String) -> Unit = { _, _, _ -> },
    onRemoveInvoice: (publicId: String) -> Unit = {},
    serverStatus: ServerConnectionStatusUi = AsisViewModel.computeServerConnectionStatus(
        isOnline = isOnline,
        settings = settings,
        telemetry = com.example.network.ServerConnectionTelemetry()
    ),
    postErrorSyncState: PostErrorSyncState = PostErrorSyncState(),
    onCheckServerConnection: () -> Unit = onFlushQueue,
    onSyncAfterErrors: () -> Unit = onCheckServerConnection,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showSimulateDialog by rememberSaveable { mutableStateOf(false) }
    var showAddInvoiceDialog by rememberSaveable { mutableStateOf(false) }

    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager
    val batteryOptIgnored = isIgnoringBatteryOptimizations ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    } else {
        true
    }

    val requestBatteryExemption: () -> Unit = onRequestIgnoreBatteryOptimizations ?: {
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
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Real-time Server Connection Status Widget (Green / Yellow / Red) + Post-Error Recovery Sync
        ServerConnectionStatusWidget(
            status = serverStatus,
            autoSyncAfterErrors = settings.autoSyncAfterErrors,
            pendingErrorCount = errorLogs.size,
            postErrorSyncState = postErrorSyncState,
            onRefreshClick = onCheckServerConnection,
            onSyncAfterErrors = onSyncAfterErrors,
            onOpenSettings = onNavigateToSettings
        )

        ServiceHeroCard(
            settings = settings,
            isOnline = isOnline,
            onToggleService = onToggleService,
            onNavigateToSettings = onNavigateToSettings
        )

        AnimatedVisibility(
            visible = !settings.lastTokenError.isNullOrBlank(),
            enter = fadeIn(tween(220)) +
                expandVertically(animationSpec = AsisMotion.smoothSpring()) +
                scaleIn(initialScale = 0.96f, animationSpec = AsisMotion.bouncySpring()),
            exit = fadeOut(tween(160)) +
                shrinkVertically(animationSpec = AsisMotion.smoothSpring()) +
                scaleOut(targetScale = 0.96f, animationSpec = tween(160))
        ) {
            TokenErrorAlertBanner(
                errorMessage = settings.lastTokenError.orEmpty(),
                onFixToken = onNavigateToSettings
            )
        }

        AnimatedVisibility(
            visible = !hasSmsPermissions || !hasNotificationPermission,
            enter = fadeIn(tween(220)) +
                expandVertically(animationSpec = AsisMotion.smoothSpring()) +
                scaleIn(initialScale = 0.96f, animationSpec = AsisMotion.bouncySpring()),
            exit = fadeOut(tween(160)) +
                shrinkVertically(animationSpec = AsisMotion.smoothSpring()) +
                scaleOut(targetScale = 0.96f, animationSpec = tween(160))
        ) {
            PermissionsRequiredCard(
                hasSmsPermissions = hasSmsPermissions,
                hasNotificationPermission = hasNotificationPermission,
                onRequestPermissions = onRequestPermissions
            )
        }

        // Battery Optimization Exemption Banner for uninterrupted background SMS forwarding
        AnimatedVisibility(
            visible = !batteryOptIgnored,
            enter = fadeIn(tween(220)) +
                expandVertically(animationSpec = AsisMotion.smoothSpring()) +
                scaleIn(initialScale = 0.96f, animationSpec = AsisMotion.bouncySpring()),
            exit = fadeOut(tween(160)) +
                shrinkVertically(animationSpec = AsisMotion.smoothSpring()) +
                scaleOut(targetScale = 0.96f, animationSpec = tween(160))
        ) {
            AsisElevatedCard(
                modifier = Modifier.testTag("battery_optimization_card"),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                borderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
            ) {
                SectionHeader(
                    icon = Icons.Default.BatteryChargingFull,
                    title = "معافیت از بهینه‌سازی باتری (ضروری برای پس‌زمینه)",
                    subtitle = "جلوگیری از توقف سرویس ارسال پیامک توسط سیستم‌عامل",
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "برای اینکه در حالت صفحه خاموش یا قفل بودن گوشی، پیامک‌های واریز بانکی بدون تأخیر دریافت و توسط WorkManager به سرور ارسال شوند، اجازهٔ فعالیت بدون محدودیت باتری را تایید کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = requestBatteryExemption,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("ignore_battery_opt_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("غیرفعال کردن بهینه‌سازی باتری", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("open_battery_settings_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("لیست برنامه‌ها")
                    }
                }
            }
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
                    .asisFloatAndSway(
                        phaseIndex = 2,
                        floatAmplitudeDp = 1.8f,
                        swayAmplitudeDp = 0.6f,
                        tiltDegrees = 0.22f
                    )
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
                    .asisFloatAndSway(
                        phaseIndex = 3,
                        floatAmplitudeDp = 1.8f,
                        swayAmplitudeDp = 0.6f,
                        tiltDegrees = 0.22f
                    )
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
                    .asisFloatAndSway(
                        phaseIndex = 4,
                        floatAmplitudeDp = 1.8f,
                        swayAmplitudeDp = 0.6f,
                        tiltDegrees = 0.22f
                    )
                    .testTag("stat_failed_card")
                    .clickable(onClick = onNavigateToLogs)
            )
        }

        // Failed Send Error Log & Manual Retry Card
        AnimatedVisibility(
            visible = errorLogs.isNotEmpty(),
            enter = fadeIn(tween(220)) +
                expandVertically(animationSpec = AsisMotion.smoothSpring()) +
                scaleIn(initialScale = 0.96f, animationSpec = AsisMotion.bouncySpring()),
            exit = fadeOut(tween(160)) +
                shrinkVertically(animationSpec = AsisMotion.smoothSpring()) +
                scaleOut(targetScale = 0.96f, animationSpec = tween(160))
        ) {
            FailedDeliveryErrorLogCard(
                errorLogs = errorLogs,
                onRetrySingleLog = onRetrySingleLog,
                onRetryAllFailed = onRetryAllFailed,
                onViewAllLogs = onNavigateToLogs
            )
        }

        // Quick Actions Panel
        AsisElevatedCard(
            modifier = Modifier
                .asisFloatAndSway(
                    phaseIndex = 5,
                    floatAmplitudeDp = 2.2f,
                    swayAmplitudeDp = 1.1f,
                    tiltDegrees = 0.25f
                )
                .testTag("quick_actions_card")
        ) {
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

        // Registered Approved Invoices & Price Summary Card
        ApprovedInvoicesLedgerCard(
            invoices = invoices,
            modifier = Modifier.asisFloatAndSway(
                phaseIndex = 6,
                floatAmplitudeDp = 2.2f,
                swayAmplitudeDp = 1.1f,
                tiltDegrees = 0.25f
            )
        )

        // Smart Amount Matching & 1-Minute Server Invoice Verification Module
        SmartAmountMatchingModuleCard(
            invoices = invoices,
            onOpenAddInvoice = { showAddInvoiceDialog = true },
            onFlushQueue = onFlushQueue,
            onRemoveInvoice = onRemoveInvoice
        )

        // Last Matched Summary Card
        LastMatchedSummaryCard(
            lastMatchedLog = lastSentLog,
            onViewAllLogs = onNavigateToLogs
        )

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

    if (showAddInvoiceDialog) {
        RegisterInvoiceInquiryDialog(
            onDismiss = { showAddInvoiceDialog = false },
            onConfirm = { publicId, amountToman, kind ->
                showAddInvoiceDialog = false
                onRegisterInvoice(publicId, amountToman, kind)
            }
        )
    }
}

@Composable
private fun ServerConnectionStatusWidget(
    status: ServerConnectionStatusUi,
    autoSyncAfterErrors: Boolean,
    pendingErrorCount: Int,
    postErrorSyncState: PostErrorSyncState,
    onRefreshClick: () -> Unit,
    onSyncAfterErrors: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val greenColor = Color(0xFF10B981)
    val yellowColor = Color(0xFFF59E0B)
    val redColor = Color(0xFFEF4444)

    val targetAccent = when (status.level) {
        ServerConnectionLevel.GREEN -> greenColor
        ServerConnectionLevel.YELLOW -> yellowColor
        ServerConnectionLevel.RED -> redColor
    }
    val accentColor by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = AsisMotion.smoothSpring(),
        label = "serverWidgetAccent"
    )
    val containerColor by animateColorAsState(
        targetValue = when (status.level) {
            ServerConnectionLevel.GREEN -> Color(0xFF0A241E)
            ServerConnectionLevel.YELLOW -> Color(0xFF281D0B)
            ServerConnectionLevel.RED -> Color(0xFF2B1117)
        },
        animationSpec = AsisMotion.smoothSpring(),
        label = "serverWidgetBg"
    )

    val statusIcon = when (status.level) {
        ServerConnectionLevel.GREEN -> Icons.Default.CloudDone
        ServerConnectionLevel.YELLOW -> Icons.Default.CloudSync
        ServerConnectionLevel.RED -> Icons.Default.CloudOff
    }

    val badgeLabel = when (status.level) {
        ServerConnectionLevel.GREEN -> status.latencyMs?.let { "${it} ms" } ?: "متصل"
        ServerConnectionLevel.YELLOW -> if (status.isChecking) "در حال تست..." else "هشدار"
        ServerConnectionLevel.RED -> "آفلاین"
    }

    val spinAngle = if (status.isChecking) {
        val transition = rememberInfiniteTransition(label = "pingSpin")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pingSpinAngle"
        )
        angle
    } else {
        0f
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.45f)),
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .asisFloatAndSway(
                phaseIndex = 0,
                floatAmplitudeDp = 2.5f,
                swayAmplitudeDp = 1.3f,
                tiltDegrees = 0.30f
            )
            .animateContentSize(animationSpec = AsisMotion.smoothSpring())
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                if (status.needsConfiguration) onOpenSettings() else onRefreshClick()
            }
            .testTag("server_connection_status_widget")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    // Glowing Icon Box
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(accentColor.copy(alpha = 0.16f))
                            .border(1.dp, accentColor.copy(alpha = 0.40f), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = status.titleFa,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LivePulseDot(
                                isActive = status.level == ServerConnectionLevel.GREEN || status.isChecking,
                                activeColor = accentColor,
                                inactiveColor = accentColor
                            )
                            Text(
                                text = status.titleFa,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = status.detailFa,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 3-Color Traffic Light Capsule + Refresh Button
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Mini Tri-Color LED Capsule (Red / Yellow / Green)
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFF0B1018),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                        modifier = Modifier.testTag("server_status_tri_led")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            val leds = listOf(
                                ServerConnectionLevel.GREEN to greenColor,
                                ServerConnectionLevel.YELLOW to yellowColor,
                                ServerConnectionLevel.RED to redColor
                            )
                            for ((ledLevel, ledColor) in leds) {
                                val isActiveLed = status.level == ledLevel
                                val ledSize by animateDpAsState(
                                    targetValue = if (isActiveLed) 10.dp else 7.dp,
                                    animationSpec = AsisMotion.snappySpring(),
                                    label = "ledSize_${ledLevel.name}"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(ledSize)
                                        .clip(CircleShape)
                                        .background(
                                            if (isActiveLed) ledColor else ledColor.copy(alpha = 0.22f)
                                        )
                                        .then(
                                            if (isActiveLed) {
                                                Modifier.border(
                                                    1.5.dp,
                                                    Color.White.copy(alpha = 0.7f),
                                                    CircleShape
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                )
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            AnimatedContent(
                                targetState = badgeLabel,
                                transitionSpec = {
                                    (fadeIn(tween(180)) + slideInVertically(AsisMotion.offsetSpring()) { it / 3 })
                                        .togetherWith(fadeOut(tween(120)) + slideOutVertically { -it / 3 })
                                },
                                label = "serverBadgeLabel"
                            ) { labelText ->
                                Text(
                                    text = labelText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp
                                    ),
                                    color = accentColor
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (status.lastCheckedAtMs != null) {
                            val formattedTime = remember(status.lastCheckedAtMs) {
                                SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                    .format(Date(status.lastCheckedAtMs))
                            }
                            Text(
                                text = "بررسی: $formattedTime",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = Color.White.copy(alpha = 0.55f)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = !status.isChecking, onClick = onRefreshClick)
                                .testTag("refresh_server_status_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.08f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "بررسی لحظه‌ای اتصال سرور",
                                    tint = accentColor,
                                    modifier = Modifier
                                        .padding(6.dp)
                                        .size(16.dp)
                                        .rotate(spinAngle)
                                )
                            }
                        }
                    }
                }
            }

            // Post-Error Recovery Sync Strip
            val showRecoveryStrip = postErrorSyncState.isSyncing ||
                pendingErrorCount > 0 ||
                postErrorSyncState.lastRecoveryRecoveredCount > 0
            AnimatedVisibility(
                visible = showRecoveryStrip,
                enter = fadeIn(tween(200)) + expandVertically(animationSpec = AsisMotion.smoothSpring()),
                exit = fadeOut(tween(150)) + shrinkVertically(animationSpec = AsisMotion.smoothSpring())
            ) {
                val stripColor = when {
                    postErrorSyncState.isSyncing -> Color(0xFF06B6D4)
                    pendingErrorCount > 0 -> yellowColor
                    else -> greenColor
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, stripColor.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("post_error_sync_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "همگام‌سازی پس از خطا",
                                tint = stripColor,
                                modifier = Modifier
                                    .size(16.dp)
                                    .rotate(if (postErrorSyncState.isSyncing) spinAngle else 0f)
                            )
                            Text(
                                text = when {
                                    postErrorSyncState.isSyncing ->
                                        "در حال همگام‌سازی پس از خطا (${postErrorSyncState.triggerReasonFa ?: "بررسی اتصال"})..."
                                    pendingErrorCount > 0 && autoSyncAfterErrors ->
                                        "$pendingErrorCount مورد در انتظار بازیابی • Sync خودکار پس از رفع خطا فعال است"
                                    pendingErrorCount > 0 ->
                                        "$pendingErrorCount پیامک با خطای ارسال • نیاز به همگام‌سازی دستی"
                                    else ->
                                        "بازیابی خودکار پس از خطا: ${postErrorSyncState.lastRecoveryRecoveredCount} مورد همگام‌سازی شد"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White.copy(alpha = 0.90f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (pendingErrorCount > 0 && !postErrorSyncState.isSyncing) {
                            Spacer(modifier = Modifier.width(6.dp))
                            FilledTonalButton(
                                onClick = onSyncAfterErrors,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = stripColor.copy(alpha = 0.22f),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("sync_after_errors_button")
                            ) {
                                Text(
                                    text = "Sync خطاها",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
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

    val targetGlowColor = when {
        isFullyActive -> Color(0xFF10B981)
        !isConfigured -> Color(0xFFF59E0B)
        else -> Color(0xFF64748B)
    }
    val glowColor by animateColorAsState(
        targetValue = targetGlowColor,
        animationSpec = AsisMotion.smoothSpring(),
        label = "heroGlowColor"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .asisFloatAndSway(
                phaseIndex = 1,
                floatAmplitudeDp = 3.2f,
                swayAmplitudeDp = 1.6f,
                tiltDegrees = 0.42f
            )
            .animateContentSize(animationSpec = AsisMotion.smoothSpring())
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(gradientColors))
            .border(
                width = 1.dp,
                color = glowColor.copy(alpha = if (isFullyActive) 0.45f else 0.25f),
                shape = RoundedCornerShape(24.dp)
            )
            .testTag("service_hero_card")
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
    val context = LocalContext.current
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
                    text = "• مجوز دریافت و خواندن پیامک (RECEIVE_SMS / READ_SMS) برای شنود آنی BroadcastReceiver و تشخیص واریزهای بانکی الزامی است.",
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("grant_permissions_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            ) {
                Text("اعطای دسترسی‌ها", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = {
                    runCatching {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier
                    .height(48.dp)
                    .testTag("open_app_permission_settings_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("تنظیمات اپ")
            }
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
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.42f),
                    accentColor.copy(alpha = 0.14f)
                )
            )
        ),
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(40f, 30f),
                        radius = 220f
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(13.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(accentColor.copy(alpha = 0.18f))
                            .border(1.dp, accentColor.copy(alpha = 0.30f), RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    val numericCount = value.toIntOrNull()
                    if (numericCount != null) {
                        AnimatedSpringCounter(
                            count = numericCount,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = accentColor
                        )
                    } else {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.headlineMedium,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
}

@Composable
private fun LastMatchedSummaryCard(
    lastMatchedLog: SmsRecord?,
    onViewAllLogs: () -> Unit
) {
    AsisElevatedCard(
        modifier = Modifier
            .asisFloatAndSway(
                phaseIndex = 8,
                floatAmplitudeDp = 2.3f,
                swayAmplitudeDp = 1.1f,
                tiltDegrees = 0.26f
            )
            .testTag("last_sent_card")
    ) {
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
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f), RoundedCornerShape(11.dp)),
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

        AnimatedContent(
            targetState = lastMatchedLog,
            transitionSpec = {
                (fadeIn(tween(220)) +
                    slideInVertically(AsisMotion.offsetSpring()) { it / 8 } +
                    scaleIn(initialScale = 0.97f, animationSpec = AsisMotion.bouncySpring()))
                    .togetherWith(
                        fadeOut(tween(150)) +
                            scaleOut(targetScale = 0.97f, animationSpec = tween(150))
                    )
            },
            label = "lastMatchedCardContent"
        ) { currentLog ->
            if (currentLog == null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
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
                val timeStr = rememberFormattedTime(currentLog.decidedMs ?: currentLog.receivedMs)
                val matchedBankSpec = remember(currentLog.bank, currentLog.bankTitle) {
                    BankVisualCatalog.findByCodeOrTitle(currentLog.bank, currentLog.bankTitle)
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill(
                                label = currentLog.matchedLabelFa() ?: "تأییدشده",
                                color = MaterialTheme.colorScheme.primary,
                                icon = Icons.Default.CheckCircle
                            )
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (matchedBankSpec != null) {
                                BankLogoBadge(spec = matchedBankSpec, size = 44.dp)
                            }
                            Column {
                                if (currentLog.amountMinor != null) {
                                    Text(
                                        text = "${SmsParser.formatToman(currentLog.amountMinor)} تومان",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${currentLog.bankTitle} • ${SmsParser.formatRial(currentLog.amountMinor)} ریال",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Text(
                            text = currentLog.body,
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
}

@Composable
private fun FailedDeliveryErrorLogCard(
    errorLogs: List<SmsRecord>,
    onRetrySingleLog: (SmsRecord) -> Unit,
    onRetryAllFailed: () -> Unit,
    onViewAllLogs: () -> Unit
) {
    AsisElevatedCard(
        modifier = Modifier.testTag("home_failed_logs_card"),
        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f),
        borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
    ) {
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
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "خطا در ارسال به سرور",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "لاگ خطاهای ارسال به سرور (${errorLogs.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "علت شکست در ارسال و امکان تلاش مجدد دستی",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FilledTonalButton(
                onClick = onRetryAllFailed,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("home_retry_all_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "تلاش مجدد همه",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("تلاش مجدد همه", fontWeight = FontWeight.Bold)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            errorLogs.take(3).forEach { rec ->
                val timeStr = rememberFormattedTime(rec.receivedMs)
                val reasonText = rec.lastError?.takeIf { it.isNotBlank() }
                    ?: "خطا در برقراری ارتباط با سرور (عدم اتصال یا پاسخ نامعتبر)"
                val bankSpec = remember(rec.bank, rec.bankTitle) {
                    BankVisualCatalog.findByCodeOrTitle(rec.bank, rec.bankTitle)
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_failed_log_item_${rec.id}")
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (bankSpec != null) {
                                    BankLogoBadge(spec = bankSpec, size = 30.dp)
                                }
                                Text(
                                    text = buildString {
                                        append(rec.bankTitle.ifBlank { rec.bank })
                                        if (rec.amountMinor != null) {
                                            append(" • ")
                                            append(SmsParser.formatToman(rec.amountMinor))
                                            append(" تومان")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "تلاش #${rec.attempts} • $timeStr",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 2.dp)
                            )
                            Text(
                                text = "علت خطا: $reasonText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = { onRetrySingleLog(rec) },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("home_retry_log_button_${rec.id}"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "تلاش مجدد دستی",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تلاش مجدد دستی", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (errorLogs.size > 3) {
                TextButton(
                    onClick = onViewAllLogs,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("مشاهده همه ${errorLogs.size} خطای ارسال در بخش لاگ‌ها")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
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

@Composable
private fun SmartAmountMatchingModuleCard(
    invoices: List<com.example.data.PendingInvoiceEntity>,
    onOpenAddInvoice: () -> Unit,
    onFlushQueue: () -> Unit,
    onRemoveInvoice: (String) -> Unit
) {
    val waitingInvoices = remember(invoices) { invoices.filter { it.status == "WAITING" } }
    val matchedInvoices = remember(invoices) { invoices.filter { it.status == "MATCHED" } }

    AsisElevatedCard(
        modifier = Modifier
            .asisFloatAndSway(
                phaseIndex = 7,
                floatAmplitudeDp = 2.4f,
                swayAmplitudeDp = 1.2f,
                tiltDegrees = 0.28f
            )
            .testTag("smart_amount_matcher_card"),
        borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
    ) {
        SectionHeader(
            icon = Icons.Default.AutoAwesome,
            title = "ماژول تطبیق هوشمند مبلغ و فاکتور (۱ دقیقه‌ای)",
            subtitle = "استعلام خودکار سرور هر ۱۵ ثانیه • تشخیص واریز ناشناس و بررسی مجدد پس از وصل اینترنت",
            tint = MaterialTheme.colorScheme.secondary
        )

        Text(
            text = "با دریافت هر پیامک واریز یا استعلام فاکتور از سرور، مبلغ به صورت دقیق با فاکتورهای دیتابیس مقایسه می‌شود. در صورت تطابق، ظرف کمتر از ۱ دقیقه تأیید می‌شود؛ در صورت عدم تطابق، وضعیت «واریز ناشناس» یا «نیاز به بررسی دستی» ثبت شده و در صورت قطعی اینترنت، به محض وصل شدن مجدد شبکه خودکار بررسی و تأیید می‌گردد.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "${waitingInvoices.size} فاکتور",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "منتظر تطبیق در دیتابیس",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "${matchedInvoices.size} فاکتور",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "تأییدشده در لاگ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = waitingInvoices.isNotEmpty(),
            enter = fadeIn(tween(200)) + expandVertically(animationSpec = AsisMotion.smoothSpring()),
            exit = fadeOut(tween(150)) + shrinkVertically(animationSpec = AsisMotion.smoothSpring())
        ) {
            Column(
                modifier = Modifier.animateContentSize(animationSpec = AsisMotion.smoothSpring()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (inv in waitingInvoices.take(3)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "فاکتور ${inv.publicId} • ${SmsParser.formatToman(inv.dueMinor)} تومان",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${SmsParser.formatRial(inv.dueMinor)} ریال • در انتظار تطبیق با لاگ پیامک",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = { onRemoveInvoice(inv.publicId) },
                                modifier = Modifier.testTag("remove_invoice_${inv.publicId}")
                            ) {
                                Text("حذف", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onOpenAddInvoice,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("register_invoice_inquiry_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("+ استعلام / ثبت فاکتور", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onFlushQueue,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("verify_server_invoices_now_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("چک سرور")
            }
        }
    }
}

@Composable
private fun RegisterInvoiceInquiryDialog(
    onDismiss: () -> Unit,
    onConfirm: (publicId: String, amountToman: Long, kind: String) -> Unit
) {
    var publicId by rememberSaveable { mutableStateOf("ORD-${(100..999).random()}") }
    var amountTomanStr by rememberSaveable { mutableStateOf("5000000") }

    AsisSpringModal(onDismissRequest = onDismiss) { triggerClose ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "استعلام / ثبت فاکتور در دیتابیس",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "مبلغ فاکتور را وارد کنید؛ اپلیکیشن بلافاصله لاگ پیامک‌های دریافتی را بررسی کرده و اگر این مبلغ واریز شده باشد در کمتر از ۱ دقیقه تأیید می‌کند (یا منتظر پیامک می‌ماند).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.material3.OutlinedTextField(
                value = publicId,
                onValueChange = { publicId = it },
                label = { Text("شناسه فاکتور (مثلاً ORD-105)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("invoice_id_input")
            )
            androidx.compose.material3.OutlinedTextField(
                value = amountTomanStr,
                onValueChange = { amountTomanStr = it.filter { ch -> ch.isDigit() } },
                label = { Text("مبلغ فاکتور (به تومان)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("invoice_amount_toman_input")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { triggerClose(onDismiss) }) {
                    Text("انصراف")
                }
                Button(
                    onClick = {
                        val amountToman = amountTomanStr.trim().toLongOrNull() ?: 0L
                        triggerClose { onConfirm(publicId, amountToman, "order") }
                    },
                    enabled = (amountTomanStr.trim().toLongOrNull() ?: 0L) > 0L,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("confirm_register_invoice_button")
                ) {
                    Text("بررسی لاگ و ثبت فاکتور")
                }
            }
        }
    }
}


