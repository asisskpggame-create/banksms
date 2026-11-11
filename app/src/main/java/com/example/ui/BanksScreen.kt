package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.GatewaySettings
import com.example.data.SmsParser

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BanksScreen(
    settings: GatewaySettings,
    onToggleFilterEnabled: (Boolean) -> Unit,
    onToggleBank: (String) -> Unit,
    onSelectAllBanks: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var parserTestInput by rememberSaveable {
        mutableStateOf("بانک سامان\nواریز به حساب 821-100-1234567-1\nمبلغ 485,000 تومان\nمانده 9,200,000 تومان\n1405/08/20 14:22")
    }
    val parsedPreview = SmsParser.parse(parserTestInput)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Optional Bank Filter Card (Section 4 of ROADMAP.md)
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
                            text = "فیلتر بانک‌های انتخابی (اختیاری)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (settings.filterBanksEnabled) {
                                "فقط پیامک بانک‌های انتخاب‌شده در زیر به سرور ارسال می‌شوند."
                            } else {
                                "همهٔ پیامک‌های دریافتی به سرور ارسال می‌شوند (رفتار پیش‌فرض سرور)."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.filterBanksEnabled,
                        onCheckedChange = onToggleFilterEnabled,
                        modifier = Modifier.testTag("filter_banks_switch")
                    )
                }

                if (settings.filterBanksEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSelectAllBanks(true) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("select_all_banks_button")
                        ) {
                            Text("انتخاب همه")
                        }
                        OutlinedButton(
                            onClick = { onSelectAllBanks(false) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("deselect_all_banks_button")
                        ) {
                            Text("حذف همه")
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (bank in SmsParser.KNOWN_BANKS) {
                            val selected = settings.selectedBankCodes.contains(bank.code)
                            FilterChip(
                                selected = selected,
                                onClick = { onToggleBank(bank.code) },
                                label = { Text(bank.titleFa) },
                                modifier = Modifier.testTag("bank_chip_${bank.code}")
                            )
                        }
                    }
                }
            }
        }

        // Interactive Local Parser Tester (`reference/sms_parse.py` verifier)
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
                Text(
                    text = "آزمایشگر زندهٔ پارسر پیامک (منطق sms_parse.py)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "هر متن پیامک بانکی را در کادر زیر قرار دهید تا دقیقاً ببینید سرور ربات شما چگونه مبلغ ریال/تومان و اثرانگشت (Fingerprint) آن را استخراج می‌کند:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = parserTestInput,
                    onValueChange = { parserTestInput = it },
                    label = { Text("متن نمونه پیامک بانکی") },
                    minLines = 4,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("parser_tester_input")
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "بانک تشخیص‌داده‌شده: ${parsedPreview.bankTitleFa} (code: ${parsedPreview.bankCode})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (parsedPreview.amountMinor != null) {
                            Text(
                                text = "مبلغ واریز (amount_minor): ${SmsParser.formatRial(parsedPreview.amountMinor)} ریال (معادل ${SmsParser.formatToman(parsedPreview.amountMinor)} تومان)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Text(
                                text = "مبلغ واریز: None • دلیل: ${SmsParser.reasonLabelFa(parsedPreview.reason)} (${parsedPreview.reason})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            text = "Fingerprint (SHA-256):\n${parsedPreview.fingerprint}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Build & Self-Signing Instructions Card (as requested in AI_STUDIO_PROMPT.txt item 2)
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
                    text = "راهنمای بیلد و امضای شخصی (بدون هیچ قفل امضا)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "این برنامه هیچ بررسی امضا (Signature Check) یا کد مخفی ندارد و کاملاً متعلق به شماست. برای ساخت کلید امضای شخصی و خروجی Release:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "1) ساخت کلید امضای شخصی:\nkeytool -genkey -v -keystore asis-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload\n\n2) بیلد نسخه Release:\nKEYSTORE_PATH=/path/to/asis-release.jks STORE_PASSWORD=*** KEY_PASSWORD=*** ./gradlew assembleRelease",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    )
                }
            }
        }
    }
}
