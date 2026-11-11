package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {

    @Query("SELECT * FROM sms_logs ORDER BY createdAt DESC")
    fun observeAllLogs(): Flow<List<LogEntry>>

    @Query("SELECT * FROM sms_logs WHERE status IN ('QUEUED', 'RETRY') ORDER BY createdAt ASC")
    suspend fun getPendingLogs(): List<LogEntry>

    @Query("SELECT * FROM sms_logs WHERE id = :id LIMIT 1")
    suspend fun getLogById(id: Long): LogEntry?

    @Query("SELECT COUNT(*) FROM sms_logs WHERE status IN ('QUEUED', 'RETRY')")
    fun observeQueueCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sms_logs WHERE status = 'SENT'")
    fun observeSentCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sms_logs WHERE status = 'FAILED'")
    fun observeFailedCount(): Flow<Int>

    @Query("SELECT * FROM sms_logs WHERE status = 'SENT' ORDER BY updatedAt DESC LIMIT 1")
    fun observeLastSentLog(): Flow<LogEntry?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(entry: LogEntry): Long

    @Update
    suspend fun updateLog(entry: LogEntry)

    @Query("DELETE FROM sms_logs")
    suspend fun clearAllLogs()

    @Query("DELETE FROM sms_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)
}
