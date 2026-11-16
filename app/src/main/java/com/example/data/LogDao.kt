package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {

    @Query("SELECT * FROM sms_records WHERE reason != 'promo' ORDER BY receivedMs DESC, id DESC")
    fun observeAllLogs(): Flow<List<SmsRecord>>

    @Query("DELETE FROM sms_records WHERE reason = 'promo'")
    suspend fun deletePromoLogs(): Int

    @Query("SELECT * FROM sms_records WHERE status IN ('NEW', 'RETRY', 'UNKNOWN_DEPOSIT') AND amountMinor IS NOT NULL ORDER BY receivedMs ASC, id ASC")
    suspend fun getActiveDepositsForMatching(): List<SmsRecord>

    @Query("SELECT * FROM sms_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SmsRecord?

    @Query("SELECT * FROM sms_records WHERE fingerprint = :fp LIMIT 1")
    suspend fun getByFingerprint(fp: String): SmsRecord?

    @Query("SELECT COUNT(*) FROM sms_records WHERE status IN ('NEW', 'RETRY', 'CLAIMED')")
    fun observeQueueCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sms_records WHERE status = 'MATCHED'")
    fun observeMatchedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sms_records WHERE status IN ('MANUAL', 'AMBIGUOUS', 'UNKNOWN_DEPOSIT', 'FAILED')")
    fun observeActionNeededCount(): Flow<Int>

    @Query("SELECT * FROM sms_records WHERE status = 'MATCHED' ORDER BY decidedMs DESC, id DESC LIMIT 1")
    fun observeLastMatchedLog(): Flow<SmsRecord?>

    /**
     * Unique index on `fingerprint`: OnConflictStrategy.IGNORE returns -1L when duplicate,
     * matching Section 6 ("unique(fingerprint)؛ درج تکراری = duplicate و بی‌صدا نادیده").
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreDuplicate(record: SmsRecord): Long

    @Update
    suspend fun update(record: SmsRecord)

    /**
     * Claim-first atomic update before network call (`NEW/RETRY/MANUAL/UNKNOWN_DEPOSIT -> CLAIMED`).
     */
    @Query("UPDATE sms_records SET status = 'CLAIMED', matchedKind = :kind, matchedId = :publicId, attempts = attempts + 1 WHERE id = :id AND status IN ('NEW', 'RETRY', 'MANUAL', 'UNKNOWN_DEPOSIT')")
    suspend fun claimForApproval(id: Long, kind: String, publicId: String): Int

    /**
     * A row can only be CLAIMED while the worker mutex is held, so any CLAIMED row seen at the start of a sweep was left
     * behind by a crash / killed process mid-request. Put it back in the queue (the server answers `duplicate` if the
     * first request actually went through).
     */
    @Query("UPDATE sms_records SET status = 'RETRY' WHERE status = 'CLAIMED'")
    suspend fun releaseStaleClaims(): Int

    /**
     * Expire `NEW` and `UNKNOWN_DEPOSIT` deposits older than `cutoffMs` (`now - match_window_hours`).
     */
    @Query("UPDATE sms_records SET status = 'EXPIRED', decidedMs = :nowMs, reason = 'expired_window' WHERE status IN ('NEW', 'UNKNOWN_DEPOSIT') AND receivedMs < :cutoffMs")
    suspend fun expireOldNewDeposits(cutoffMs: Long, nowMs: Long): Int

    /**
     * Delete records older than retention cutoff (`storedMs < cutoffMs`).
     */
    @Query("DELETE FROM sms_records WHERE storedMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int

    @Query("SELECT COUNT(*) FROM sms_records WHERE storedMs < :cutoffMs")
    suspend fun countLogsOlderThan(cutoffMs: Long): Int

    @Query("UPDATE sms_records SET status = 'RETRY', nextRetryAtMs = :nowMs, lastError = NULL WHERE status IN ('FAILED', 'RETRY', 'UNKNOWN_DEPOSIT') AND amountMinor IS NOT NULL AND reason != 'test_deposit_not_sent'")
    suspend fun resetFailedAndErrorLogsForRetry(nowMs: Long = System.currentTimeMillis()): Int

    @Query("SELECT * FROM sms_records WHERE status IN ('FAILED', 'RETRY') AND amountMinor IS NOT NULL AND reason != 'test_deposit_not_sent' ORDER BY COALESCE(nextRetryAtMs, 0) ASC, receivedMs ASC, id ASC")
    suspend fun getRetryPendingRecords(): List<SmsRecord>

    @Query("SELECT * FROM sms_records WHERE id IN (:ids) AND amountMinor IS NOT NULL AND reason != 'test_deposit_not_sent' ORDER BY receivedMs ASC, id ASC")
    suspend fun getRecordsByIds(ids: List<Long>): List<SmsRecord>

    @Query("UPDATE sms_records SET status = 'RETRY', nextRetryAtMs = :nowMs, lastError = NULL WHERE id IN (:ids) AND amountMinor IS NOT NULL AND reason != 'test_deposit_not_sent'")
    suspend fun markIdsForImmediateBatchRetry(ids: List<Long>, nowMs: Long = System.currentTimeMillis()): Int

    @Query("SELECT COUNT(*) FROM sms_records WHERE status IN ('FAILED', 'RETRY') AND amountMinor IS NOT NULL AND reason != 'test_deposit_not_sent'")
    suspend fun getRecoveryCandidateCount(): Int

    @Query(
        """
        UPDATE sms_records
        SET status = 'RETRY',
            nextRetryAtMs = :nowMs,
            lastError = NULL,
            attempts = CASE WHEN attempts >= 8 THEN 7 ELSE attempts END
        WHERE amountMinor IS NOT NULL
          AND reason != 'test_deposit_not_sent'
          AND (
            status = 'RETRY'
            OR (status = 'FAILED' AND (:includeAuth401 = 1 OR httpCode IS NULL OR httpCode != 401))
          )
        """
    )
    suspend fun prepareErrorRecordsForRecoverySync(includeAuth401: Boolean, nowMs: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM sms_records")
    suspend fun clearAllLogs()

    // --- Local Invoice Database (`pending_invoices`) for Smart Amount Matching ---

    @Query("SELECT * FROM pending_invoices ORDER BY COALESCE(matchedAtMs, syncedAtMs, createdMs) DESC, id DESC")
    fun observeAllInvoices(): Flow<List<PendingInvoiceEntity>>

    @Query("SELECT * FROM pending_invoices WHERE status = 'WAITING' ORDER BY createdMs ASC, id ASC")
    suspend fun getWaitingInvoices(): List<PendingInvoiceEntity>

    @Query("SELECT * FROM pending_invoices WHERE publicId = :publicId LIMIT 1")
    suspend fun getInvoiceByPublicId(publicId: String): PendingInvoiceEntity?

    @Query("SELECT * FROM pending_invoices WHERE status = 'MATCHED' ORDER BY COALESCE(matchedAtMs, syncedAtMs, createdMs) DESC, id DESC")
    fun observeApprovedInvoices(): Flow<List<PendingInvoiceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInvoice(invoice: PendingInvoiceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInvoices(invoices: List<PendingInvoiceEntity>)

    /**
     * Upserts waiting invoices received from server without overwriting already-MATCHED invoices.
     */
    @Transaction
    suspend fun upsertServerWaitingInvoices(invoices: List<PendingInvoiceEntity>) {
        if (invoices.isEmpty()) return
        val toInsert = invoices.filter { incoming ->
            val existing = getInvoiceByPublicId(incoming.publicId)
            existing == null || existing.status != "MATCHED"
        }
        if (toInsert.isNotEmpty()) {
            upsertInvoices(toInsert)
        }
    }

    @Query("UPDATE pending_invoices SET status = 'MATCHED', matchedSmsId = :smsId WHERE publicId = :publicId")
    suspend fun markInvoiceMatched(publicId: String, smsId: Long)

    /**
     * Persists an approved invoice with its exact price (`dueMinor`), bank name, SMS link, and approval time.
     * Inserts a new `MATCHED` row if the invoice wasn't already pre-registered in `pending_invoices`.
     */
    @Transaction
    suspend fun recordApprovedInvoice(
        publicId: String,
        kind: String,
        dueMinor: Long,
        smsId: Long?,
        bankTitle: String?,
        matchedAtMs: Long = System.currentTimeMillis()
    ) {
        val cleanId = publicId.trim()
        if (cleanId.isEmpty()) return
        val existing = getInvoiceByPublicId(cleanId)
        val cleanKind = kind.trim().ifEmpty { existing?.kind ?: "order" }
        val resolvedDueMinor = when {
            dueMinor > 0L -> dueMinor
            existing != null && existing.dueMinor > 0L -> existing.dueMinor
            else -> 0L
        }
        val cleanBank = bankTitle?.trim()?.takeIf { it.isNotEmpty() } ?: existing?.bankTitle
        if (existing != null) {
            upsertInvoice(
                existing.copy(
                    kind = cleanKind,
                    dueMinor = resolvedDueMinor,
                    status = "MATCHED",
                    matchedSmsId = smsId ?: existing.matchedSmsId,
                    matchedAtMs = matchedAtMs,
                    syncedAtMs = matchedAtMs,
                    bankTitle = cleanBank
                )
            )
        } else {
            upsertInvoice(
                PendingInvoiceEntity(
                    publicId = cleanId,
                    kind = cleanKind,
                    dueMinor = resolvedDueMinor,
                    createdMs = matchedAtMs,
                    syncedAtMs = matchedAtMs,
                    early = false,
                    status = "MATCHED",
                    matchedSmsId = smsId,
                    matchedAtMs = matchedAtMs,
                    bankTitle = cleanBank
                )
            )
        }
    }

    @Query("DELETE FROM pending_invoices WHERE publicId = :publicId")
    suspend fun deleteInvoiceByPublicId(publicId: String)

    @Query("DELETE FROM pending_invoices WHERE status = 'WAITING' AND publicId NOT IN (:activePublicIds)")
    suspend fun pruneResolvedServerInvoices(activePublicIds: List<String>)

    @Query("DELETE FROM pending_invoices")
    suspend fun clearAllInvoices()

    // --- 7-Day Auto-Retry Connectivity Health Telemetry (`retry_attempt_events`) ---

    @Insert
    suspend fun insertRetryAttemptEvent(event: RetryAttemptEventEntity): Long

    @Insert
    suspend fun insertRetryAttemptEvents(events: List<RetryAttemptEventEntity>)

    @Query("SELECT * FROM retry_attempt_events WHERE attemptedAtMs >= :sinceMs ORDER BY attemptedAtMs ASC, id ASC")
    fun observeRecentRetryEvents(sinceMs: Long): Flow<List<RetryAttemptEventEntity>>

    @Query("SELECT * FROM retry_attempt_events WHERE attemptedAtMs >= :sinceMs ORDER BY attemptedAtMs ASC, id ASC")
    suspend fun getRecentRetryEvents(sinceMs: Long): List<RetryAttemptEventEntity>

    @Query("DELETE FROM retry_attempt_events WHERE attemptedAtMs < :cutoffMs")
    suspend fun deleteOldRetryEvents(cutoffMs: Long): Int

    @Query("DELETE FROM retry_attempt_events")
    suspend fun clearAllRetryEvents()
}
