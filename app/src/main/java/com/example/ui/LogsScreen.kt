package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.SmsParser
import com.example.data.SmsRecord
import com.example.data.SmsStatus

private enum class LogFilter(val labelFa: String) {
    ALL("همه"),
    MATCHED("تأییدشده (MATCHED)"),
    MANUAL_AMBIGUOUS("نیازمند تأیید دستی"),
    NEW_RETRY("در صف (NEW/RETRY)"),
    IGNORED("نادیده (IGNORED)")
}

@Composable
fun LogsScreen(
    logs: List<SmsRecord>,
    onApproveManually: (record: SmsRecord, overridePublicId: String?, overrideKind: String?) -> Unit,
    onRetrySingle: (SmsRecord) -> Unit,
    onClearAllLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    var manualDialogRecord by remember { mutableStateOf<SmsRecord?>(null) }

    val filteredLogs by remember(logs, selectedFilter) {
        derivedStateOf {
            when (selectedFilter) {
                LogFilter.ALL -> logs
                LogFilter.MATCHED -> logs.filter { it.statusEnum == SmsStatus.MATCHED }
                LogFilter.MANUAL_AMBIGUOUS -> logs.filter {
                    it.statusEnum == SmsStatus.MANUAL ||
                        it.statusEnum == SmsStatus.AMBIGUOUS ||
                        it.statusEnum == SmsStatus.FAILED
                }
                LogFilter.NEW_RETRY -> logs.filter {
                    it.statusEnum == SmsStatus.NEW ||
                        it.statusEnum == SmsStatus.RETRY ||
                        it.statusEnum == SmsStatus.CLAIMED
                }
                LogFilter.IGNORED -> logs.filter {
                    it.statusEnum == SmsStatus.IGNORED || it.statusEnum == SmsStatus.EXPIRED
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "لاگ پیامک‌ها و تطبیق (${logs.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (logs.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.testTag("clear_logs_button"),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "پاک کردن لاگ",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "پاک کردن لاگ",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(LogFilter.entries) { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter.labelFa) },
                    modifier = Modifier.testTag("log_filter_${filter.name.lowercase()}")
                )
            }
        }

        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = "لاگی وجود ندارد",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "هیچ رکوردی در این فیلتر وجود ندارد",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "پیامک‌های دریافتی پس از پارس شدن با وضعیت‌های NEW، IGNORED، MATCHED، MANUAL یا AMBIGUOUS در اینجا نمایش داده می‌شوند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .testTag("logs_lazy_column"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(
                    items = filteredLogs,
                    key = { it.id }
                ) { entry ->
                    SmsRecordCard(
                        entry = entry,
                        onDirectApprove = {
                            if (!entry.matchedId.isNullOrBlank()) {
                                onApproveManually(entry, entry.matchedId, entry.matchedKind)
                            } else {
                                manualDialogRecord = entry
                            }
                        },
                        onOpenManualDialog = { manualDialogRecord = entry },
                        onRetry = { onRetrySingle(entry) }
                    )
                }
            }
        }
    }

    manualDialogRecord?.let { rec ->
        ManualOrderApproveDialog(
            record = rec,
            onDismiss = { manualDialogRecord = null },
            onConfirm = { publicId, kind ->
                manualDialogRecord = null
                onApproveManually(rec, publicId, kind)
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("پاک کردن تمام رکوردها؟") },
            text = {
                Text("آیا مطمئن هستید که می‌خواهید تاریخچهٔ پیامک‌های ذخیره‌شده در Room را پاک کنید؟")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        onClearAllLogs()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_clear_logs_button")
                ) {
                    Text("بله، پاک کن")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("انصراف")
                }
            }
        )
    }
}

