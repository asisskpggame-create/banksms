package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.example.data.GatewaySettings
import com.example.data.SmsParser
import com.example.ui.components.AsisElevatedCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill

private data class ParserLabPreset(
    val label: String,
    val sender: String,
    val body: String
)

private val PARSER_PRESETS = listOf(
    ParserLabPreset(
        label = "واریز سامان (تومان)",
        sender = "9820000",
        body = "بانک سامان\nواریز به حساب 821-100-1234567-1\nمبلغ 485,000 تومان\nمانده 9,200,000 تومان\n1405/08/20 14:22"
    ),
    ParserLabPreset(
        label = "واحد چسبیده 50Mریال",
        sender = "98200084210",
        body = "بلو\nواریز 50,000,000ریال به حساب شما\nموجودی: 80,000,000ریال"
    ),
    ParserLabPreset(
        label = "ویپاد پاسارگاد",
        sender = "982000888",
        body = "ویپاد\nواریز به حساب شما\nمبلغ: 12,500,000 ریال\nموجودی: 45,000,000 ریال"
    ),
    ParserLabPreset(
        label = "قرض‌الحسنه رسالت",
        sender = "982000069",
        body = "بانک قرض الحسنه رسالت\nواریز به حساب 10.123456.1\nمبلغ 3,200,000 ریال\nمانده 14,000,000 ریال"
    ),
    ParserLabPreset(
        label = "شماره شخصی (رد)",
        sender = "09123456789",
        body = "واریز به حساب 2,000,000 ریال"
    ),
    ParserLabPreset(
        label = "تبلیغاتی/لینک (رد)",
        sender = "98200033",
        body = "جایزه واریز 1,000,000 ریال! کلیک کنید https://example.com"
    )
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BanksScreen(
    settings: GatewaySettings,
    onToggleFilterEnabled: (Boolean) -> Unit,
    onToggleBank: (String) -> Unit,
    onSelectAllBanks: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var bankSearchQuery by rememberSaveable { mutableStateOf("") }
    var senderInput by rememberSaveable { mutableStateOf(PARSER_PRESETS.first().sender) }
    var parserTestInput by rememberSaveable { mutableStateOf(PARSER_PRESETS.first().body) }

    val filteredBanks = remember(bankSearchQuery) {
        val q = bankSearchQuery.trim()
        if (q.isEmpty()) {
            SmsParser.KNOWN_BANKS
        } else {
            SmsParser.KNOWN_BANKS.filter { bank ->
                bank.titleFa.contains(q, ignoreCase = true) ||
                    bank.code.contains(q, ignoreCase = true) ||
                    bank.senderKeywords.any { it.contains(q, ignoreCase = true) } ||
                    bank.bodyKeywords.any { it.contains(q, ignoreCase = true) }
            }
        }
    }

    val isPersonalSender = SmsParser.isPersonalSender(senderInput)
    val parsedPreview = SmsParser.parseFull(parserTestInput, senderInput)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Iranian Banks Directory & Optional Bank Filter Card
        AsisElevatedCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SectionHeader(
                        icon = Icons.Default.AccountBalance,
                        title = "بانک‌ها و نئوبانک‌های ایران (${SmsParser.KNOWN_BANKS.size} بانک)",
                        subtitle = if (settings.filterBanksEnabled) {
                            "فیلتر محدودکننده فعال: ${settings.selectedBankCodes.size} از ${SmsParser.KNOWN_BANKS.size} بانک مجاز"
                        } else {
                            "پشتیبانی خودکار از تمام بانک‌های دولتی، خصوصی، قرض‌الحسنه، موسسات و نئوبانک‌ها"
                        }
                    )
                }
                Switch(
                    checked = settings.filterBanksEnabled,
                    onCheckedChange = onToggleFilterEnabled,
                    modifier = Modifier.testTag("filter_banks_switch")
                )
            }

            OutlinedTextField(
                value = bankSearchQuery,
                onValueChange = { bankSearchQuery = it },
                label = { Text("جستجوی نام بانک، کد یا سرشماره (مثلاً ملت، دی، ویپاد، 200033)") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = if (bankSearchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { bankSearchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "پاک کردن جستجو"
                            )
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bank_search_input")
            )

            AnimatedVisibility(visible = settings.filterBanksEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSelectAllBanks(true) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("select_all_banks_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("انتخاب همه (${SmsParser.KNOWN_BANKS.size})")
                    }
                    OutlinedButton(
                        onClick = { onSelectAllBanks(false) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("deselect_all_banks_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("حذف همه")
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (bank in filteredBanks) {
                    val selected = !settings.filterBanksEnabled || settings.selectedBankCodes.contains(bank.code)
                    val primaryShortcode = bank.senderKeywords.firstOrNull { it.all(Char::isDigit) }
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (!settings.filterBanksEnabled) {
                                // در حالت پیش‌فرض، لمس یک بانک سرشمارهٔ آن را در آزمایشگاه پارسر قرار می‌دهد
                                senderInput = primaryShortcode ?: bank.code
                                parserTestInput = "${bank.titleFa}\nواریز به حساب شما\nمبلغ: 2,500,000 ریال\nمانده: 15,000,000 ریال"
                            } else {
                                onToggleBank(bank.code)
                            }
                        },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            null
                        },
                        label = {
                            Text(
                                text = if (primaryShortcode != null) {
                                    "${bank.titleFa} ($primaryShortcode)"
                                } else {
                                    bank.titleFa
                                }
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("bank_chip_${bank.code}")
                    )
                }
            }
        }

        // Interactive Local Parser Lab
        AsisElevatedCard {
            SectionHeader(
                icon = Icons.Default.Science,
                title = "آزمایشگاه زندهٔ پارسر پیامک",
                subtitle = "منطق هم‌تراز ۱:۱ با پارسر سرور (sms_parse.py)"
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PARSER_PRESETS.forEach { preset ->
                    AssistChip(
                        onClick = {
                            senderInput = preset.sender
                            parserTestInput = preset.body
                        },
                        label = {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }

            OutlinedTextField(
                value = senderInput,
                onValueChange = { senderInput = it },
                label = { Text("فرستنده (برای تست تشخیص بانک و شمارهٔ شخصی)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("parser_tester_sender_input")
            )

            OutlinedTextField(
                value = parserTestInput,
                onValueChange = { parserTestInput = it },
                label = { Text("متن نمونه پیامک بانکی") },
                minLines = 4,
                maxLines = 6,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("parser_tester_input")
            )

            val isAccepted = !isPersonalSender && parsedPreview.amountMinor != null
            val statusColor = if (isAccepted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = parsedPreview.bankTitleFa,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        StatusPill(
                            label = if (isAccepted) "واریز معتبر" else "ردشده",
                            color = statusColor,
                            icon = if (isAccepted) Icons.Default.CheckCircle else Icons.Default.RemoveCircleOutline
                        )
                    }

                    when {
                        isPersonalSender -> {
                            Text(
                                text = "فرستندهٔ موبایل شخصی تشخیص داده شد (isPersonalSender = true)؛ در SmsReceiver نادیده گرفته می‌شود.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        parsedPreview.amountMinor != null -> {
                            Text(
                                text = "${SmsParser.formatToman(parsedPreview.amountMinor)} تومان (${SmsParser.formatRial(parsedPreview.amountMinor)} ریال)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        else -> {
                            Text(
                                text = "reason = \"${parsedPreview.reason}\" • ${SmsParser.reasonLabelFa(parsedPreview.reason)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "SHA-256 Fingerprint:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = parsedPreview.fingerprint,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    textDirection = TextDirection.Ltr
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}
