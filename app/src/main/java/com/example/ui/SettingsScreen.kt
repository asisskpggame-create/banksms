package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.GatewaySettings
import com.example.data.SmsParser
import com.example.network.GatewayApiClient
import com.example.ui.components.AsisElevatedCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill

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
        // App-Side Approval Controls Card
        AsisElevatedCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader(
                    icon = Icons.Default.VerifiedUser,
                    title = "تأیید خودکار در اپ",
                    subtitle = if (settings.autoApproveInApp) {
                        "روشن: پس از تطبیق دقیق مبلغ، فرمان تأیید امضاشده ارسال می‌شود"
                    } else {
                        "خاموش: پیامک تطبیق می‌خورد و برای تأیید دستی اعلان ارسال می‌شود"
                    }
                )
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

        // Endpoint & Token Configuration Card
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

            if (basePanelPreview.isNotEmpty() && isHttpsValid) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "GET  $basePanelPreview/sms-pending",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                textDirection = TextDirection.Ltr
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "POST $basePanelPreview/sms-approve",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                textDirection = TextDirection.Ltr
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    .height(48.dp)
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
        }

        // Test Result Banner
        AnimatedVisibility(
            visible = testConnectionState !is TestConnectionUiState.Idle,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            TestConnectionResultBanner(testConnectionState = testConnectionState)
        }

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

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun TestConnectionResultBanner(testConnectionState: TestConnectionUiState) {
    when (testConnectionState) {
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
                            text = "تست اتصال /sms-in موفق بود (HTTP ${testConnectionState.httpCode})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "{\"ok\": true, \"status\": \"${testConnectionState.status}\"}",
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
                        text = "دریافت /sms-pending موفق (${testConnectionState.itemsCount} پرداخت منتظر)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "• اختلاف ساعت سرور و گوشی: ${testConnectionState.clockOffsetMs} ms\n" +
                        "• بازهٔ تطبیق: ${testConnectionState.matchWindowHours} ساعت\n" +
                        "• سقف تأیید خودکار سرور: ${SmsParser.formatRial(testConnectionState.autoMaxToman)} تومان",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (testConnectionState.sampleItems.isNotEmpty()) {
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
                            for (item in testConnectionState.sampleItems) {
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