@Composable
private fun SmsRecordCard(
    entry: SmsRecord,
    onDirectApprove: () -> Unit,
    onOpenManualDialog: () -> Unit,
    onRetry: () -> Unit
) {
    val (statusLabel, badgeBgColor, badgeTextColor, statusIcon) = when (entry.statusEnum) {
        SmsStatus.MATCHED -> Quadruple(
            "تأییدشده (MATCHED)",
            Color(0xFF10B981).copy(alpha = 0.18f),
            Color(0xFF10B981),
            Icons.Default.CheckCircle
        )
        SmsStatus.NEW, SmsStatus.CLAIMED -> Quadruple(
            if (entry.statusEnum == SmsStatus.CLAIMED) "در حال ارسال (CLAIMED)" else "واریز جدید (NEW)",
            Color(0xFFF59E0B).copy(alpha = 0.18f),
            Color(0xFFF59E0B),
            Icons.Default.HourglassTop
        )
        SmsStatus.RETRY -> Quadruple(
            "صف تلاش مجدد (${entry.attempts})",
            Color(0xFFF59E0B).copy(alpha = 0.18f),
            Color(0xFFF59E0B),
            Icons.Default.HourglassTop
        )
        SmsStatus.MANUAL -> Quadruple(
            "منتظر تأیید شما (MANUAL)",
            Color(0xFF06B6D4).copy(alpha = 0.18f),
            Color(0xFF06B6D4),
            Icons.Default.WarningAmber
        )
        SmsStatus.AMBIGUOUS -> Quadruple(
            "مبهم - چند سفارش (AMBIGUOUS)",
            Color(0xFFF43F5E).copy(alpha = 0.18f),
            Color(0xFFF43F5E),
            Icons.Default.WarningAmber
        )
        SmsStatus.IGNORED, SmsStatus.EXPIRED -> Quadruple(
            if (entry.statusEnum == SmsStatus.EXPIRED) "منقضی (EXPIRED)" else "نادیده (${entry.reason})",
            Color(0xFF64748B).copy(alpha = 0.18f),
            Color(0xFF94A3B8),
            Icons.Default.RemoveCircleOutline
        )
        SmsStatus.FAILED -> Quadruple(
            "خطای دائمی (${entry.httpCode ?: 401})",
            Color(0xFFF43F5E).copy(alpha = 0.18f),
            Color(0xFFF43F5E),
            Icons.Default.Error
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("log_item_card_${entry.id}"),
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.bankTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "فرستنده: ${entry.bank.ifEmpty { "نامشخص" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = badgeBgColor
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = statusLabel,
                            tint = badgeTextColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = badgeTextColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (entry.amountMinor != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "مبلغ واریز (ریال / تومان):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${SmsParser.formatToman(entry.amountMinor)} تومان (${SmsParser.formatRial(entry.amountMinor)} ریال)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Matched Order/Top-Up Badge
            entry.matchedLabelFa()?.let { label ->
                Text(
                    text = "سفارش منطبق: $label",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            // Raw unmodified SMS Body
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = entry.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                )
            }

            Text(
                text = "Fingerprint: ${entry.fingerprint.take(16)}…",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!entry.lastError.isNullOrBlank()) {
                Text(
                    text = entry.lastError,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (entry.statusEnum == SmsStatus.FAILED || entry.statusEnum == SmsStatus.AMBIGUOUS) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }
                )
            }

            // Action row: One-Tap Approve Button for MANUAL / AMBIGUOUS / NEW deposits
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rememberFormattedTime(entry.receivedMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (entry.amountMinor != null &&
                        entry.statusEnum != SmsStatus.MATCHED &&
                        entry.statusEnum != SmsStatus.CLAIMED
                    ) {
                        if (!entry.matchedId.isNullOrBlank()) {
                            Button(
                                onClick = onDirectApprove,
                                modifier = Modifier.testTag("approve_record_button_${entry.id}"),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "تأیید سفارش",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تأیید ${entry.matchedId}")
                            }
                        } else if (entry.statusEnum == SmsStatus.AMBIGUOUS || entry.statusEnum == SmsStatus.MANUAL) {
                            OutlinedButton(
                                onClick = onOpenManualDialog,
                                modifier = Modifier.testTag("manual_id_approve_button_${entry.id}"),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("تأیید با کد سفارش")
                            }
                        }

                        TextButton(
                            onClick = onRetry,
                            modifier = Modifier.testTag("retry_log_button_${entry.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "تطبیق مجدد",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تطبیق مجدد")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualOrderApproveDialog(
    record: SmsRecord,
    onDismiss: () -> Unit,
    onConfirm: (publicId: String, kind: String) -> Unit
) {
    var publicId by rememberSaveable { mutableStateOf(record.matchedId.orEmpty()) }
    var kind by rememberSaveable { mutableStateOf(record.matchedKind ?: "order") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تأیید دستی واریز (POST /sms-approve)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "مبلغ واریز: ${record.amountMinor?.let { SmsParser.formatToman(it) } ?: "0"} تومان",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = publicId,
                    onValueChange = { publicId = it },
                    label = { Text("شناسه سفارش یا شارژ (public_id مثلاً ORD123)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = kind == "order",
                        onClick = { kind = "order" },
                        label = { Text("سفارش (order)") }
                    )
                    FilterChip(
                        selected = kind == "topup",
                        onClick = { kind = "topup" },
                        label = { Text("شارژ کیف پول (topup)") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(publicId.trim(), kind) },
                enabled = publicId.isNotBlank()
            ) {
                Text("ارسال فرمان تأیید امضاشده")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
