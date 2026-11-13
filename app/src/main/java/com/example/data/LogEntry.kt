package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Statuses per Section 6 of `APP_SIDE_APPROVAL_SPEC.md`:
 * `NEW | CLAIMED | IGNORED | MATCHED | MANUAL | AMBIGUOUS | EXPIRED | RETRY | FAILED`
 */
enum class SmsStatus {
    NEW,        // واریز معتبر جدید، منتظر تطبیق با سفارش‌های منتظر (GET /sms-pending)
    CLAIMED,    // قفل‌شده قبل از ارسال درخواست شبکه (Claim-first safety)
    IGNORED,    // پیامک غیر واریزی (برداشت، مانده، بدون مبلغ و...)
    MATCHED,    // تطبیق و تأیید موفق در سرور (status = approved | duplicate)
    MANUAL,     // تطبیق یافت شد ولی نیازمند تأیید دستی کاربر (تأیید خودکار خاموش، بالاتر از سقف، یا 409 سرور)
    AMBIGUOUS,  // چند سفارش منتظر با مبلغ یکسان وجود دارد (I > D) — نیازمند بررسی دستی
    EXPIRED,    // منقضی‌شده پس از گذشت match_window_hours
    RETRY,      // خطای موقت شبکه / 5xx / 429 — در صف تلاش مجدد با Backoff
    FAILED      // خطای دائمی (401 توکن/امضا نامعتبر)
}

@Entity(
    tableName = "sms_records",
    indices = [Index(value = ["fingerprint"], unique = true)]
)
data class SmsRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fingerprint: String,       // SHA-256 unique fingerprint
    val bank: String,              // originatingAddress (max 32 chars)
    val bankTitle: String,         // نام فارسی بانک برای نمایش در UI
    val body: String,              // متن خام و دست‌نخوردهٔ پیامک
    val normalized: String,        // متن نرمال‌شده
    val amountMinor: Long?,        // مبلغ به ریال (null اگر واریز نباشد)
    val reason: String,            // دلیل رد در پارسر یا دلیل وضعیت
    val status: String = SmsStatus.NEW.name,
    val matchedKind: String? = null, // "order" | "topup"
    val matchedId: String? = null,   // public_id (مثلاً ORD123)
    val receivedMs: Long = System.currentTimeMillis(), // زمان PDU پیامک
    val storedMs: Long = System.currentTimeMillis(),   // زمان ذخیره در گوشی
    val decidedMs: Long? = null,
    val attempts: Int = 0,
    val httpCode: Int? = null,
    val lastError: String? = null
) {
    val statusEnum: SmsStatus
        get() = runCatching { SmsStatus.valueOf(status) }.getOrDefault(SmsStatus.NEW)

    fun matchedLabelFa(): String? {
        val id = matchedId ?: return null
        return when (matchedKind) {
            "topup" -> "شارژ کیف پول $id"
            "sms-in" -> "ارسال مستقیم (/sms-in): $id"
            else -> "سفارش $id"
        }
    }
}
