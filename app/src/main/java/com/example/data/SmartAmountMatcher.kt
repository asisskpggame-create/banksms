package com.example.data

import kotlin.math.abs

/**
 * Result of comparing an incoming SMS deposit amount against invoices stored in the local database.
 */
sealed class SmartMatchDecision {
    /** Local invoice table has no waiting invoices yet; query server `/sms-pending`. */
    data object NoActiveInvoicesInDb : SmartMatchDecision()

    /** Single unambiguous invoice matches the exact deposit amount. */
    data class ExactMatch(
        val invoice: PendingInvoiceEntity
    ) : SmartMatchDecision()

    /** Multiple waiting invoices have the exact same amount (`AMBIGUOUS` / بررسی دستی). */
    data class AmbiguousSameAmount(
        val matchingInvoices: List<PendingInvoiceEntity>,
        val reasonFa: String
    ) : SmartMatchDecision()

    /**
     * Deposit has a close/suspicious relationship to an existing invoice (e.g. Rial/Toman 10x discrepancy,
     * small fee difference, or timestamp slightly before invoice creation) -> `MANUAL` («نیاز به بررسی دستی»).
     */
    data class NeedsManualReview(
        val candidateInvoice: PendingInvoiceEntity,
        val reasonFa: String
    ) : SmartMatchDecision()

    /**
     * Deposit amount does not match any waiting invoice in the database -> `UNKNOWN_DEPOSIT` («واریز ناشناس»).
     * Remains eligible for automatic promotion to `MATCHED` if the server sends an invoice inquiry within the window.
     */
    data class UnknownDeposit(
        val reasonFa: String
    ) : SmartMatchDecision()
}

/**
 * Result of checking the app's SMS log when the server inquiries about a specific invoice (`dueMinor`).
 */
sealed class InvoiceLogCheckResult {
    data class MatchedInLog(
        val depositRecord: SmsRecord,
        val deltaSeconds: Long
    ) : InvoiceLogCheckResult()

    data class AmbiguousInLog(
        val candidates: List<SmsRecord>,
        val reasonFa: String
    ) : InvoiceLogCheckResult()

    data object NotFoundInLog : InvoiceLogCheckResult()
}

/**
 * ماژول «تطبیق هوشمند مبلغ» (Smart Amount Matcher)
 *
 * ۱. مقایسهٔ آنی مبلغ پیامک واریز با فاکتورهای موجود در دیتابیس (`pending_invoices`):
 *    - در صورت تطابق دقیق با یک فاکتور -> آماده‌سازی برای تأیید خودکار (در بازهٔ ۱ دقیقه‌ای).
 *    - در صورت اختلاف جزئی مبلغ/واحد (تومان/ریال) یا تقدم زمانی -> ثبت وضعیت `MANUAL` («نیاز به بررسی دستی»).
 *    - در صورت عدم تطابق با هیچ‌یک از فاکتورهای موجود -> ثبت وضعیت `UNKNOWN_DEPOSIT` («واریز ناشناس»).
 *
 * ۲. پاسخ به استعلام فاکتور از سمت سرور (`verifyServerInvoiceInLogs`):
 *    - وقتی سرور برای یک فاکتور استعلام می‌فرستد («آیا این مبلغ واریز شده است؟»)، لاگ پیامک‌های دریافتی
 *      بررسی شده و در صورت وجود مبلغ منطبق، بلافاصله (معمولاً زیر ۱ دقیقه) تأیید و بسته می‌شود.
 *    - در صورت قطعی اینترنت، رکورد در وضعیت `RETRY` قرار گرفته و به محض وصل شدن مجدد اینترنت،
 *      استعلام سرور دوباره بررسی و رسید خودکار تأیید می‌شود.
 */
object SmartAmountMatcher {

    /** 60-second grace tolerance for slight phone/bank SMS timestamp skew around invoice creation. */
    private const val FAST_WINDOW_GRACE_MS = 60_000L

