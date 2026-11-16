package com.example.network

import com.example.data.RetryAttemptEventEntity
import com.example.data.SmsRecord
import com.example.data.SmsStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class ConnectivityHealthTier(val labelFa: String) {
    NO_DATA("بدون سابقهٔ خطا"),
    EXCELLENT("پایدار و عالی"),
    GOOD("پایداری مناسب"),
    DEGRADED("نوسان اتصال"),
    CRITICAL("اختلال اتصال به سرور")
}

data class DailyRetryHealthPoint(
    val dayStartMs: Long,
    val dayLabel: String,
    val shortAxisLabel: String,
    val totalAttempts: Int,
    val succeededAttempts: Int,
    val failedAttempts: Int,
    val successRatePercent: Float?
)

data class RetryHealthSummary(
    val days: List<DailyRetryHealthPoint>,
    val totalAttempts7d: Int,
    val succeededAttempts7d: Int,
    val failedAttempts7d: Int,
    val overallSuccessRatePercent: Float?,
    val trendDeltaPercent: Float?,
    val healthTier: ConnectivityHealthTier
)

object RetryHealthAnalytics {
    private const val DAY_MS = 86_400_000L

    fun compute7DaySummary(
        logs: List<SmsRecord>,
        events: List<RetryAttemptEventEntity>,
        nowMs: Long = System.currentTimeMillis()
    ): RetryHealthSummary {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStartMs = cal.timeInMillis
        val windowStartMs = todayStartMs - (6L * DAY_MS)
        val windowEndMs = todayStartMs + DAY_MS

        val dayStarts = (0..6).map { offset -> windowStartMs + (offset * DAY_MS) }
        val dayFmt = SimpleDateFormat("MM/dd", Locale.US)

        // Track which SmsRecord IDs already have explicit retry event rows
        val recordedSmsIds = events.asSequence().map { it.smsRecordId }.toHashSet()

        data class DayCounter(var total: Int = 0, var succeeded: Int = 0, var failed: Int = 0)
        val counters = Array(7) { DayCounter() }

        fun bucketIndexForTimestamp(tsMs: Long): Int? {
            if (tsMs < windowStartMs || tsMs >= windowEndMs) return null
            val idx = ((tsMs - windowStartMs) / DAY_MS).toInt()
            return idx.takeIf { it in 0..6 }
        }

        for (ev in events) {
            val idx = bucketIndexForTimestamp(ev.attemptedAtMs) ?: continue
            counters[idx].total++
            if (ev.succeeded) {
                counters[idx].succeeded++
            } else {
                counters[idx].failed++
            }
        }

        // Fallback telemetry for legacy records that underwent retries before event logging existed
        for (rec in logs) {
            if (rec.attempts <= 0 || rec.id in recordedSmsIds) continue
            if (rec.amountMinor == null || rec.reason == "test_deposit_not_sent") continue
            val ts = rec.decidedMs ?: rec.storedMs
            val idx = bucketIndexForTimestamp(ts) ?: continue
            val succeeded = rec.statusEnum == SmsStatus.MATCHED
            val failedCount = if (succeeded) (rec.attempts - 1).coerceAtLeast(0) else rec.attempts.coerceAtLeast(1)
            val totalCount = failedCount + if (succeeded) 1 else 0
            counters[idx].total += totalCount
            if (succeeded) counters[idx].succeeded += 1
            counters[idx].failed += failedCount
        }

        val dailyPoints = dayStarts.mapIndexed { idx, dayStart ->
            val counter = counters[idx]
            val rate = if (counter.total > 0) {
                (counter.succeeded * 100f) / counter.total.toFloat()
            } else {
                null
            }
            val fullLabel = when (idx) {
                6 -> "امروز"
                5 -> "دیروز"
                else -> dayFmt.format(Date(dayStart))
            }
            val axisLabel = when (idx) {
                6 -> "امروز"
                else -> dayFmt.format(Date(dayStart))
            }
            DailyRetryHealthPoint(
                dayStartMs = dayStart,
                dayLabel = fullLabel,
                shortAxisLabel = axisLabel,
                totalAttempts = counter.total,
                succeededAttempts = counter.succeeded,
                failedAttempts = counter.failed,
                successRatePercent = rate
            )
        }

        val total7d = dailyPoints.sumOf { it.totalAttempts }
        val succeeded7d = dailyPoints.sumOf { it.succeededAttempts }
        val failed7d = dailyPoints.sumOf { it.failedAttempts }
        val overallRate = if (total7d > 0) (succeeded7d * 100f) / total7d.toFloat() else null

        val earlyTotal = dailyPoints.take(4).sumOf { it.totalAttempts }
        val earlySucceeded = dailyPoints.take(4).sumOf { it.succeededAttempts }
        val recentTotal = dailyPoints.drop(4).sumOf { it.totalAttempts }
        val recentSucceeded = dailyPoints.drop(4).sumOf { it.succeededAttempts }
        val trendDelta = if (earlyTotal > 0 && recentTotal > 0) {
            val earlyRate = (earlySucceeded * 100f) / earlyTotal.toFloat()
            val recentRate = (recentSucceeded * 100f) / recentTotal.toFloat()
            recentRate - earlyRate
        } else {
            null
        }

        val tier = when {
            overallRate == null -> ConnectivityHealthTier.NO_DATA
            overallRate >= 90f -> ConnectivityHealthTier.EXCELLENT
            overallRate >= 70f -> ConnectivityHealthTier.GOOD
            overallRate >= 40f -> ConnectivityHealthTier.DEGRADED
            else -> ConnectivityHealthTier.CRITICAL
        }

        return RetryHealthSummary(
            days = dailyPoints,
            totalAttempts7d = total7d,
            succeededAttempts7d = succeeded7d,
            failedAttempts7d = failed7d,
            overallSuccessRatePercent = overallRate,
            trendDeltaPercent = trendDelta,
            healthTier = tier
        )
    }

    fun insightMessageFa(summary: RetryHealthSummary): String {
        val rateInt = summary.overallSuccessRatePercent?.roundToInt()
        return when (summary.healthTier) {
            ConnectivityHealthTier.NO_DATA ->
                "در ۷ روز گذشته هیچ تلاش مجددی ثبت نشده است (تمام ارتباطات بدون نیاز به بازارسال یا هنوز خطایی رخ نداده است)."
            ConnectivityHealthTier.EXCELLENT ->
                "پایداری اتصال بسیار خوب است؛ $rateInt٪ از تلاش‌های مجدد خودکار (${summary.succeededAttempts7d} از ${summary.totalAttempts7d}) با موفقیت تحویل شدند."
            ConnectivityHealthTier.GOOD ->
                "وضعیت اتصال قابل قبول است ($rateInt٪ موفقیت در بازارسال)؛ اختلالات مقطعی به موقع جبران شده‌اند."
            ConnectivityHealthTier.DEGRADED ->
                "نوسان شبکه یا سرور مشاهده شده است (نرخ موفقیت $rateInt٪)؛ وضعیت اینترنت و پاسخ‌گویی درگاه را بررسی کنید."
            ConnectivityHealthTier.CRITICAL ->
                "نرخ موفقیت تلاش‌های مجدد پایین است ($rateInt٪)؛ اتصال سرور، توکن درگاه یا مسیر پنل را بررسی نمایید."
        }
    }
}
