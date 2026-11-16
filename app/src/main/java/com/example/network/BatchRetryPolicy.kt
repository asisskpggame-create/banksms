package com.example.network

import com.example.data.SmsRecord
import com.example.data.SmsStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class BatchRetryState(
    val isBatchRunning: Boolean = false,
    val lastBatchRunAtMs: Long? = null,
    val lastBatchProcessedCount: Int = 0,
    val lastBatchSucceededCount: Int = 0,
    val lastBatchFailedCount: Int = 0,
    val maxBatchSize: Int = BatchRetryPolicy.DEFAULT_BATCH_SIZE,
    val nextScheduledWorkAtMs: Long? = null
)

object BatchRetryPolicy {
    const val DEFAULT_BATCH_SIZE = 10
    const val INITIAL_BACKOFF_MS = 15_000L
    const val MAX_BACKOFF_MS = 300_000L // 5 minutes cap
    const val MAX_AUTO_RETRY_ATTEMPTS = 8

    /**
     * Exponential backoff schedule:
     * Attempt 1 -> 15s, Attempt 2 -> 30s, Attempt 3 -> 60s, Attempt 4 -> 120s,
     * Attempt 5 -> 240s, Attempt 6+ -> 300s (capped at 5 min).
     */
    fun backoffMsForAttempt(attempts: Int): Long {
        val exp = (attempts.coerceAtLeast(1) - 1).coerceAtMost(5)
        val raw = INITIAL_BACKOFF_MS shl exp
        return raw.coerceIn(INITIAL_BACKOFF_MS, MAX_BACKOFF_MS)
    }

    fun computeNextRetryAtMs(attempts: Int, nowMs: Long = System.currentTimeMillis()): Long? {
        if (attempts >= MAX_AUTO_RETRY_ATTEMPTS) return null
        return nowMs + backoffMsForAttempt(attempts.coerceAtLeast(1))
    }

    fun isEligibleForRetryPending(record: SmsRecord): Boolean {
        if (record.amountMinor == null || record.reason == "test_deposit_not_sent") return false
        return record.statusEnum == SmsStatus.FAILED || record.statusEnum == SmsStatus.RETRY
    }

    fun isAutoRetryEligible(record: SmsRecord): Boolean {
        if (!isEligibleForRetryPending(record)) return false
        if (record.statusEnum == SmsStatus.FAILED && record.httpCode == 401) return false
        return record.attempts < MAX_AUTO_RETRY_ATTEMPTS
    }

    fun earliestAutoRetryAtMs(records: List<SmsRecord>): Long? {
        return records.asSequence()
            .filter { isAutoRetryEligible(it) && it.statusEnum == SmsStatus.RETRY }
            .mapNotNull { it.nextRetryAtMs }
            .minOrNull()
    }

    fun formatCountdownMmSs(remainingMs: Long): String {
        val totalSeconds = (remainingMs.coerceAtLeast(0L) + 999L) / 1000L
        val mins = totalSeconds / 60L
        val secs = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

object BatchRetryTracker {
    private val _stateFlow = MutableStateFlow(BatchRetryState())
    val stateFlow: StateFlow<BatchRetryState> = _stateFlow.asStateFlow()

    fun onBatchStarted() {
        _stateFlow.value = _stateFlow.value.copy(isBatchRunning = true)
    }

    fun onBatchCompleted(
        processedCount: Int,
        succeededCount: Int,
        failedCount: Int,
        nextScheduledWorkAtMs: Long?,
        nowMs: Long = System.currentTimeMillis()
    ) {
        _stateFlow.value = _stateFlow.value.copy(
            isBatchRunning = false,
            lastBatchRunAtMs = nowMs,
            lastBatchProcessedCount = processedCount,
            lastBatchSucceededCount = succeededCount,
            lastBatchFailedCount = failedCount,
            nextScheduledWorkAtMs = nextScheduledWorkAtMs
        )
    }

    fun updateNextScheduledWork(nextScheduledWorkAtMs: Long?) {
        _stateFlow.value = _stateFlow.value.copy(
            nextScheduledWorkAtMs = nextScheduledWorkAtMs
        )
    }

    fun resetForTests() {
        _stateFlow.value = BatchRetryState()
    }
}