    fun evaluateDepositAgainstInvoices(
        amountMinor: Long,
        depositAtServerMs: Long,
        waitingInvoices: List<PendingInvoiceEntity>
    ): SmartMatchDecision {
        if (waitingInvoices.isEmpty()) {
            return SmartMatchDecision.NoActiveInvoicesInDb
        }

        // 1. Exact amount & valid timing (including 60s grace window)
        val exactValid = waitingInvoices.filter { inv ->
            inv.dueMinor == amountMinor && (depositAtServerMs + FAST_WINDOW_GRACE_MS) >= inv.createdMs
        }.sortedBy { it.createdMs }

        if (exactValid.size == 1) {
            return SmartMatchDecision.ExactMatch(exactValid.first())
        }
        if (exactValid.size > 1) {
            val ids = exactValid.joinToString("، ") { it.publicId }
            return SmartMatchDecision.AmbiguousSameAmount(
                matchingInvoices = exactValid,
                reasonFa = "نیاز به بررسی دستی: ${exactValid.size} فاکتور هم‌مبلغ ($ids) در دیتابیس منتظر پرداخت هستند."
            )
        }

        // 2. Check if an invoice with the exact same amount exists but was created significantly AFTER the deposit
        val sameAmountLaterInvoice = waitingInvoices.firstOrNull { it.dueMinor == amountMinor }
        if (sameAmountLaterInvoice != null) {
            return SmartMatchDecision.NeedsManualReview(
                candidateInvoice = sameAmountLaterInvoice,
                reasonFa = "نیاز به بررسی دستی: فاکتور ${sameAmountLaterInvoice.publicId} هم‌مبلغ است اما زمان پیامک قبل از ثبت فاکتور بوده است."
            )
        }

        // 3. Check for 10x Rial/Toman mistake by customer or near-amount (within 1% or 10,000 Rial)
        val tenXCandidate = waitingInvoices.firstOrNull { inv ->
            inv.dueMinor == amountMinor * 10L || amountMinor == inv.dueMinor * 10L
        }
        if (tenXCandidate != null) {
            return SmartMatchDecision.NeedsManualReview(
                candidateInvoice = tenXCandidate,
                reasonFa = "نیاز به بررسی دستی: احتمال اشتباه ریال/تومان برای فاکتور ${tenXCandidate.publicId} (مبلغ فاکتور: ${SmsParser.formatToman(tenXCandidate.dueMinor)} تومان)."
            )
        }

        val nearAmountCandidate = waitingInvoices.firstOrNull { inv ->
            val diff = abs(inv.dueMinor - amountMinor)
            val tolerance = maxOf(10_000L, inv.dueMinor / 100L) // 1% or 1,000 Toman
            diff in 1L..tolerance
        }
        if (nearAmountCandidate != null) {
            return SmartMatchDecision.NeedsManualReview(
                candidateInvoice = nearAmountCandidate,
                reasonFa = "نیاز به بررسی دستی: مبلغ واریزی به فاکتور ${nearAmountCandidate.publicId} (${SmsParser.formatToman(nearAmountCandidate.dueMinor)} تومان) بسیار نزدیک است ولی دقیقاً برابر نیست."
            )
        }

        // 4. Completely unrelated deposit amount -> mark as UNKNOWN_DEPOSIT («واریز ناشناس»)
        return SmartMatchDecision.UnknownDeposit(
            reasonFa = "واریز ناشناس: مبلغ ${SmsParser.formatToman(amountMinor)} تومان با هیچ‌یک از ${waitingInvoices.size} فاکتور منتظر در دیتابیس مطابقت ندارد."
        )
    }

    /**
     * Checks whether a specific server invoice request (`invoice.dueMinor`) is present in the app's SMS logs.
     */
    fun verifyServerInvoiceInLogs(
        invoice: PendingInvoiceEntity,
        activeDeposits: List<SmsRecord>,
        clockOffsetMs: Long = 0L
    ): InvoiceLogCheckResult {
        val candidates = activeDeposits.filter { rec ->
            val amt = rec.amountMinor ?: return@filter false
            val depositServerMs = rec.receivedMs + clockOffsetMs
            amt == invoice.dueMinor && (depositServerMs + FAST_WINDOW_GRACE_MS) >= invoice.createdMs
        }.sortedBy { abs((it.receivedMs + clockOffsetMs) - invoice.createdMs) }

        if (candidates.isEmpty()) {
            return InvoiceLogCheckResult.NotFoundInLog
        }
        if (candidates.size == 1) {
            val matched = candidates.first()
            val deltaSec = abs((matched.receivedMs + clockOffsetMs) - invoice.createdMs) / 1000L
            return InvoiceLogCheckResult.MatchedInLog(
                depositRecord = matched,
                deltaSeconds = deltaSec
            )
        }
        // Multiple deposits of the same amount exist; pick the oldest valid one per Section 4 of spec
        val oldestFirst = candidates.minByOrNull { it.receivedMs }!!
        val deltaSec = abs((oldestFirst.receivedMs + clockOffsetMs) - invoice.createdMs) / 1000L
        return InvoiceLogCheckResult.MatchedInLog(
            depositRecord = oldestFirst,
            deltaSeconds = deltaSec
        )
    }
}
