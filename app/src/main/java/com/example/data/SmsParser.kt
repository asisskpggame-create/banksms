package com.example.data

import java.security.MessageDigest
import java.text.NumberFormat
import java.util.Locale

data class BankInfo(
    val code: String,
    val titleFa: String,
    val senderKeywords: List<String>,
    val bodyKeywords: List<String>
)

/** Exact result structure matching `reference/sms_parse.py` and Section 3 of `APP_SIDE_APPROVAL_SPEC.md`. */
data class Parsed(
    val amountMinor: Long?, // ریال؛ null یعنی یک واریز مشخص با یک مبلغ نیست
    val reason: String      // "" در صورت موفقیت، یا کد علت رد شدن
)

data class ParsedSms(
    val amountMinor: Long?,
    val reason: String,
    val normalizedText: String,
    val fingerprint: String,
    val bankCode: String,
    val bankTitleFa: String
)

/**
 * Exact Kotlin port of `reference/sms_parse.py` per Section 3 of `APP_SIDE_APPROVAL_SPEC.md`.
 * Uses Unicode character classes `[\p{L}\p{N}_]` instead of `(?U)\w` so it works identically
 * on both Android ART's ICU regex engine (which rejects `(?U)`) and desktop JVM JUnit tests.
 */
object SmsParser {

    const val TEST_MARK = "تست اتصال"
    const val MAX_SMS_LENGTH = 4000
    private const val MAX_TEXT = 2000

    private val NOISE = listOf(
        Regex("""(?<![\p{L}\p{N}_])\d{2,4}[/\-.]\d{1,2}[/\-.]\d{1,4}(?![\p{L}\p{N}_])"""), // تاریخ
        Regex("""(?<![\p{L}\p{N}_])\d{1,2}:\d{2}(?::\d{2})?(?![\p{L}\p{N}_])"""),           // ساعت
        Regex("""(?<![\p{L}\p{N}_])\d{4}[\s\-]?\d{4}[\s\-]?\d{4}[\s\-]?\d{4}(?![\p{L}\p{N}_])"""), // کارت ۱۶ رقمی
        Regex("""\d{4,6}\s?[*xX•#]{2,}\s?\d{2,4}"""),                                        // 603799******1234
        Regex("""[*xX•#]{2,}\s?\d{2,6}"""),                                                  // ***1234
        Regex("""(?:\+98|0098|98)?9\d{9}(?![\p{L}\p{N}_])"""),                               // موبایل
        Regex("""(?<![\p{L}\p{N}_])0\d{8,}(?![\p{L}\p{N}_])""")                              // شمارهٔ حساب
    )

    // The unit may touch the number («50,000,000ریال»). `(?!\d|,\d)` stops «50,000,000ریال» from backtracking to «50,000».
    // MUST stay identical to `_NUM` in the bot's app/services/sms_parse.py (the server re-parses every SMS).
    private val NUM = Regex(
        """(?<![A-Za-z0-9_.])(?<!\d,)([+\-])?\s?(\d{1,3}(?:,\d{3})+|[1-9]\d{3,11})(?!\d|,\d)\s?([+\-])?(?![A-Za-z0-9_])"""
    )

    private val MOBILE_SENDER = Regex("""^(?:\+?98|0098|0)?9\d{9}$""")

    /** A bank never texts from a mobile number; an SMS from one is never treated as a deposit. */
    fun isPersonalSender(sender: String?): Boolean =
        MOBILE_SENDER.matches(normalize(sender).replace(Regex("[\\s\\-]"), ""))

    private val CREDIT = listOf(
        "واریز", "واريز", "دریافت", "ایداع", "إيداع", "افزایش", "بستانکار", "deposit", "credit", "variz"
    )
    private val DEBIT = listOf(
        "برداشت", "پرداخت", "خرید", "کسر", "انتقال از", "بدهکار", "پایا", "ساتنا", "قبض",
        "withdraw", "debit", "purchase"
    )
    private val BALANCE = listOf("مانده", "موجودی", "باقیمانده", "balance", "bal")
    private val FEE = listOf("کارمزد", "wage", "fee")
    // Marketing / link texts are never a deposit (identical to PROMO_WORDS in the bot's sms_parse.py).
    private val PROMO = listOf("هدیه", "جایزه", "تخفیف", "قرعه", "لینک", "http", "www.")

    private val INVISIBLE = "\u200c\u200d\u200e\u200f\u202a\u202b\u202c\u202d\u202e\u00a0\ufeff".toSet()

