package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.data.LogEntry
import com.example.data.LogStatus
import com.example.data.SmsParser

private enum class LogFilter(val labelFa: String) {
    ALL("همه"),
    SENT("ارسال‌شده"),
    QUEUE("در صف / تلاش مجدد"),
    FAILED("ناموفق")
}

@Composable
fun LogsScreen(
    logs: List<LogEntry>,
    onRetrySingle: (LogEntry) -> Unit,
    onClearAllLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }

    val filteredLogs by remember(logs, selectedFilter) {
        derivedStateOf {
            when (selectedFilter) {
                LogFilter.ALL -> logs
                LogFilter.SENT -> logs.filter { it.statusEnum == LogStatus.SENT }
                LogFilter.QUEUE -> logs.filter {
                    it.statusEnum == LogStatus.QUEUED || it.statusEnum == LogStatus.RETRY
                }
                LogFilter.FAILED -> logs.filter { it.statusEnum == LogStatus.FAILED }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header row with filter chips and Clear Logs button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "لاگ پردازش پیامک‌ها (${logs.size})",
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
                        text = "هیچ لاگی در این بخش ثبت نشده است",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "به محض دریافت پیامک بانکی جدید یا زدن دکمهٔ «پیامک تستی» در صفحهٔ خانه، وضعیت ارسال با رنگ‌بندی در اینجا نمایش داده می‌شود.",
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
                    LogEntryCard(
                        entry = entry,
                        onRetry = { onRetrySingle(entry) }
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("پاک کردن تمام لاگ‌ها؟") },
            text = {
                Text("آیا مطمئن هستید که می‌خواهید تاریخچهٔ لاگ‌های داخل برنامه را پاک کنید؟")
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
private fun LogEntryCard(
    entry: LogEntry,
    onRetry: () -> Unit
) {
    // Status color coding per spec/ROADMAP.md:
    // Green = SENT, Yellow/Amber = QUEUED/RETRY, Red = FAILED
    val (statusLabel, badgeBgColor, badgeTextColor, statusIcon) = when (entry.statusEnum) {
        LogStatus.SENT -> Quadruple(
            "ارسال‌شده (${entry.httpCode ?: 200})",
            Color(0xFF10B981).copy(alpha = 0.18f),
            Color(0xFF10B981),
            Icons.Default.CheckCircle
        )
        LogStatus.QUEUED -> Quadruple(
            "در صف ارسال",
            Color(0xFFF59E0B).copy(alpha = 0.18f),
            Color(0xFFF59E0B),
            Icons.Default.HourglassTop
        )
        LogStatus.RETRY -> Quadruple(
            "در صف تلاش مجدد (${entry.retryCount})",
            Color(0xFFF59E0B).copy(alpha = 0.18f),
            Color(0xFFF59E0B),
            Icons.Default.HourglassTop
        )
        LogStatus.FAILED -> Quadruple(
            "ناموفق ${entry.httpCode?.let { "($it)" } ?: ""}",
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
                Column {
                    Text(
                        text = entry.bankTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "فرستنده: ${entry.sender} • کد بانک: ${entry.bankCode}",
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

            if (entry.amountRial != null) {
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
                            text = "مبلغ واریز تشخیص‌داده‌شده:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${SmsParser.formatToman(entry.amountRial)} تومان (${SmsParser.formatRial(entry.amountRial)} ریال)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Full SMS Body
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

            if (entry.serverStatus != null) {
                Text(
                    text = "پاسخ سرور: status=${entry.serverStatus} • تایید خودکار=${entry.approvedCount ?: 0} • اثرانگشت=${entry.fingerprint.take(10)}…",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (!entry.errorMessage.isNullOrBlank()) {
                Text(
                    text = entry.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (entry.statusEnum == LogStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rememberFormattedTime(entry.updatedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (entry.statusEnum != LogStatus.SENT) {
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.testTag("retry_log_button_${entry.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "تلاش مجدد",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ارسال مجدد اکنون")
                    }
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
