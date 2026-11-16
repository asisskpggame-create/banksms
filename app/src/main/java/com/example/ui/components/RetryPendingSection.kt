package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.SmsParser
import com.example.data.SmsRecord
import com.example.data.SmsStatus
import com.example.network.BatchRetryPolicy
import com.example.network.BatchRetryState
import com.example.network.PostErrorSyncState
import kotlinx.coroutines.delay

@Composable
fun RetryPendingSectionCard(
    retryPendingItems: List<SmsRecord>,
    batchRetryState: BatchRetryState,
    isOnline: Boolean,
    isServerConfigured: Boolean,
    onRetrySelectedBatch: (List<Long>) -> Unit,
    onRetrySingleItem: (SmsRecord) -> Unit,
    postErrorSyncState: PostErrorSyncState = PostErrorSyncState(),
    autoSyncAfterErrors: Boolean = true,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    var selectedIds by rememberSaveable(retryPendingItems.map { it.id }) {
        mutableStateOf(emptySet<Long>())
    }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(retryPendingItems.isNotEmpty()) {
        if (retryPendingItems.isEmpty()) return@LaunchedEffect
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val earliestNextRetryAt = remember(retryPendingItems, batchRetryState.nextScheduledWorkAtMs) {
        BatchRetryPolicy.earliestAutoRetryAtMs(retryPendingItems)
            ?: batchRetryState.nextScheduledWorkAtMs
    }

    val earliestBackoffWindowMs = remember(retryPendingItems) {
        val candidate = retryPendingItems
            .filter { BatchRetryPolicy.isAutoRetryEligible(it) && it.statusEnum == SmsStatus.RETRY && it.nextRetryAtMs != null }
            .minByOrNull { it.nextRetryAtMs ?: Long.MAX_VALUE }
        if (candidate != null) {
            BatchRetryPolicy.backoffMsForAttempt(candidate.attempts.coerceAtLeast(1))
        } else {
            BatchRetryPolicy.INITIAL_BACKOFF_MS
        }
    }

    val remainingMs = earliestNextRetryAt?.let { (it - nowMs).coerceAtLeast(0L) }
    val rawProgress = if (remainingMs != null && earliestBackoffWindowMs > 0L) {
        (1f - (remainingMs.toFloat() / earliestBackoffWindowMs.toFloat())).coerceIn(0.04f, 1f)
    } else {
        0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = if (batchRetryState.isBatchRunning) 1f else rawProgress,
        animationSpec = AsisMotion.smoothSpring(),
        label = "retryCountdownProgress"
    )

    val allSelected = retryPendingItems.isNotEmpty() && selectedIds.size == retryPendingItems.size
    val accentColor by animateColorAsState(
        targetValue = when {
            batchRetryState.isBatchRunning -> MaterialTheme.colorScheme.primary
            !isOnline || !isServerConfigured -> MaterialTheme.colorScheme.error
            else -> Color(0xFFF59E0B)
        },
        animationSpec = AsisMotion.smoothSpring(),
        label = "retryPendingAccentColor"
    )

    AsisElevatedCard(
        modifier = modifier
            .animateContentSize(animationSpec = AsisMotion.smoothSpring())
            .testTag("retry_pending_section"),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        borderColor = accentColor.copy(alpha = 0.42f),
        cornerRadiusDp = 20
    ) {
        // Header row with expand/collapse and Retry Pending pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Autorenew,
                    contentDescription = "صف ارسال مجدد خودکار",
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "Retry Pending • صف تلاش مجدد دسته‌ای",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${retryPendingItems.size} پیامک در صف ارسال مجدد (حداکثر ${batchRetryState.maxBatchSize} مورد در هر دسته)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatusPill(
                    label = if (batchRetryState.isBatchRunning) "در حال ارسال" else "${retryPendingItems.size} مورد",
                    color = accentColor,
                    isPulsing = batchRetryState.isBatchRunning || (remainingMs != null && isOnline && isServerConfigured)
                )
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.testTag("toggle_retry_pending_expand")
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "جمع کردن" else "باز کردن"
                    )
                }
            }
        }

        // Visual Auto-Retry Countdown Bar (always visible when section is shown)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("retry_pending_countdown_box")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (batchRetryState.isBatchRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = accentColor
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        AnimatedContent(
                            targetState = when {
                                batchRetryState.isBatchRunning -> "در حال پردازش دسته‌ای..."
                                !isOnline -> "شمارش معکوس متوقف (اینترنت قطع)"
                                !isServerConfigured -> "تنظیمات سرور ناقص است"
                                remainingMs == null -> "نیاز به بررسی یا اجرای دستی"
                                remainingMs == 0L -> "آمادهٔ ارسال دسته‌ای..."
                                else -> "تلاش مجدد خودکار بعدی:"
                            },
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                            label = "countdownStatusLabel"
                        ) { labelText ->
                            Text(
                                text = labelText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (!batchRetryState.isBatchRunning && remainingMs != null && isOnline && isServerConfigured) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentColor.copy(alpha = 0.16f),
                            modifier = Modifier.testTag("auto_retry_countdown_timer")
                        ) {
                            Text(
                                text = BatchRetryPolicy.formatCountdownMmSs(remainingMs),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = FontFamily.Monospace,
                                    textDirection = TextDirection.Ltr
                                ),
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (batchRetryState.isBatchRunning) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = accentColor,
                        trackColor = accentColor.copy(alpha = 0.16f)
                    )
                } else if (remainingMs != null && isOnline && isServerConfigured) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = accentColor,
                        trackColor = accentColor.copy(alpha = 0.16f)
                    )
                }

                if (batchRetryState.lastBatchRunAtMs != null && batchRetryState.lastBatchProcessedCount > 0) {
                    Text(
                        text = "آخرین پردازش دسته‌ای: ${batchRetryState.lastBatchProcessedCount} مورد (${batchRetryState.lastBatchSucceededCount} موفق • ${batchRetryState.lastBatchFailedCount} ناموفق)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = when {
                        postErrorSyncState.isSyncing ->
                            "⚡ همگام‌سازی خودکار اتصال بعد از خطاها در حال اجرا است..."
                        autoSyncAfterErrors ->
                            "✓ همگام‌سازی خودکار بمحض برقراری اتصال و رفع خطای سرور فعال است"
                        else ->
                            "همگام‌سازی خودکار پس از خطا خاموش است (فقط زمانبندی Backoff / دستی)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (postErrorSyncState.isSyncing || autoSyncAfterErrors) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.testTag("retry_pending_recovery_status_text")
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(220)) +
                expandVertically(animationSpec = AsisMotion.smoothSpring()) +
                scaleIn(initialScale = 0.97f, animationSpec = AsisMotion.bouncySpring()),
            exit = fadeOut(tween(150)) +
                shrinkVertically(animationSpec = AsisMotion.smoothSpring()) +
                scaleOut(targetScale = 0.97f, animationSpec = tween(150))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            selectedIds = if (allSelected) {
                                emptySet()
                            } else {
                                retryPendingItems.map { it.id }.toSet()
                            }
                        },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("select_all_retry_pending_button")
                    ) {
                        Text(
                            text = if (allSelected) "لغو انتخاب همه" else "انتخاب همه (${retryPendingItems.size})",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = if (selectedIds.isNotEmpty()) {
                            "${selectedIds.size} مورد انتخاب شده"
                        } else {
                            "برای ارسال گزینشی تیک بزنید"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                retryPendingItems.take(8).forEach { rec ->
                    val isSelected = selectedIds.contains(rec.id)
                    val itemRemainingMs = if (BatchRetryPolicy.isAutoRetryEligible(rec) && rec.statusEnum == SmsStatus.RETRY) {
                        rec.nextRetryAtMs?.let { (it - nowMs).coerceAtLeast(0L) }
                    } else {
                        null
                    }
                    RetryPendingItemRow(
                        record = rec,
                        isSelected = isSelected,
                        remainingMs = itemRemainingMs,
                        isBatchRunning = batchRetryState.isBatchRunning,
                        onToggleSelect = {
                            selectedIds = if (isSelected) selectedIds - rec.id else selectedIds + rec.id
                        },
                        onRetrySingle = { onRetrySingleItem(rec) }
                    )
                }

                if (retryPendingItems.size > 8) {
                    Text(
                        text = "و ${retryPendingItems.size - 8} مورد دیگر در لیست پایین...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val targetBatchIds = if (selectedIds.isNotEmpty()) {
                    selectedIds.toList()
                } else {
                    retryPendingItems.map { it.id }
                }

                Button(
                    onClick = { onRetrySelectedBatch(targetBatchIds) },
                    enabled = !batchRetryState.isBatchRunning && targetBatchIds.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedIds.isNotEmpty()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color(0xFFF59E0B)
                        },
                        contentColor = if (selectedIds.isNotEmpty()) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            Color(0xFF0F172A)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("retry_selected_batch_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "همگام‌سازی مجدد دسته‌ای",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selectedIds.isNotEmpty()) {
                            "همگام‌سازی مجدد موارد انتخاب‌شده (${selectedIds.size})"
                        } else {
                            "ارسال مجدد دسته‌ای همه (${retryPendingItems.size})"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun RetryPendingItemRow(
    record: SmsRecord,
    isSelected: Boolean,
    remainingMs: Long?,
    isBatchRunning: Boolean,
    onToggleSelect: () -> Unit,
    onRetrySingle: () -> Unit
) {
    val isPermanent401 = record.statusEnum == SmsStatus.FAILED && record.httpCode == 401
    val exhaustedAuto = record.attempts >= BatchRetryPolicy.MAX_AUTO_RETRY_ATTEMPTS

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            1.dp,
            if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleSelect() }
            .testTag("retry_pending_item_${record.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.testTag("retry_pending_checkbox_${record.id}")
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = record.bankTitle,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (record.amountMinor != null) {
                        Text(
                            text = "• ${SmsParser.formatToman(record.amountMinor)} ت",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val statusTag = when {
                        isPermanent401 -> "خطای توکن ۴۰۱ • دستی"
                        exhaustedAuto -> "تلاش خودکار تمام شد • دستی"
                        remainingMs != null -> "تا تلاش خودکار: ${BatchRetryPolicy.formatCountdownMmSs(remainingMs)}"
                        else -> "در انتظار صف خودکار"
                    }
                    Icon(
                        imageVector = if (isPermanent401 || exhaustedAuto) Icons.Default.ErrorOutline else Icons.Default.Schedule,
                        contentDescription = null,
                        tint = if (isPermanent401 || exhaustedAuto) MaterialTheme.colorScheme.error else Color(0xFFF59E0B),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "$statusTag (تلاش #${record.attempts})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onRetrySingle,
                enabled = !isBatchRunning,
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("retry_pending_single_btn_${record.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "همگام‌سازی مجدد فوری",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
