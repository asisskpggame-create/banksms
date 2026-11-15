package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.SmsParser
import com.example.data.SmsRecord
import com.example.data.SmsStatus
import com.example.ui.components.StatusPill

private enum class LogFilter(val labelFa: String) {
    ALL("همه"),
    UNKNOWN_DEPOSIT("واریز ناشناس"),
    MANUAL_AMBIGUOUS("نیاز به بررسی دستی"),
    FAILED_ERRORS("خطا در ارسال"),
    MATCHED("تأییدشده"),
    NEW_RETRY("در صف"),
    IGNORED("نادیده / تست")
}

private fun SmsRecord.hasSendError(): Boolean =
    statusEnum == SmsStatus.FAILED ||
        (!lastError.isNullOrBlank() &&
            statusEnum != SmsStatus.MATCHED &&
            statusEnum != SmsStatus.IGNORED &&
            statusEnum != SmsStatus.UNKNOWN_DEPOSIT &&
            statusEnum != SmsStatus.MANUAL)

@Composable
fun LogsScreen(
    logs: List<SmsRecord>,
    onApproveManually: (record: SmsRecord, overridePublicId: String?, overrideKind: String?) -> Unit,
    onRetrySingle: (SmsRecord) -> Unit,
    onClearAllLogs: () -> Unit,
    onRetryAllFailed: () -> Unit = {},
    onMarkAsPersonal: (SmsRecord) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedFilter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    var manualDialogRecord by remember { mutableStateOf<SmsRecord?>(null) }

    val visibleLogs = remember(logs) {
        logs.filterNot { it.reason == "promo" }
    }

    val errorRecordsCount = remember(visibleLogs) {
        visibleLogs.count { it.hasSendError() }
    }

    val filterCounts by remember(visibleLogs) {
        derivedStateOf {
            mapOf(
                LogFilter.ALL to visibleLogs.size,
                LogFilter.UNKNOWN_DEPOSIT to visibleLogs.count {
                    it.statusEnum == SmsStatus.UNKNOWN_DEPOSIT
                },
                LogFilter.MANUAL_AMBIGUOUS to visibleLogs.count {
                    it.statusEnum == SmsStatus.MANUAL ||
                        it.statusEnum == SmsStatus.AMBIGUOUS
                },
                LogFilter.FAILED_ERRORS to visibleLogs.count { it.hasSendError() },
                LogFilter.MATCHED to visibleLogs.count { it.statusEnum == SmsStatus.MATCHED },
                LogFilter.NEW_RETRY to visibleLogs.count {
                    it.statusEnum == SmsStatus.NEW ||
                        it.statusEnum == SmsStatus.RETRY ||
                        it.statusEnum == SmsStatus.CLAIMED
                },
                LogFilter.IGNORED to visibleLogs.count {
                    it.statusEnum == SmsStatus.IGNORED || it.statusEnum == SmsStatus.EXPIRED
                }
            )
        }
    }

    val filteredLogs by remember(visibleLogs, selectedFilter, searchQuery) {
        derivedStateOf {
            val statusFiltered = when (selectedFilter) {
                LogFilter.ALL -> visibleLogs
                LogFilter.UNKNOWN_DEPOSIT -> visibleLogs.filter {
                    it.statusEnum == SmsStatus.UNKNOWN_DEPOSIT
                }
                LogFilter.MANUAL_AMBIGUOUS -> visibleLogs.filter {
                    it.statusEnum == SmsStatus.MANUAL ||
                        it.statusEnum == SmsStatus.AMBIGUOUS
                }
                LogFilter.FAILED_ERRORS -> visibleLogs.filter { it.hasSendError() }
                LogFilter.MATCHED -> visibleLogs.filter { it.statusEnum == SmsStatus.MATCHED }
                LogFilter.NEW_RETRY -> visibleLogs.filter {
                    it.statusEnum == SmsStatus.NEW ||
                        it.statusEnum == SmsStatus.RETRY ||
                        it.statusEnum == SmsStatus.CLAIMED
                }
                LogFilter.IGNORED -> visibleLogs.filter {
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
                        (rec.lastError?.contains(q, ignoreCase = true) == true) ||
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
                    text = if (errorRecordsCount > 0) {
                        "مجموع ${visibleLogs.size} رکورد • $errorRecordsCount مورد دارای خطای ارسال"
                    } else {
                        "مجموع ${visibleLogs.size} رکورد ثبت‌شده"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (errorRecordsCount > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (errorRecordsCount > 0) {
                    Button(
                        onClick = onRetryAllFailed,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("retry_all_logs_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "تلاش مجدد همه خطاها",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "تلاش مجدد ($errorRecordsCount)",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                if (visibleLogs.isNotEmpty()) {
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
        }

        if (visibleLogs.isNotEmpty()) {
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
                        onRetry = { onRetrySingle(entry) },
                        onMarkAsPersonal = { onMarkAsPersonal(entry) }
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
        var clearVisible by remember { mutableStateOf(false) }
        var clearClosingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

        LaunchedEffect(Unit) {
            clearVisible = true
        }
        LaunchedEffect(clearClosingAction) {
            val action = clearClosingAction ?: return@LaunchedEffect
            clearVisible = false
            kotlinx.coroutines.delay(150)
            action()
        }

        Dialog(
            onDismissRequest = { clearClosingAction = { showClearConfirm = false } },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            AnimatedVisibility(
                visible = clearVisible,
                enter = fadeIn(tween(220)) +
                    scaleIn(
                        initialScale = 0.92f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) +
                    slideInVertically { it / 12 },
                exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.92f) + slideOutVertically { it / 12 }
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth(0.90f)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "پاک کردن تمام رکوردها؟",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "آیا مطمئن هستید که می‌خواهید کل تاریخچهٔ پیامک‌های ذخیره‌شده را پاک کنید؟",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                        ) {
                            TextButton(onClick = { clearClosingAction = { showClearConfirm = false } }) {
                                Text("انصراف")
                            }
                            Button(
                                onClick = {
                                    clearClosingAction = {
                                        showClearConfirm = false
                                        onClearAllLogs()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.testTag("confirm_clear_logs_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("بله، پاک کن")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SmsRecordCard(
    entry: SmsRecord,
    onDirectApprove: () -> Unit,
    onOpenManualDialog: () -> Unit,
    onRetry: () -> Unit,
    onMarkAsPersonal: () -> Unit = {}
) {
    val isTestDeposit = entry.reason == "test_deposit_not_sent"
    val (statusLabel, badgeColor, statusIcon) = when (entry.statusEnum) {
        SmsStatus.MATCHED -> Triple("تأییدشده", Color(0xFF10B981), Icons.Default.CheckCircle)
        SmsStatus.CLAIMED -> Triple("در حال ارسال", Color(0xFFF59E0B), Icons.Default.HourglassTop)
        SmsStatus.NEW -> Triple("واریز جدید", Color(0xFFF59E0B), Icons.Default.HourglassTop)
        SmsStatus.RETRY -> Triple("تلاش مجدد (${entry.attempts})", Color(0xFFF59E0B), Icons.Default.HourglassTop)
        SmsStatus.MANUAL -> Triple("نیاز به بررسی دستی", Color(0xFF06B6D4), Icons.Default.WarningAmber)
        SmsStatus.AMBIGUOUS -> Triple("نیاز به بررسی دستی (چند فاکتور)", Color(0xFFF43F5E), Icons.Default.WarningAmber)
        SmsStatus.UNKNOWN_DEPOSIT -> Triple("واریز ناشناس", Color(0xFFA855F7), Icons.Default.WarningAmber)
        SmsStatus.EXPIRED -> Triple("منقضی", Color(0xFF64748B), Icons.Default.RemoveCircleOutline)
        SmsStatus.IGNORED -> when {
            isTestDeposit -> Triple("تست محلی", Color(0xFF06B6D4), Icons.Default.Science)
            entry.reason == "promo" -> Triple("تبلیغاتی", Color(0xFF64748B), Icons.Default.RemoveCircleOutline)
            entry.reason == "debit" -> Triple("برداشت", Color(0xFF64748B), Icons.Default.RemoveCircleOutline)
            entry.reason == "personal_unrelated" -> Triple("واریز شخصی", Color(0xFF64748B), Icons.Default.RemoveCircleOutline)
            else -> Triple("نادیده", Color(0xFF64748B), Icons.Default.RemoveCircleOutline)
        }
        SmsStatus.FAILED -> Triple("خطا (${entry.httpCode ?: 401})", Color(0xFFF43F5E), Icons.Default.Error)
    }

    val hasActionButtons = entry.amountMinor != null &&
        entry.statusEnum != SmsStatus.MATCHED &&
        entry.statusEnum != SmsStatus.CLAIMED &&
        entry.statusEnum != SmsStatus.IGNORED &&
        !isTestDeposit

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val bankSpec = remember(entry.bank, entry.bankTitle) {
                    BankVisualCatalog.findByCodeOrTitle(entry.bank, entry.bankTitle)
                }
                if (bankSpec != null) {
                    BankLogoBadge(
                        spec = bankSpec,
                        size = 42.dp
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(badgeColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
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
                val isUnknownDeposit = entry.statusEnum == SmsStatus.UNKNOWN_DEPOSIT
                val isManualReview = entry.statusEnum == SmsStatus.MANUAL
                val isCriticalError = entry.statusEnum == SmsStatus.FAILED ||
                    entry.statusEnum == SmsStatus.AMBIGUOUS ||
                    entry.statusEnum == SmsStatus.RETRY

                val errContainer = when {
                    isUnknownDeposit -> Color(0xFFA855F7).copy(alpha = 0.14f)
                    isManualReview -> Color(0xFF06B6D4).copy(alpha = 0.14f)
                    isCriticalError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                    else -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                }
                val errAccent = when {
                    isUnknownDeposit -> Color(0xFFC084FC)
                    isManualReview -> Color(0xFF22D3EE)
                    isCriticalError -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.tertiary
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = errContainer,
                    border = BorderStroke(1.dp, errAccent.copy(alpha = 0.35f)),
                    modifier = Modifier.testTag("log_error_reason_box_${entry.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isUnknownDeposit || isManualReview) Icons.Default.WarningAmber else Icons.Default.Error,
                            contentDescription = "جزئیات وضعیت",
                            tint = errAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    isUnknownDeposit -> "نتیجهٔ ماژول تطبیق هوشمند (واریز ناشناس):"
                                    isManualReview -> "نتیجهٔ ماژول تطبیق هوشمند (نیاز به بررسی دستی):"
                                    else -> buildString {
                                        append("علت خطا در ارسال")
                                        if (entry.httpCode != null) {
                                            append(" (کد HTTP ${entry.httpCode})")
                                        } else {
                                            append(" (خطای ارتباط / عدم اتصال)")
                                        }
                                        if (entry.attempts > 0) {
                                            append(" • تلاش #${entry.attempts}")
                                        }
                                        append(":")
                                    }
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = errAccent
                            )
                            Text(
                                text = entry.lastError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
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
                        entry.statusEnum == SmsStatus.UNKNOWN_DEPOSIT ||
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

                    if (entry.statusEnum == SmsStatus.UNKNOWN_DEPOSIT ||
                        entry.statusEnum == SmsStatus.MANUAL ||
                        entry.statusEnum == SmsStatus.AMBIGUOUS
                    ) {
                        OutlinedButton(
                            onClick = onMarkAsPersonal,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("mark_personal_button_${entry.id}"),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("واریز شخصی")
                        }
                    }

                    val isSendError = entry.hasSendError()
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSendError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                            contentColor = if (isSendError) {
                                MaterialTheme.colorScheme.onError
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("retry_log_button_${entry.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "تلاش مجدد دستی",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSendError) "تلاش مجدد دستی" else "بررسی مجدد",
                            fontWeight = FontWeight.Bold
                        )
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
    var modalVisible by remember { mutableStateOf(false) }
    var closingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(Unit) {
        modalVisible = true
    }
    LaunchedEffect(closingAction) {
        val action = closingAction ?: return@LaunchedEffect
        modalVisible = false
        kotlinx.coroutines.delay(150)
        action()
    }

    Dialog(
        onDismissRequest = { closingAction = onDismiss },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedVisibility(
            visible = modalVisible,
            enter = fadeIn(tween(220)) +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) +
                slideInVertically { it / 12 },
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.92f) + slideOutVertically { it / 12 }
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .animateContentSize()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "تأیید دستی واریز",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        TextButton(onClick = { closingAction = onDismiss }) {
                            Text("انصراف")
                        }
                        Button(
                            onClick = { closingAction = { onConfirm(publicId.trim(), kind) } },
                            enabled = publicId.isNotBlank(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("ارسال تأیید امضاشده")
                        }
                    }
                }
            }
        }
    }
}
