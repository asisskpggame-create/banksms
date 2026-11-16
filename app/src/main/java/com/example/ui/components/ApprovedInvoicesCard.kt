package com.example.ui.components

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.PendingInvoiceEntity
import com.example.data.SmsParser
import com.example.ui.rememberFormattedTime
import java.util.Calendar

@Composable
fun ApprovedInvoicesLedgerCard(
    invoices: List<PendingInvoiceEntity>,
    modifier: Modifier = Modifier
) {
    val approvedInvoices = remember(invoices) {
        invoices.filter { it.status == "MATCHED" }
            .sortedByDescending { it.matchedAtMs ?: it.syncedAtMs }
    }
    val totalMinor = remember(approvedInvoices) {
        approvedInvoices.sumOf { it.dueMinor.coerceAtLeast(0L) }
    }
    val todayStats = remember(approvedInvoices) {
        val startOfDayMs = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todayList = approvedInvoices.filter { (it.matchedAtMs ?: it.syncedAtMs) >= startOfDayMs }
        todayList.size to todayList.sumOf { it.dueMinor.coerceAtLeast(0L) }
    }

    var expanded by rememberSaveable { mutableStateOf(false) }
    val greenAccent = Color(0xFF10B981)

    AsisElevatedCard(
        modifier = modifier
            .animateContentSize(animationSpec = AsisMotion.smoothSpring())
            .testTag("approved_invoices_card"),
        borderColor = if (approvedInvoices.isNotEmpty()) {
            greenAccent.copy(alpha = 0.38f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
    ) {
        SectionHeader(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            title = "فاکتورهای تأییدشده و قیمت‌ها",
            subtitle = "ثبت خودکار و لحظه‌ای تمام فاکتورهای پرداخت‌شده همراه با مبلغ دقیق",
            tint = greenAccent,
            trailing = {
                StatusPill(
                    label = "${approvedInvoices.size} فاکتور",
                    color = greenAccent,
                    icon = Icons.Default.Verified
                )
            }
        )

        // Total Price Highlight Banner
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = greenAccent.copy(alpha = 0.13f),
            border = BorderStroke(1.dp, greenAccent.copy(alpha = 0.34f)),
            modifier = Modifier.fillMaxWidth()
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(greenAccent.copy(alpha = 0.20f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Paid,
                                contentDescription = null,
                                tint = greenAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "جمع کل فاکتورهای تأییدشده",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "امروز: ${todayStats.first} فاکتور (${SmsParser.formatToman(todayStats.second)} تومان)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                AnimatedContent(
                    targetState = totalMinor,
                    transitionSpec = {
                        (fadeIn(tween(200)) + slideInVertically(AsisMotion.offsetSpring()) { it / 4 })
                            .togetherWith(fadeOut(tween(140)))
                    },
                    label = "approvedTotalPriceAnim"
                ) { sumMinor ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "${SmsParser.formatToman(sumMinor)} تومان",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = greenAccent,
                            modifier = Modifier.testTag("approved_invoices_total_price")
                        )
                        Text(
                            text = "${SmsParser.formatRial(sumMinor)} ریال",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (approvedInvoices.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "هنوز فاکتور تأییدشده‌ای ثبت نشده است. به محض تطبیق پیامک واریز با سفارش، شناسهٔ فاکتور و قیمت آن اینجا ثبت و نمایش داده می‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else {
            val visibleInvoices = if (expanded) approvedInvoices else approvedInvoices.take(5)
            Column(
                modifier = Modifier.animateContentSize(animationSpec = AsisMotion.smoothSpring()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (inv in visibleInvoices) {
                    ApprovedInvoiceRowItem(invoice = inv, accent = greenAccent)
                }
            }

            AnimatedVisibility(
                visible = approvedInvoices.size > 5,
                enter = fadeIn(tween(180)) + expandVertically(animationSpec = AsisMotion.smoothSpring()),
                exit = fadeOut(tween(140)) + shrinkVertically(animationSpec = AsisMotion.smoothSpring())
            ) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("toggle_all_approved_invoices_button")
                ) {
                    Text(
                        text = if (expanded) {
                            "نمایش کمتر"
                        } else {
                            "مشاهده همه ${approvedInvoices.size} فاکتور تأییدشده"
                        },
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ApprovedInvoiceRowItem(
    invoice: PendingInvoiceEntity,
    accent: Color
) {
    val kindLabel = when (invoice.kind) {
        "topup" -> "شارژ کیف پول"
        "sms-in" -> "ارسال مستقیم"
        else -> "فاکتور سفارش"
    }
    val timeText = rememberFormattedTime(invoice.matchedAtMs ?: invoice.syncedAtMs)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("approved_invoice_item_${invoice.publicId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "$kindLabel • ${invoice.publicId}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = buildString {
                        invoice.bankTitle?.takeIf { it.isNotBlank() }?.let {
                            append(it)
                            append(" • ")
                        }
                        append(timeText)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accent.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "${SmsParser.formatToman(invoice.dueMinor)} تومان",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("approved_invoice_price_${invoice.publicId}")
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${SmsParser.formatRial(invoice.dueMinor)} ریال",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
