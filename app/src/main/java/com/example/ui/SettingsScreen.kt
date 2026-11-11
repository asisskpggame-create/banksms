package com.example.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
    onSaveSettings: (endpointUrl: String, token: String) -> Unit,
    onTestConnection: (endpointUrl: String, token: String) -> Unit,
    onResetTestState: () -> Unit,
    modifier: Modifier = Modifier
) {
    var endpointInput by rememberSaveable { mutableStateOf(settings.endpointUrl) }
    var tokenInput by rememberSaveable { mutableStateOf(settings.gatewayToken) }
    var showToken by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(settings.endpointUrl, settings.gatewayToken) {
        if (endpointInput.isEmpty() && settings.endpointUrl.isNotEmpty()) {
            endpointInput = settings.endpointUrl
        }
        if (tokenInput.isEmpty() && settings.gatewayToken.isNotEmpty()) {
            tokenInput = settings.gatewayToken
        }
    }

    val isHttpInsecure = endpointInput.trim().startsWith("http://", ignoreCase = true)
    val normalizedPreview = GatewayApiClient.normalizeUrl(endpointInput)
    val tokenLen = tokenInput.trim().length

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Security Notice Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
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
                    modifier = Modifier.size(26.dp)
                )
                Column {
                    Text(
                        text = "امنیت توکن و آدرس سرور (EncryptedSharedPreferences)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "هیچ توکن یا آدرسی داخل APK هاردکد نشده است. اطلاعات شما با کلید سخت‌افزاری Android Keystore (AES-256-GCM) صرفاً روی همین دستگاه ذخیره می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
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
                    text = "مشخصات اتصال به پنل ASiS",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = endpointInput,
                    onValueChange = {
                        endpointInput = it
                        onResetTestState()
                    },
                    label = { Text("آدرس سرور (HTTPS)") },
                    placeholder = { Text("https://domain.com/panel-path/sms-in") },
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
                                text = "پروتکل http:// مجاز نیست! طبق قرارداد امنیتی حتماً از https:// استفاده کنید.",
                                color = MaterialTheme.colorScheme.error
                            )
                            normalizedPreview.isNotEmpty() -> Text(
                                text = "Endpoint نهایی: $normalizedPreview",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    textDirection = TextDirection.Ltr
                                )
                            )
                            else -> Text("اگر انتهای آدرس /sms-in را ننویسید، برنامه خودکار آن را اضافه می‌کند.")
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
                            text = "طول فعلی: $tokenLen کاراکتر (حداقل مجاز سرور: ۲۰ کاراکتر)",
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onSaveSettings(endpointInput, tokenInput) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_settings_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "ذخیره تنظیمات",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ذخیره تنظیمات")
                    }

                    FilledTonalButton(
                        onClick = { onTestConnection(endpointInput, tokenInput) },
                        enabled = testConnectionState !is TestConnectionUiState.Loading,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_connection_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (testConnectionState is TestConnectionUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.NetworkCheck,
                                contentDescription = "تست اتصال",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تست اتصال")
                    }
                }
            }
        }

        // Test Connection Result Banner
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
                                text = "در حال ارسال متن ثابت «${SmsParser.TEST_MARK}» به سرور...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                is TestConnectionUiState.Success -> {
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
                                    text = "اتصال با موفقیت تایید شد (HTTP ${testConnectionState.httpCode})",
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
                                        "خطا در تست اتصال (کد HTTP ${testConnectionState.httpCode})"
                                    } else {
                                        "خطا در تست اتصال"
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

        // Contract Reference Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "قرارداد ارتباط با سرور (API_CONTRACT)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "POST /<PANEL_PATH>/sms-in\n{\n  \"token\": \"<SMS_GATEWAY_TOKEN>\",\n  \"sms\": \"تست اتصال\",\n  \"bank\": \"\"\n}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            textDirection = TextDirection.Ltr
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
                Text(
                    text = "• کد ۲۰۰: پذیرفته‌شده (حذف از صف)\n• کد ۴۰۱: توکن نامعتبر (عدم تلاش مجدد + اعلان هشدار)\n• کد ۴۰۴: درگاه سمت سرور غیرفعال\n• کد ۴۲۹ / ۵xx / قطعی شبکه: نگهداری در صف و تلاش مجدد خودکار با Backoff نمایی",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
