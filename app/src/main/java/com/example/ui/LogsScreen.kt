package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.SmsParser
import com.example.data.SmsRecord
import com.example.data.SmsStatus
import com.example.ui.components.StatusPill

private enum class LogFilter(val labelFa: String) {
    ALL("همه"),
    MATCHED("تأییدشده"),
    MANUAL_AMBIGUOUS("بررسی دستی"),
    NEW_RETRY("در صف"),
    IGNORED("نادیده / تست")
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
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    var manualDialogRecord by remember { mutableStateOf<SmsRecord?>(null) }

    val filterCounts by remember(logs) {
        derivedStateOf {
            mapOf(
                LogFilter.ALL to logs.size,
                LogFilter.MATCHED to logs.count { it.statusEnum == SmsStatus.MATCHED },
                LogFilter.MANUAL_AMBIGUOUS to logs.count {
                    it.statusEnum == SmsStatus.MANUAL ||
                        it.statusEnum == SmsStatus.AMBIGUOUS ||
                        it.statusEnum == SmsStatus.FAILED
                },
                LogFilter.NEW_RETRY to logs.count {
                    it.statusEnum == SmsStatus.NEW ||
                        it.statusEnum == SmsStatus.RETRY ||
                        it.statusEnum == SmsStatus.CLAIMED
                },
                LogFilter.IGNORED to logs.count {
                    it.statusEnum == SmsStatus.IGNORED || it.statusEnum == SmsStatus.EXPIRED
                }
            )
        }
    }

