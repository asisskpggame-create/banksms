package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LogStatus {
    QUEUED, // در صف انتظار ارسال اولیه
    RETRY,  // در صف تلاش مجدد (Backoff) پس از خطای شبکه، 5xx یا 429
    SENT,   // ارسال موفق (200 OK)
    FAILED  // خطای دائمی بدون تلاش مجدد خودکار (401 توکن نامعتبر، 404 درگاه خاموش، 400 متن نامعتبر)
}

@Entity(tableName = "sms_logs")
data class LogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String,
    val bankCode: String,
    val bankTitle: String,
    val body: String,
    val amountRial: Long?,
    val parseReason: String,
    val fingerprint: String,
    val status: String = LogStatus.QUEUED.name,
    val httpCode: Int? = null,
    val serverStatus: String? = null, // new | duplicate | ignored | test
    val approvedCount: Int? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val statusEnum: LogStatus
        get() = runCatching { LogStatus.valueOf(status) }.getOrDefault(LogStatus.QUEUED)
}
