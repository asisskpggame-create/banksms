package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.SmsParser

private data class SampleSmsPreset(
    val label: String,
    val sender: String,
    val body: String
)

private val SIMULATOR_PRESETS = listOf(
    SampleSmsPreset(
        label = "واریز ملت (ریال)",
        sender = "98200033",
        body = "بانک ملت\nواریز به حساب 0123456789\nمبلغ: 5,000,070 ریال\nمانده: 18,400,000 ریال\n1405/08/20-15:30"
    ),
    SampleSmsPreset(
        label = "واحد چسبیده 50M",
        sender = "98200084210",
        body = "بلوبانک\nواریز به حساب شما\nمبلغ 50,000,000ریال\nموجودی 75,000,000ریال"
    ),
    SampleSmsPreset(
        label = "واریز تومان (سامان)",
        sender = "9820000",
        body = "بانک سامان\nواریز به حساب\nمبلغ 485,000 تومان\nموجودی: 9,200,000 تومان"
    ),
    SampleSmsPreset(
        label = "برداشت / خرید",
        sender = "98600060",
        body = "بانک ملی\nبرداشت از حساب\nمبلغ: 500,000 ریال\nمانده: 1,200,000 ریال"
    ),
    SampleSmsPreset(
        label = "تبلیغاتی (رد خودکار)",
        sender = "98200033",
        body = "هدیه ویژه واریز 100,000 تومان برای اطلاعات بیشتر روی لینک https://promo.example کلیک کنید"
    )
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimulateBankSmsDialog(
    onDismiss: () -> Unit,
    onSendSimulated: (sender: String, body: String) -> Unit
) {
    var sender by rememberSaveable { mutableStateOf(SIMULATOR_PRESETS.first().sender) }
    var body by rememberSaveable { mutableStateOf(SIMULATOR_PRESETS.first().body) }

    val isPersonalSender = SmsParser.isPersonalSender(sender)
    val previewParsed = SmsParser.parseFull(body, sender)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "شبیه‌ساز تست محلی پیامک بانکی",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "این پیامک صرفاً به‌صورت محلی توسط SmsParser پارس و در تاریخچه ذخیره می‌شود و هرگز به سرور ارسال نمی‌گردد (جهت جلوگیری از تأیید اشتباه سفارش واقعی).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SIMULATOR_PRESETS.forEach { preset ->
                        AssistChip(
                            onClick = {
                                sender = preset.sender
                                body = preset.body
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
                    value = sender,
                    onValueChange = { sender = it },
                    label = { Text("فرستنده (bank / originatingAddress)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    isError = isPersonalSender,
                    supportingText = if (isPersonalSender) {
                        {
                            Text(
                                text = "فرستندهٔ شماره موبایل شخصی توسط SmsReceiver نادیده گرفته می‌شود.",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("simulate_sender_input")
                )

                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("متن خام پیامک بانکی") },
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("simulate_body_input")
                )

                val validDeposit = !isPersonalSender && previewParsed.amountMinor != null
                val boxColor = if (validDeposit) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                }
                val accentColor = if (validDeposit) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = boxColor,
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (validDeposit) Icons.Default.CheckCircle else Icons.Default.RemoveCircleOutline,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "نتیجهٔ آنی SmsParser (${previewParsed.bankTitleFa}):",
                                style = MaterialTheme.typography.labelLarge,
                                color = accentColor
                            )
                        }
                        Text(
                            text = when {
                                isPersonalSender -> "رد به دلیل فرستندهٔ موبایل شخصی (isPersonalSender = true)"
                                previewParsed.amountMinor != null ->
                                    "واریز معتبر: ${SmsParser.formatToman(previewParsed.amountMinor)} تومان (${SmsParser.formatRial(previewParsed.amountMinor)} ریال)\nثبت تستی: status=IGNORED • reason=test_deposit_not_sent"
                                else ->
                                    "ردشده: reason=${previewParsed.reason} (${SmsParser.reasonLabelFa(previewParsed.reason)})"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSendSimulated(sender, body) },
                modifier = Modifier.testTag("confirm_simulate_sms_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "ثبت تست محلی",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("ثبت تست محلی")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}