    val filteredLogs by remember(logs, selectedFilter, searchQuery) {
        derivedStateOf {
            val statusFiltered = when (selectedFilter) {
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
            val q = searchQuery.trim()
            if (q.isEmpty()) {
                statusFiltered
            } else {
                statusFiltered.filter { rec ->
                    rec.bankTitle.contains(q, ignoreCase = true) ||
                        rec.bank.contains(q, ignoreCase = true) ||
                        rec.body.contains(q, ignoreCase = true) ||
                        (rec.matchedId?.contains(q, ignoreCase = true) == true) ||
                        (rec.amountMinor?.toString()?.contains(q) == true)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "تاریخچهٔ پیامک‌ها و تطبیق",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "مجموع ${logs.size} رکورد ثبت‌شده",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (logs.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("clear_logs_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
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
                        text = "پاک کردن",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        if (logs.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("جستجو بر اساس بانک، مبلغ، کد سفارش یا متن...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "جستجو",
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "پاک کردن جستجو",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("logs_search_input")
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(LogFilter.entries) { filter ->
                val count = filterCounts[filter] ?: 0
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text("${filter.labelFa} ($count)") },
                    shape = RoundedCornerShape(12.dp),
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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = "لاگی وجود ندارد",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Text(
                        text = if (searchQuery.isNotBlank()) {
                            "نتیجه‌ای برای «$searchQuery» پیدا نشد"
                        } else {
                            "هیچ رکوردی در این بخش وجود ندارد"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "پیامک‌های بانکی دریافتی پس از پارس شدن با وضعیت‌های NEW، MATCHED، MANUAL، AMBIGUOUS یا IGNORED اینجا نمایش داده می‌شوند.",
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
            shape = RoundedCornerShape(22.dp),
            title = { Text("پاک کردن تمام رکوردها؟", fontWeight = FontWeight.Bold) },
            text = {
                Text("آیا مطمئن هستید که می‌خواهید کل تاریخچهٔ پیامک‌های ذخیره‌شده را پاک کنید؟")
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
                    modifier = Modifier.testTag("confirm_clear_logs_button"),
                    shape = RoundedCornerShape(12.dp)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SmsRecordCard(
    entry: SmsRecord,
    onDirectApprove: () -> Unit,
    onOpenManualDialog: () -> Unit,
    onRetry: () -> Unit
) {
    val isTestDeposit = entry.reason == "test_deposit_not_sent"
    val (statusLabel, badgeColor, statusIcon) = when (entry.statusEnum) {
        SmsStatus.MATCHED -> Triple("تأییدشده", Color(0xFF10B981), Icons.Default.CheckCircle)
        SmsStatus.CLAIMED -> Triple("در حال ارسال", Color(0xFFF59E0B), Icons.Default.HourglassTop)
        SmsStatus.NEW -> Triple("واریز جدید", Color(0xFFF59E0B), Icons.Default.HourglassTop)
        SmsStatus.RETRY -> Triple("تلاش مجدد (${entry.attempts})", Color(0xFFF59E0B), Icons.Default.HourglassTop)
        SmsStatus.MANUAL -> Triple("نیازمند تأیید", Color(0xFF06B6D4), Icons.Default.WarningAmber)
        SmsStatus.AMBIGUOUS -> Triple("چند سفارش هم‌مبلغ", Color(0xFFF43F5E), Icons.Default.WarningAmber)
        SmsStatus.EXPIRED -> Triple("منقضی", Color(0xFF64748B), Icons.Default.RemoveCircleOutline)
        SmsStatus.IGNORED -> when {
            isTestDeposit -> Triple("تست محلی", Color(0xFF06B6D4), Icons.Default.Science)
            entry.reason == "promo" -> Triple("تبلیغاتی", Color(0xFF64748B), Icons.Default.RemoveCircleOutline)
            entry.reason == "debit" -> Triple("برداشت", Color(0xFF64748B), Icons.Default.RemoveCircleOutline)
            else -> Triple("نادیده", Color(0xFF64748B), Icons.Default.RemoveCircleOutline)
        }
        SmsStatus.FAILED -> Triple("خطا (${entry.httpCode ?: 401})", Color(0xFFF43F5E), Icons.Default.Error)
    }

    val hasActionButtons = entry.amountMinor != null &&
        entry.statusEnum != SmsStatus.MATCHED &&
        entry.statusEnum != SmsStatus.CLAIMED &&
        !isTestDeposit

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("log_item_card_${entry.id}"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(badgeColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.bankTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "فرستنده: ${entry.bank.ifEmpty { "نامشخص" }}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                StatusPill(
                    label = statusLabel,
                    color = badgeColor,
                    icon = statusIcon
                )
            }

            if (entry.amountMinor != null) {
                val amountBg = if (isTestDeposit) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                }
                val amountAccent = if (isTestDeposit) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = amountBg,
                    border = BorderStroke(1.dp, amountAccent.copy(alpha = 0.22f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${SmsParser.formatToman(entry.amountMinor)} تومان",
                            style = MaterialTheme.typography.titleMedium,
                            color = amountAccent,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${SmsParser.formatRial(entry.amountMinor)} ریال",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            entry.matchedLabelFa()?.let { label ->
                Text(
                    text = "تطبیق‌یافته با: $label",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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

            if (entry.reason.isNotBlank() && (entry.statusEnum == SmsStatus.IGNORED || entry.statusEnum == SmsStatus.EXPIRED)) {
                Text(
                    text = SmsParser.reasonLabelFa(entry.reason),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!entry.lastError.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (entry.statusEnum == SmsStatus.FAILED || entry.statusEnum == SmsStatus.AMBIGUOUS) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                    }
                ) {
                    Text(
                        text = entry.lastError,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (entry.statusEnum == SmsStatus.FAILED || entry.statusEnum == SmsStatus.AMBIGUOUS) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

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
                Text(
                    text = "FP: ${entry.fingerprint.take(12)}…",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
            }

            if (hasActionButtons) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!entry.matchedId.isNullOrBlank()) {
                        Button(
                            onClick = onDirectApprove,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("approve_record_button_${entry.id}"),
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "تأیید سفارش",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تأیید ${entry.matchedId}")
                        }
                    } else if (entry.statusEnum == SmsStatus.AMBIGUOUS ||
                        entry.statusEnum == SmsStatus.MANUAL ||
                        entry.statusEnum == SmsStatus.FAILED
                    ) {
                        OutlinedButton(
                            onClick = onOpenManualDialog,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("manual_id_approve_button_${entry.id}"),
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("تأیید با کد")
                        }
                    }

                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("retry_log_button_${entry.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "تطبیق مجدد",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("بررسی مجدد")
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
        shape = RoundedCornerShape(22.dp),
        title = { Text("تأیید دستی واریز", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "مبلغ واریز: ${record.amountMinor?.let { SmsParser.formatToman(it) } ?: "0"} تومان",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = publicId,
                    onValueChange = { publicId = it },
                    label = { Text("شناسه سفارش یا شارژ (مثلاً ORD123)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
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
                        label = { Text("شارژ (topup)") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(publicId.trim(), kind) },
                enabled = publicId.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("ارسال تأیید امضاشده")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}