    val KNOWN_BANKS: List<BankInfo> = listOf(
        BankInfo("mellat", "بانک ملت", listOf("mellat", "98200033", "200033", "100033", "300033"), listOf("بانک ملت", "ملت")),
        BankInfo("melli", "بانک ملی", listOf("melli", "bmi", "98600060", "600060", "100060"), listOf("بانک ملی", "بانك ملي", "ملی ایران")),
        BankInfo("saderat", "بانک صادرات", listOf("saderat", "bsi", "98200069", "200069", "600069"), listOf("صادرات", "سپهر")),
        BankInfo("tejarat", "بانک تجارت", listOf("tejarat", "98200070", "200070", "100070"), listOf("بانک تجارت", "تجارت")),
        BankInfo("sepah", "بانک سپه", listOf("sepah", "98200022", "200022", "300022"), listOf("بانک سپه", "سپه")),
        BankInfo("pasargad", "بانک پاسارگاد", listOf("pasargad", "bpi", "982000888", "2000888", "500040"), listOf("پاسارگاد", "ویپاد", "wepod")),
        BankInfo("parsian", "بانک پارسیان", listOf("parsian", "pec", "98200040", "200040"), listOf("پارسیان")),
        BankInfo("saman", "بانک سامان", listOf("saman", "sb24", "9820000", "20000", "98200084210"), listOf("بانک سامان", "سامان")),
        BankInfo("blu", "بلوبانک", listOf("blu", "blubank", "98200084210"), listOf("بلوبانک", "بلو بانک", "حساب بلو", "بلو")),
        BankInfo("enbank", "بانک اقتصاد نوین", listOf("enbank", "98200050", "200050"), listOf("اقتصاد نوین", "اقتصادنوین")),
        BankInfo("keshavarzi", "بانک کشاورزی", listOf("keshavarzi", "bki", "98200090", "200090", "500022"), listOf("کشاورزی", "بانک کشاورزی")),
        BankInfo("maskan", "بانک مسکن", listOf("maskan", "98200014", "200014", "300014"), listOf("بانک مسکن", "مسکن")),
        BankInfo("resalat", "بانک رسالت", listOf("resalat", "rqbank", "982000069", "2000069"), listOf("رسالت", "قرض الحسنه رسالت")),
        BankInfo("mehr", "بانک مهر ایران", listOf("mehr", "qmb", "982000444", "2000444"), listOf("مهر ایران", "قرض الحسنه مهر")),
        BankInfo("ayandeh", "بانک آینده", listOf("ayandeh", "ba24", "98200049", "200049"), listOf("بانک آینده", "آینده")),
        BankInfo("shahr", "بانک شهر", listOf("shahr", "98200086", "200086"), listOf("بانک شهر")),
        BankInfo("sina", "بانک سینا", listOf("sina", "982000727", "2000727"), listOf("بانک سینا", "سینا")),
        BankInfo("refah", "بانک رفاه", listOf("refah", "98200000", "200000"), listOf("بانک رفاه", "رفاه کارگران")),
        BankInfo("gardeshgari", "بانک گردشگری", listOf("gardeshgari", "tourism", "982000181", "2000181"), listOf("گردشگری")),
        BankInfo("karafarin", "بانک کارآفرین", listOf("karafarin", "98200087", "200087"), listOf("کارآفرین")),
        BankInfo("khavarmianeh", "بانک خاورمیانه", listOf("mebank", "khavarmianeh"), listOf("خاورمیانه")),
        BankInfo("postbank", "پست بانک", listOf("postbank", "98200084"), listOf("پست بانک"))
    )

    private fun mapChar(c: Char): Char = when (c) {
        in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')   // ۰-۹
        in '\u0660'..'\u0669' -> '0' + (c - '\u0660')   // ٠-٩
        '\u066C', '\u060C' -> ','                       // ٬ ،
        in INVISIBLE -> ' '
        else -> c
    }

    /** ارقام لاتین، یک فاصله بین کلمات، خطوط خالی حذف. فرم hash و پارس. */
    fun normalize(text: String?): String =
        (text ?: "").take(MAX_TEXT).map(::mapChar).joinToString("")
            .lines()
            .filter { it.isNotBlank() }
            .joinToString("\n") { it.trim().split(Regex("\\s+")).joinToString(" ") }
            .trim()

