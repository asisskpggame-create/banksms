package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.example.data.GatewaySettings
import com.example.data.SmsParser
import com.example.network.GatewayApiClient

@Composable
fun SettingsScreen(
    settings: GatewaySettings,
    testConnectionState: TestConnectionUiState,
    onSaveSettings: (endpointUrl: String, token: String, localMaxToman: String) -> Unit,
    onToggleAutoApproveInApp: (Boolean) -> Unit,
    onTestConnection: (endpointUrl: String, token: String) -> Unit,
    onTestPendingEndpoint: (endpointUrl: String, token: String) -> Unit,
    onResetTestState: () -> Unit,
    modifier: Modifier = Modifier
) {
    var endpointInput by rememberSaveable { mutableStateOf(settings.endpointUrl) }
    var tokenInput by rememberSaveable { mutableStateOf(settings.gatewayToken) }
    var localMaxTomanInput by rememberSaveable {
        mutableStateOf(if (settings.localMaxToman > 0L) settings.localMaxToman.toString() else "")
    }
    var showToken by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(settings.endpointUrl, settings.gatewayToken, settings.localMaxToman) {
        if (endpointInput.isEmpty() && settings.endpointUrl.isNotEmpty()) {
            endpointInput = settings.endpointUrl
        }
        if (tokenInput.isEmpty() && settings.gatewayToken.isNotEmpty()) {
            tokenInput = settings.gatewayToken
        }
    }

    val cleanEndpointPreview = GatewayApiClient.cleanCredentialString(endpointInput)
    val isHttpInsecure = cleanEndpointPreview.startsWith("http://", ignoreCase = true)
    val basePanelPreview = GatewayApiClient.extractBasePanelUrl(endpointInput)
    val tokenLen = GatewayApiClient.cleanCredentialString(tokenInput).length

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!settings.isEncryptedStorage) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("unencrypted_storage_warning_card"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "هشدار رمزنگاری",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "هشدار امنیتی: Android Keystore در این دستگاه در دسترس نبود؛ حافظهٔ جایگزین خصوصی برنامه استفاده شده است.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        // App-Side Approval Controls Card (Section 7 of APP_SIDE_APPROVAL_SPEC.md)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "تأیید خودکار در اپ (POST /sms-approve)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (settings.autoApproveInApp) {
                                "روشن: پس از تطبیق دقیق مبلغ با سفارش، اپ به‌طور خودکار فرمان تأیید امضاشده (HMAC-SHA256) را به سرور می‌فرستد."
                            } else {
                                "خاموش (پیش‌فرض امن): اپ پیامک را با سفارش تطبیق می‌دهد و به شما نوتیفیکیشن می‌دهد تا با یک دکمه تأییدش کنید."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                        Text("واریزهای بالاتر از این سقف (به تومان) خودکار تأیید نمی‌شوند و به حالت MANUAL می‌روند.")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("local_max_toman_input")
                )
            }
        }

        // Endpoint & Token Form Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "آدرس پنل و توکن درگاه (EncryptedSharedPreferences)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
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
                    supportingText = {
                        when {
                            isHttpInsecure -> Text(
                                text = "پروتکل http:// مجاز نیست! حتماً از https:// استفاده کنید.",
                                color = MaterialTheme.colorScheme.error
                            )
                            basePanelPreview.isNotEmpty() -> Text(
                                text = "مسیرهای فعال:\n• GET $basePanelPreview/sms-pending\n• POST $basePanelPreview/sms-approve\n• POST $basePanelPreview/sms-in",
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
                    isError = tokenInput.isNotEmpty() && tokenLen < 20,
                    supportingText = {
                        Text(
                            text = "طول فعلی: $tokenLen کاراکتر (حداقل مجاز سرور: ۲۰ کاراکتر — کلید امضای HMAC-SHA256)",
                            color = if (tokenInput.isNotEmpty() && tokenLen < 20) {
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
                        .testTag("save_settings_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "ذخیره تنظیمات",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ذخیره تنظیمات در حافظهٔ رمزنگاری‌شده")
                }

                // Two test buttons per Section 7: 1) Test sms-in («تست اتصال»)  2) Test sms-pending
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onTestConnection(endpointInput, tokenInput) },
                        enabled = testConnectionState !is TestConnectionUiState.Loading,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_connection_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = "تست اتصال sms-in",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تست اتصال (sms-in)")
                    }

                    OutlinedButton(
                        onClick = { onTestPendingEndpoint(endpointInput, tokenInput) },
                        enabled = testConnectionState !is TestConnectionUiState.Loading,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_pending_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "تست sms-pending",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تست sms-pending")
                    }
                }
            }
        }

        // Test Result Banner
        AnimatedVisibility(visible = testConnectionState !is TestConnectionUiState.Idle) {
            when (testConnectionState) {
                is TestConnectionUiState.Loading -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Text(
                                text = "در حال برقراری ارتباط امن با سرور...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                is TestConnectionUiState.SuccessSmsIn -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_connection_success_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
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
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "اتصال موفق",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = "تست اتصال /sms-in موفق بود (HTTP ${testConnectionState.httpCode})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "پاسخ سرور: {\"ok\": true, \"status\": \"${testConnectionState.status}\"}",
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
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_pending_success_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                    text = "دریافت /sms-pending موفق (${testConnectionState.itemsCount} پرداخت منتظر)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "• اختلاف ساعت سرور و گوشی (offset): ${testConnectionState.clockOffsetMs} ms\n" +
                                    "• بازهٔ تطبیق (match_window_hours): ${testConnectionState.matchWindowHours} ساعت\n" +
                                    "• سقف تأیید خودکار سرور (auto_max_toman): ${SmsParser.formatRial(testConnectionState.autoMaxToman)} تومان",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (testConnectionState.sampleItems.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        for (item in testConnectionState.sampleItems) {
                                            Text(
                                                text = "${item.kind} #${item.publicId} -> ${SmsParser.formatToman(item.dueMinor)} تومان (${item.dueMinor} ریال)",
                                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                is TestConnectionUiState.Error -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_connection_error_card"),
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
                                contentDescription = "خطای تست اتصال",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = if (testConnectionState.httpCode != null) {
                                        "خطا در ارتباط با سرور (کد HTTP ${testConnectionState.httpCode})"
                                    } else {
                                        "خطا در ارتباط با سرور"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = testConnectionState.message,
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

        // Security Notice Banner
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
                    imageVector = Icons.Default.Lock,
                    contentDescription = "ذخیره‌سازی رمزنگاری‌شده",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "امضای درخواست‌های تأیید: sig = HMAC_SHA256(token, kind|public_id|fingerprint|amount_minor|ts). توکن فقط از طریق هدر امن X-Token و بدنهٔ TLS ارسال می‌شود، هرگز داخل APK هاردکد نشده و با EncryptedSharedPreferences محافظت می‌گردد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
