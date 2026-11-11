package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {

    @Query("SELECT * FROM sms_records ORDER BY receivedMs DESC, id DESC")
    fun observeAllLogs(): Flow<List<SmsRecord>>

    @Query("SELECT * FROM sms_records WHERE status IN ('NEW', 'RETRY') AND amountMinor IS NOT NULL ORDER BY receivedMs ASC, id ASC")
    suspend fun getActiveDepositsForMatching(): List<SmsRecord>

    @Query("SELECT * FROM sms_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SmsRecord?

    @Query("SELECT * FROM sms_records WHERE fingerprint = :fp LIMIT 1")
    suspend fun getByFingerprint(fp: String): SmsRecord?

    @Query("SELECT COUNT(*) FROM sms_records WHERE status IN ('NEW', 'RETRY', 'CLAIMED')")
    fun observeQueueCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sms_records WHERE status = 'MATCHED'")
    fun observeMatchedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sms_records WHERE status IN ('MANUAL', 'AMBIGUOUS', 'FAILED')")
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
     * Claim-first atomic update before network call (`NEW/RETRY/MANUAL -> CLAIMED`).
     */
    @Query("UPDATE sms_records SET status = 'CLAIMED', matchedKind = :kind, matchedId = :publicId, attempts = attempts + 1 WHERE id = :id AND status IN ('NEW', 'RETRY', 'MANUAL')")
    suspend fun claimForApproval(id: Long, kind: String, publicId: String): Int

    /**
     * Expire `NEW` deposits older than `cutoffMs` (`now - match_window_hours`).
     */
    @Query("UPDATE sms_records SET status = 'EXPIRED', decidedMs = :nowMs, reason = 'expired_window' WHERE status = 'NEW' AND receivedMs < :cutoffMs")
    suspend fun expireOldNewDeposits(cutoffMs: Long, nowMs: Long): Int

    /**
     * Delete records older than 30 days (`KEEP_DAYS = 30`).
     */
    @Query("DELETE FROM sms_records WHERE storedMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int

    @Query("DELETE FROM sms_records")
    suspend fun clearAllLogs()
}