    fun fingerprint(bank: String?, text: String?): String {
        val raw = "${(bank ?: "").trim().lowercase(Locale.ROOT)}\n${normalize(text)}".lowercase(Locale.ROOT)
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun has(words: List<String>, chunk: String): Boolean {
        val low = chunk.lowercase(Locale.ROOT)
        return words.any { low.contains(it) }
    }

    /** Exact `parse(raw: String): Parsed` function from Section 3 of APP_SIDE_APPROVAL_SPEC.md. */
    fun parse(raw: String): Parsed {
        val text = normalize(raw)
        if (text.isEmpty()) return Parsed(null, "empty")

        var cleaned = text
        for (p in NOISE) {
            cleaned = p.replace(cleaned) { " ".repeat(it.value.length) } // طول حفظ شود
        }

        if (has(PROMO, text)) return Parsed(null, "promo")
        val creditMarked = has(CREDIT, cleaned)
        val debitMarked = has(DEBIT, cleaned)
        val credits = mutableListOf<Long>()

        for (m in NUM.findAll(cleaned)) {
            val start = m.range.first
            val before = cleaned.substring(maxOf(0, start - 14), start)
            val tail = before.trimEnd(' ', ':', '：', '=', '-')
            val sign = m.groupValues[1].ifEmpty { m.groupValues[3] }
            if (has(BALANCE, tail.takeLast(12)) || has(FEE, tail.takeLast(10))) continue
            val value = m.groupValues[2].replace(",", "").toLongOrNull() ?: continue
            if (sign == "-") return Parsed(null, "debit")
            when {
                sign == "+" || (creditMarked && !debitMarked) -> credits += value
                debitMarked && !creditMarked -> return Parsed(null, "debit")
                creditMarked && debitMarked -> return Parsed(null, "mixed_direction")
            }
        }
        if (credits.isEmpty()) return Parsed(null, if (!debitMarked) "no_credit_amount" else "debit")
        if (credits.toSet().size > 1) return Parsed(null, "several_amounts")
        var amount = credits[0]
        if ("تومان" in text && "ریال" !in text && "ريال" !in text) amount *= 10
        return Parsed(amount, "")
    }

    fun detectBank(sender: String?, body: String?): BankInfo {
        val cleanSender = (sender ?: "").trim().lowercase(Locale.ROOT)
        val cleanBody = normalize(body).lowercase(Locale.ROOT)

        if (cleanSender.isNotEmpty()) {
            KNOWN_BANKS.firstOrNull { bank ->
                bank.senderKeywords.any { kw -> cleanSender.contains(kw.lowercase(Locale.ROOT)) }
            }?.let { return it }
        }
        if (cleanBody.isNotEmpty()) {
            KNOWN_BANKS.firstOrNull { bank ->
                bank.bodyKeywords.any { kw -> cleanBody.contains(kw.lowercase(Locale.ROOT)) }
            }?.let { return it }
        }

        val rawBank = (sender ?: "").trim().take(32)
        val fallbackTitle = if (rawBank.isNotEmpty()) "فرستنده: $rawBank" else "بانک نامشخص"
        return BankInfo(
            code = rawBank,
            titleFa = fallbackTitle,
            senderKeywords = emptyList(),
            bodyKeywords = emptyList()
        )
    }

    /** Full parse helper used by UI and Receiver. Uses raw sender (`bank` up to 32 chars) for fingerprint. */
    fun parseFull(rawText: String?, sender: String? = null): ParsedSms {
        val rawSenderBank = (sender ?: "").trim().take(32)
        val detected = detectBank(rawSenderBank, rawText)
        val norm = normalize(rawText)
        val fp = fingerprint(rawSenderBank, rawText ?: "")

        if (norm.contains(TEST_MARK)) {
            return ParsedSms(
                amountMinor = null,
                reason = "test",
                normalizedText = norm,
                fingerprint = fp,
                bankCode = rawSenderBank.ifEmpty { detected.code },
                bankTitleFa = detected.titleFa
            )
        }

        val parsed = parse(rawText ?: "")
        return ParsedSms(
            amountMinor = parsed.amountMinor,
            reason = parsed.reason,
            normalizedText = norm,
            fingerprint = fp,
            bankCode = rawSenderBank.ifEmpty { detected.code },
            bankTitleFa = detected.titleFa
        )
    }

    fun formatToman(amountRial: Long): String =
        NumberFormat.getNumberInstance(Locale.US).format(amountRial / 10L)

    fun formatRial(amountRial: Long): String =
        NumberFormat.getNumberInstance(Locale.US).format(amountRial)

    fun reasonLabelFa(reason: String): String = when (reason) {
        "" -> "واریز معتبر (مبلغ دقیق استخراج شد)"
        "test" -> "پیام تست اتصال"
        "empty" -> "متن پیامک خالی است"
        "debit" -> "برداشت / خرید (نادیده گرفته‌شده)"
        "mixed_direction" -> "جهت مبهم (هم‌زمان کلمات واریز و برداشت)"
        "no_credit_amount" -> "مبلغ واریز یافت نشد"
        "several_amounts" -> "چند مبلغ متفاوت در یک پیامک (مبهم)"
        "promo" -> "پیامک تبلیغاتی یا حاوی لینک (واریز نیست)"
        "test_deposit_not_sent" -> "پیامک تستی؛ فقط پارس شد و به سرور ارسال نمی‌شود"
        else -> reason
    }
}
