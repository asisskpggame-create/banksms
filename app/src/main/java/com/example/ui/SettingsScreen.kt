package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AppThemeMode
import com.example.data.FailedSyncHapticPattern
import com.example.data.FilterAction
import com.example.data.FilterTarget
import com.example.data.GatewaySettings
import com.example.data.HandshakeHapticPattern
import com.example.data.HapticIntensity
import com.example.data.SmsParser
import com.example.network.GatewayApiClient
import com.example.network.PostErrorSyncState
import com.example.ui.components.AsisElevatedCard
import com.example.ui.components.AsisMotion
import com.example.ui.components.CustomFilterRulesCard
import com.example.ui.components.HapticSettingsCard
import com.example.ui.components.NotificationSettingsPanelCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.StorageOptimizationCard
import com.example.ui.theme.AsisCardDark
import com.example.ui.theme.AsisNavyDark
import com.example.ui.theme.AsisOledBlack
import com.example.ui.theme.AsisOledCard

private enum class SettingsCategory(val labelFa: String) {
    ALL("همه"),
    SERVER("سرور و اتصال"),
    MATCHING("تطبیق و فیلتر"),
    ALERTS("اعلان و لرزش"),
    APPEARANCE("ظاهر و حافظه")
}

@Composable
fun SettingsScreen(
    settings: GatewaySettings,
    testConnectionState: TestConnectionUiState,
    onSaveSettings: (endpointUrl: String, token: String, localMaxToman: String) -> Unit,
    onToggleAutoApproveInApp: (Boolean) -> Unit,
    onTestConnection: (endpointUrl: String, token: String) -> Unit,
    onTestPendingEndpoint: (endpointUrl: String, token: String) -> Unit,
    onResetTestState: () -> Unit,
    hasNotificationPermission: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {},
    onToggleNotifyServerError: (Boolean) -> Unit = {},
    onToggleNotifySensitiveSms: (Boolean) -> Unit = {},
    onToggleNotifyAmbiguousManual: (Boolean) -> Unit = {},
    onToggleNotifyApprovedInvoice: (Boolean) -> Unit = {},
    onUpdateSensitiveSmsMinToman: (Long) -> Unit = {},
    onSendTestNotification: () -> Unit = {},
    onSelectThemeMode: (AppThemeMode) -> Unit = {},
    onToggleCustomFiltersEnabled: (Boolean) -> Unit = {},
    onAddCustomFilterRule: (name: String, pattern: String, target: FilterTarget, action: FilterAction) -> Unit = { _, _, _, _ -> },
    onToggleCustomFilterRule: (ruleId: String) -> Unit = {},
    onRemoveCustomFilterRule: (ruleId: String) -> Unit = {},
    postErrorSyncState: PostErrorSyncState = PostErrorSyncState(),
    onToggleAutoSyncAfterErrors: (Boolean) -> Unit = {},
    onSyncAfterErrorsNow: () -> Unit = {},
    totalLogsCount: Int = 0,
    oldLogs15DaysCount: Int = 0,
    onToggleAutoClearOldLogs: (Boolean) -> Unit = {},
    onClearOldLogsNow: () -> Unit = {},
    onToggleHapticMaster: (Boolean) -> Unit = {},
    onToggleHapticHandshake: (Boolean) -> Unit = {},
    onSelectHapticHandshakePattern: (HandshakeHapticPattern) -> Unit = {},
    onSelectHapticHandshakeIntensity: (HapticIntensity) -> Unit = {},
    onTestHandshakeHaptic: () -> Unit = {},
    onToggleHapticFailedSync: (Boolean) -> Unit = {},
    onSelectHapticFailedSyncPattern: (FailedSyncHapticPattern) -> Unit = {},
    onSelectHapticFailedSyncIntensity: (HapticIntensity) -> Unit = {},
    onTestFailedSyncHaptic: () -> Unit = {},
    onToggleHapticCooldown: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var endpointInput by rememberSaveable { mutableStateOf(settings.endpointUrl) }
    var tokenInput by rememberSaveable { mutableStateOf(settings.gatewayToken) }
    var localMaxTomanInput by rememberSaveable {
        mutableStateOf(if (settings.localMaxToman > 0L) settings.localMaxToman.toString() else "")
    }
    var showToken by rememberSaveable { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf(SettingsCategory.ALL) }

    LaunchedEffect(settings.endpointUrl, settings.gatewayToken, settings.localMaxToman) {
        if (endpointInput.isEmpty() && settings.endpointUrl.isNotEmpty()) {
            endpointInput = settings.endpointUrl
        }
        if (tokenInput.isEmpty() && settings.gatewayToken.isNotEmpty()) {
            tokenInput = settings.gatewayToken
        }
    }

    val cleanEndpointPreview = GatewayApiClient.cleanCredentialString(endpointInput)
    val isHttpsValid = cleanEndpointPreview.startsWith("https://", ignoreCase = true)
    val isHttpInsecure = cleanEndpointPreview.startsWith("http://", ignoreCase = true)
    val basePanelPreview = GatewayApiClient.extractBasePanelUrl(endpointInput)
    val tokenLen = GatewayApiClient.cleanCredentialString(tokenInput).length
    val isTokenValid = tokenLen >= 20
    val localMaxPreview = localMaxTomanInput.toLongOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Security & Sync Overview Strip
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = AsisMotion.smoothSpring()),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "امضای دیجیتال HMAC-SHA256 + مخزن AES-256",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "پنجره تطبیق سرور: ${settings.lastServerWindowHours} ساعت • اختلاف ساعت: ${settings.lastClockOffsetMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                StatusPill(
                    label = if (isHttpsValid && isTokenValid) "آماده" else "تنظیم نشده",
                    color = if (isHttpsValid && isTokenValid) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }
                )
            }
        }

        // Category Filter Strip for fast navigation across grouped settings
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(SettingsCategory.entries) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = {
                        Text(
                            text = category.labelFa,
                            fontWeight = if (selectedCategory == category) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("settings_category_${category.name.lowercase()}")
                )
            }
        }

        val showServer = selectedCategory == SettingsCategory.ALL || selectedCategory == SettingsCategory.SERVER
        val showMatching = selectedCategory == SettingsCategory.ALL || selectedCategory == SettingsCategory.MATCHING
        val showAlerts = selectedCategory == SettingsCategory.ALL || selectedCategory == SettingsCategory.ALERTS
        val showAppearance = selectedCategory == SettingsCategory.ALL || selectedCategory == SettingsCategory.APPEARANCE

        // 1. SERVER & CONNECTION (Top Priority)
        if (showServer) {
            AsisElevatedCard {
                SectionHeader(
                    icon = Icons.Default.Shield,
                    title = "اتصال و اعتبارسنجی درگاه",
                    subtitle = "ذخیره‌سازی امن با رمزنگاری AES-256 (EncryptedSharedPreferences)",
                    trailing = {
                        StatusPill(
                            label = if (isHttpsValid && isTokenValid) "پیکربندی کامل" else "ناقص",
                            color = if (isHttpsValid && isTokenValid) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            }
                        )
                    }
                )

                OutlinedTextField(
                    value = endpointInput,
                    onValueChange = {
                        endpointInput = it
                        onResetTestState()
                    },
                    label = { Text("آدرس مسیر پنل سرور (HTTPS)") },
                    placeholder = { Text("https://domain.com/panel-path") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "آدرس سرور"
                        )
                    },
                    isError = isHttpInsecure,
                    shape = RoundedCornerShape(14.dp),
                    supportingText = {
                        when {
                            isHttpInsecure -> Text(
                                text = "پروتکل http:// مجاز نیست! حتماً از https:// استفاده کنید.",
                                color = MaterialTheme.colorScheme.error
                            )
                            basePanelPreview.isNotEmpty() -> Text(
                                text = "مسیرها: /sms-pending • /sms-approve • /sms-in",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    textDirection = TextDirection.Ltr
                                )
                            )
                            else -> Text("آدرس پایهٔ پنل یا آدرس کامل /sms-in را وارد کنید.")
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("endpoint_url_input")
                )

                AnimatedVisibility(
                    visible = basePanelPreview.isNotEmpty() && isHttpsValid,
                    enter = fadeIn(tween(220)) +
                        expandVertically(animationSpec = AsisMotion.smoothSpring()) +
                        scaleIn(initialScale = 0.96f, animationSpec = AsisMotion.bouncySpring()),
                    exit = fadeOut(tween(150)) +
                        shrinkVertically(animationSpec = AsisMotion.smoothSpring()) +
                        scaleOut(targetScale = 0.96f, animationSpec = tween(150))
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "GET  $basePanelPreview/sms-pending",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    textDirection = TextDirection.Ltr
                                ),
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "POST $basePanelPreview/sms-approve",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    textDirection = TextDirection.Ltr
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = {
                        tokenInput = it
                        onResetTestState()
                    },
                    label = { Text("توکن درگاه پیامک (SMS_GATEWAY_TOKEN)") },
                    placeholder = { Text("حداقل ۲۰ کاراکتر") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "توکن"
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { showToken = !showToken },
                            modifier = Modifier.testTag("toggle_token_visibility_button")
                        ) {
                            Icon(
                                imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showToken) "مخفی کردن توکن" else "نمایش توکن"
                            )
                        }
                    },
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    isError = tokenInput.isNotEmpty() && !isTokenValid,
                    shape = RoundedCornerShape(14.dp),
                    supportingText = {
                        Text(
                            text = "طول فعلی: $tokenLen کاراکتر (حداقل مجاز: ۲۰ کاراکتر — کلید امضای HMAC-SHA256)",
                            color = if (tokenInput.isNotEmpty() && !isTokenValid) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gateway_token_input")
                )

                Button(
                    onClick = { onSaveSettings(endpointInput, tokenInput, localMaxTomanInput) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_settings_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "ذخیره تنظیمات",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ذخیره در حافظهٔ رمزنگاری‌شده", fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onTestConnection(endpointInput, tokenInput) },
                        enabled = testConnectionState !is TestConnectionUiState.Loading,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("test_connection_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = "تست اتصال sms-in",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تست sms-in", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = { onTestPendingEndpoint(endpointInput, tokenInput) },
                        enabled = testConnectionState !is TestConnectionUiState.Loading,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("test_pending_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "تست sms-pending",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تست pending", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "همگام‌سازی خودکار اتصال بعد از خطاها",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "به‌محض بازگشت اینترنت، رفع خطای سرور یا اصلاح توکن، صف خطاها بدون انتظار صف Backoff ارسال می‌شوند.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = settings.autoSyncAfterErrors,
                        onCheckedChange = onToggleAutoSyncAfterErrors,
                        modifier = Modifier.testTag("auto_sync_after_errors_switch")
                    )
                }

                if (settings.lastErrorRecoverySyncAtMs != null && settings.lastErrorRecoverySyncedCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
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
                                text = "آخرین بازیابی پس از خطا: ${settings.lastErrorRecoverySyncedCount} پیامک با موفقیت همگام‌سازی شد",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onSyncAfterErrorsNow,
                    enabled = !postErrorSyncState.isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sync_after_errors_now_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (postErrorSyncState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "همگام‌سازی اتصال بعد از خطاها",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (postErrorSyncState.isSyncing) {
                            "در حال همگام‌سازی اتصال پس از خطا..."
                        } else {
                            "همگام‌سازی اتصال بعد از خطاها"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Test Result Banner placed right under Server & Connection card
            AnimatedVisibility(
                visible = testConnectionState !is TestConnectionUiState.Idle,
                enter = fadeIn(tween(220)) +
                    expandVertically(animationSpec = AsisMotion.smoothSpring()) +
                    scaleIn(initialScale = 0.95f, animationSpec = AsisMotion.bouncySpring()),
                exit = fadeOut(tween(150)) +
                    shrinkVertically(animationSpec = AsisMotion.smoothSpring()) +
                    scaleOut(targetScale = 0.95f, animationSpec = tween(150))
            ) {
                TestConnectionResultBanner(testConnectionState = testConnectionState)
            }
        }

        // 2. MATCHING & CUSTOM SMS FILTERS
        if (showMatching) {
            AsisElevatedCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        SectionHeader(
                            icon = Icons.Default.VerifiedUser,
                            title = "تأیید خودکار در اپ",
                            subtitle = if (settings.autoApproveInApp) {
                                "روشن: پس از تطبیق دقیق مبلغ، فرمان تأیید امضاشده ارسال می‌شود"
                            } else {
                                "خاموش: پیامک تطبیق می‌خورد و برای تأیید دستی اعلان ارسال می‌شود"
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = settings.autoApproveInApp,
                        onCheckedChange = onToggleAutoApproveInApp,
                        modifier = Modifier.testTag("auto_approve_switch")
                    )
                }

                OutlinedTextField(
                    value = localMaxTomanInput,
                    onValueChange = { localMaxTomanInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("سقف تومانی تأیید خودکار محلی (اختیاری)") },
                    placeholder = {
                        Text(
                            if (settings.lastServerMaxToman > 0) {
                                "خالی = سقف سرور (${SmsParser.formatRial(settings.lastServerMaxToman)} تومان)"
                            } else {
                                "خالی = استفاده از auto_max_toman سرور"
                            }
                        )
                    },
                    supportingText = {
                        val formattedNote = if (localMaxPreview != null && localMaxPreview > 0L) {
                            "سقف محلی فعلی: ${SmsParser.formatRial(localMaxPreview)} تومان (مبالغ بالاتر به MANUAL می‌روند)"
                        } else {
                            "واریزهای بالاتر از این سقف خودکار تأیید نمی‌شوند و به حالت MANUAL می‌روند."
                        }
                        Text(formattedNote)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("local_max_toman_input")
                )
            }

            CustomFilterRulesCard(
                settings = settings,
                onToggleMasterEnabled = onToggleCustomFiltersEnabled,
                onAddRule = onAddCustomFilterRule,
                onToggleRule = onToggleCustomFilterRule,
                onRemoveRule = onRemoveCustomFilterRule
            )
        }

        // 3. ALERTS & HAPTIC FEEDBACK
        if (showAlerts) {
            NotificationSettingsPanelCard(
                settings = settings,
                hasNotificationPermission = hasNotificationPermission,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onToggleNotifyServerError = onToggleNotifyServerError,
                onToggleNotifySensitiveSms = onToggleNotifySensitiveSms,
                onToggleNotifyAmbiguousManual = onToggleNotifyAmbiguousManual,
                onToggleNotifyApprovedInvoice = onToggleNotifyApprovedInvoice,
                onUpdateSensitiveSmsMinToman = onUpdateSensitiveSmsMinToman,
                onSendTestNotification = onSendTestNotification
            )

            HapticSettingsCard(
                settings = settings,
                onToggleMaster = onToggleHapticMaster,
                onToggleHandshake = onToggleHapticHandshake,
                onSelectHandshakePattern = onSelectHapticHandshakePattern,
                onSelectHandshakeIntensity = onSelectHapticHandshakeIntensity,
                onTestHandshake = onTestHandshakeHaptic,
                onToggleFailedSync = onToggleHapticFailedSync,
                onSelectFailedSyncPattern = onSelectHapticFailedSyncPattern,
                onSelectFailedSyncIntensity = onSelectHapticFailedSyncIntensity,
                onTestFailedSync = onTestFailedSyncHaptic,
                onToggleCooldown = onToggleHapticCooldown
            )
        }

        // 4. APPEARANCE & STORAGE OPTIMIZATION
        if (showAppearance) {
            ThemeAppearanceCard(
                currentMode = settings.themeMode,
                onSelectMode = onSelectThemeMode
            )

            StorageOptimizationCard(
                settings = settings,
                totalLogsCount = totalLogsCount,
                oldLogs15DaysCount = oldLogs15DaysCount,
                onToggleAutoClearOldLogs = onToggleAutoClearOldLogs,
                onClearOldLogsNow = onClearOldLogsNow
            )
        }

        if (showServer) {
            // Security Notice Banner
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
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "ذخیره‌سازی رمزنگاری‌شده",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "امضای درخواست‌های تأیید: HMAC-SHA256(token, kind|public_id|fingerprint|amount_minor|ts). توکن صرفاً از طریق هدر امن X-Token و TLS ارسال می‌شود و در EncryptedSharedPreferences نگهداری می‌گردد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun TestConnectionResultBanner(testConnectionState: TestConnectionUiState) {
    AnimatedContent(
        targetState = testConnectionState,
        transitionSpec = {
            (fadeIn(tween(220)) +
                slideInVertically(AsisMotion.offsetSpring()) { it / 8 } +
                scaleIn(initialScale = 0.96f, animationSpec = AsisMotion.bouncySpring()))
                .togetherWith(
                    fadeOut(tween(140)) + scaleOut(targetScale = 0.96f, animationSpec = tween(140))
                )
        },
        label = "testConnectionResultBanner"
    ) { state ->
    when (state) {
        is TestConnectionUiState.Loading -> {
            AsisElevatedCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Text(
                        text = "در حال برقراری ارتباط امن با سرور...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        is TestConnectionUiState.SuccessSmsIn -> {
            AsisElevatedCard(
                modifier = Modifier.testTag("test_connection_success_card"),
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "اتصال موفق",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Column {
                        Text(
                            text = "تست اتصال /sms-in موفق بود (HTTP ${state.httpCode})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "{\"ok\": true, \"status\": \"${state.status}\"}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                textDirection = TextDirection.Ltr
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        is TestConnectionUiState.SuccessPending -> {
            AsisElevatedCard(
                modifier = Modifier.testTag("test_pending_success_card"),
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "تست sms-pending موفق",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "دریافت /sms-pending موفق (${state.itemsCount} پرداخت منتظر)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "• اختلاف ساعت سرور و گوشی: ${state.clockOffsetMs} ms\n" +
                        "• بازهٔ تطبیق: ${state.matchWindowHours} ساعت\n" +
                        "• سقف تأیید خودکار سرور: ${SmsParser.formatRial(state.autoMaxToman)} تومان",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (state.sampleItems.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (item in state.sampleItems) {
                                Text(
                                    text = "${item.kind} #${item.publicId} → ${SmsParser.formatToman(item.dueMinor)} تومان (${item.dueMinor} ریال)",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                )
                            }
                        }
                    }
                }
            }
        }

        is TestConnectionUiState.Error -> {
            AsisElevatedCard(
                modifier = Modifier.testTag("test_connection_error_card"),
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "خطای تست اتصال",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(26.dp)
                    )
                    Column {
                        Text(
                            text = if (state.httpCode != null) {
                                "خطا در ارتباط با سرور (کد HTTP ${state.httpCode})"
                            } else {
                                "خطا در ارتباط با سرور"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        TestConnectionUiState.Idle -> Unit
    }
    }
}

@Composable
private fun ThemeAppearanceCard(
    currentMode: AppThemeMode,
    onSelectMode: (AppThemeMode) -> Unit
) {
    val isOled = currentMode == AppThemeMode.MIDNIGHT_OLED

    AsisElevatedCard(
        modifier = Modifier
            .animateContentSize(animationSpec = AsisMotion.smoothSpring())
            .testTag("theme_appearance_card"),
        borderColor = if (isOled) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
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
                    icon = if (isOled) Icons.Default.BatterySaver else Icons.Default.DarkMode,
                    title = "حالت نمایش و صرفه‌جویی AMOLED",
                    subtitle = if (isOled) {
                        "فعال: مشکی خالص (#000000) با خاموش کردن پیکسل‌های صفحه"
                    } else {
                        "حالت استاندارد: سرمه‌ای تیره با عمق رنگی متعادل"
                    }
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isOled,
                onCheckedChange = { enabled ->
                    onSelectMode(if (enabled) AppThemeMode.MIDNIGHT_OLED else AppThemeMode.DARK_NAVY)
                },
                modifier = Modifier.testTag("midnight_oled_switch")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ThemePreviewOptionCard(
                mode = AppThemeMode.DARK_NAVY,
                selected = currentMode == AppThemeMode.DARK_NAVY,
                bgPreview = AsisNavyDark,
                cardPreview = AsisCardDark,
                badgeText = "استاندارد",
                onClick = { onSelectMode(AppThemeMode.DARK_NAVY) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("theme_option_dark_navy")
            )
            ThemePreviewOptionCard(
                mode = AppThemeMode.MIDNIGHT_OLED,
                selected = currentMode == AppThemeMode.MIDNIGHT_OLED,
                bgPreview = AsisOledBlack,
                cardPreview = AsisOledCard,
                badgeText = "#000000 OLED",
                onClick = { onSelectMode(AppThemeMode.MIDNIGHT_OLED) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("theme_option_midnight_oled")
            )
        }

        AnimatedVisibility(
            visible = isOled,
            enter = fadeIn(tween(200)) + expandVertically(animationSpec = AsisMotion.smoothSpring()),
            exit = fadeOut(tween(150)) + shrinkVertically(animationSpec = AsisMotion.smoothSpring())
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Contrast,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "حالت Midnight Black فعال است؛ در نمایشگرهای OLED/AMOLED پیکسل‌های مشکی خاموش می‌شوند تا مصرف باتری کاهش یابد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemePreviewOptionCard(
    mode: AppThemeMode,
    selected: Boolean,
    bgPreview: Color,
    cardPreview: Color,
    badgeText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val borderColor = if (selected) accent else MaterialTheme.colorScheme.outlineVariant

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(if (selected) 1.6.dp else 1.dp, borderColor),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Miniature UI Swatch Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgPreview)
                    .border(1.dp, borderColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(accent)
                        )
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cardPreview)
                            .border(0.8.dp, borderColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = mode.titleFa,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "انتخاب شده",
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
