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

data class ParsedSms(
    val amountMinor: Long?, // Rial (Toman * 10), null if not a clear single deposit
    val reason: String,     // "" when amountMinor is set, else short code matching sms_parse.py
    val normalizedText: String,
    val fingerprint: String,
    val bankCode: String,
    val bankTitleFa: String
)

/**
 * Exact Kotlin port of `reference/sms_parse.py` + Iranian bank identifier.
 * Parses bank SMS text to detect deposit amount in Rials and computes SHA-256 fingerprint.
 */
object SmsParser {

    const val TEST_MARK = "تست اتصال"
    const val MAX_SMS_LENGTH = 4000
    private const val PARSE_MAX_TEXT = 2000

    val KNOWN_BANKS: List<BankInfo> = listOf(
        BankInfo("mellat", "بانک ملت", listOf("mellat", "98200033", "200033", "100033", "300033"), listOf("بانک ملت", "ملت")),
        BankInfo("melli", "بانک ملی", listOf("melli", "bmi", "98600060", "600060", "100060"), listOf("بانک ملی", "بانك ملي", "ملی ایران")),
        BankInfo("saderat", "بانک صادرات", listOf("saderat", "bsi", "98200069", "200069", "600069"), listOf("صادرات", "سپهر")),
        BankInfo("tejarat", "بانک تجارت", listOf("tejarat", "98200070", "200070", "100070"), listOf("بانک تجارت", "تجارت")),
        BankInfo("sepah", "بانک سپه", listOf("sepah", "98200022", "200022", "300022"), listOf("بانک سپه", "سپه")),
        BankInfo("pasargad", "بانک پاسارگاد", listOf("pasargad", "bpi", "982000888", "2000888", "500040"), listOf("پاسارگاد", "ویپاد", "wepod")),
        BankInfo("parsian", "بانک پارسیان", listOf("parsian", "pec", "98200040", "200040"), listOf("پارسیان")),
        BankInfo("saman", "بانک سامان", listOf("saman", "sb24", "9820000", "20000", "98200084210"), listOf("بانک سامان", "سامان", "بلوبانک", "بلو", "blu")),
        BankInfo("blu", "بلوبانک", listOf("blu", "blubank", "98200084210"), listOf("بلوبانک", "بلو بانک", "حساب بلو")),
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

    private val DIGIT_MAP: Map<Char, Char> = buildMap {
        val persian = "۰۱۲۳۴۵۶۷۸۹"
        val arabic = "٠١٢٣٤٥٦٧٨٩"
        for (i in 0..9) {
            put(persian[i], ('0' + i))
            put(arabic[i], ('0' + i))
        }
        put('٬', ',')
        put('،', ',')
    }

    private val INVISIBLE_CHARS: Set<Char> = setOf(
        '\u200c', '\u200d', '\u200e', '\u200f',
        '\u202a', '\u202b', '\u202c', '\u202d', '\u202e',
        '\u00a0', '\ufeff'
    )

    private val NOISE_PATTERNS: List<Regex> = listOf(
        Regex("""\b\d{2,4}[/\-.]\d{1,2}[/\-.]\d{1,4}\b"""),                 // 1405/08/20  20-08-2026
        Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\b"""),                           // 14:35  14:35:10
        Regex("""\b\d{4}[\s\-]?\d{4}[\s\-]?\d{4}[\s\-]?\d{4}\b"""),         // 16-digit card
        Regex("""\d{4,6}\s?[*xX•#]{2,}\s?\d{2,4}"""),                        // 603799******1234
        Regex("""[*xX•#]{2,}\s?\d{2,6}"""),                                  // ***1234
        Regex("""(?:\+98|0098|98)?9\d{9}\b"""),                              // mobile number
        Regex("""\b0\d{8,}\b""")                                             // account numbers start with 0
    )

    private val NUM_REGEX = Regex(
        """(?<![a-zA-Z0-9_.])([+\-])?\s?(\d{1,3}(?:,\d{3})+|[1-9]\d{3,11})(?!\d)\s?([+\-])?(?![a-zA-Z0-9_])"""
    )

    private val CREDIT_WORDS = listOf(
        "واریز", "واريز", "دریافت", "ایداع", "إيداع", "افزایش", "بستانکار", "deposit", "credit", "variz"
    )
    private val DEBIT_WORDS = listOf(
        "برداشت", "پرداخت", "خرید", "کسر", "انتقال از", "بدهکار", "پایا", "ساتنا", "قبض",
        "withdraw", "debit", "purchase"
    )
    private val BALANCE_WORDS = listOf("مانده", "موجودی", "باقیمانده", "balance", "bal")
    private val FEE_WORDS = listOf("کارمزد", "wage", "fee")

    fun normalize(rawText: String?): String {
        if (rawText.isNullOrEmpty()) return ""
        val truncated = rawText.take(PARSE_MAX_TEXT)
        val sb = StringBuilder(truncated.length)
        for (ch in truncated) {
            when {
                DIGIT_MAP.containsKey(ch) -> sb.append(DIGIT_MAP[ch])
                INVISIBLE_CHARS.contains(ch) -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
            .lines()
            .map { line -> line.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }.joinToString(" ") }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()
    }

    fun fingerprint(bank: String?, text: String?): String {
        val normBank = (bank ?: "").trim().lowercase(Locale.ROOT)
        val normText = normalize(text)
        val raw = "$normBank\n$normText".lowercase(Locale.ROOT)
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun detectBank(sender: String?, body: String?): BankInfo {
        val cleanSender = (sender ?: "").trim().lowercase(Locale.ROOT)
        val cleanBody = normalize(body).lowercase(Locale.ROOT)

        // First match by sender number / shortcode / ID
        if (cleanSender.isNotEmpty()) {
            KNOWN_BANKS.firstOrNull { bank ->
                bank.senderKeywords.any { kw -> cleanSender.contains(kw.lowercase(Locale.ROOT)) }
            }?.let { return it }
        }

        // Next match by SMS body keywords
        if (cleanBody.isNotEmpty()) {
            KNOWN_BANKS.firstOrNull { bank ->
                bank.bodyKeywords.any { kw -> cleanBody.contains(kw.lowercase(Locale.ROOT)) }
            }?.let { return it }
        }

        val fallbackCode = cleanSender.take(32).ifEmpty { "unknown" }
        val fallbackTitle = if (cleanSender.isNotEmpty()) "فرستنده: $cleanSender" else "بانک نامشخص"
        return BankInfo(
            code = fallbackCode,
            titleFa = fallbackTitle,
            senderKeywords = emptyList(),
            bodyKeywords = emptyList()
        )
    }

    private fun hasAnyWord(words: List<String>, chunk: String): Boolean {
        val low = chunk.lowercase(Locale.ROOT)
        return words.any { low.contains(it) }
    }

    fun parse(rawText: String?, sender: String? = null, explicitBankCode: String? = null): ParsedSms {
        val detected = detectBank(sender, rawText)
        val bankCode = explicitBankCode?.trim()?.takeIf { it.isNotEmpty() } ?: detected.code
        val bankTitle = KNOWN_BANKS.firstOrNull { it.code.equals(bankCode, ignoreCase = true) }?.titleFa
            ?: detected.titleFa

        val text = normalize(rawText)
        val fp = fingerprint(bankCode, rawText)

        if (text.isEmpty()) {
            return ParsedSms(null, "empty", text, fp, bankCode, bankTitle)
        }

        if (text.contains(TEST_MARK)) {
            return ParsedSms(null, "test", text, fp, bankCode, bankTitle)
        }

        var cleaned = text
        for (pattern in NOISE_PATTERNS) {
            cleaned = pattern.replace(cleaned) { matchResult ->
                " ".repeat(matchResult.range.last - matchResult.range.first + 1)
            }
        }

        val creditMarked = hasAnyWord(CREDIT_WORDS, cleaned)
        val debitMarked = hasAnyWord(DEBIT_WORDS, cleaned)

        val credits = mutableListOf<Long>()
        val trimChars = charArrayOf(' ', ':', '：', '=', '-')

        for (match in NUM_REGEX.findAll(cleaned)) {
            val startIdx = match.range.first
            val before = cleaned.substring(maxOf(0, startIdx - 14), startIdx)
            val beforeTail = before.trimEnd(*trimChars)
            val sign = match.groups[1]?.value?.takeIf { it.isNotEmpty() }
                ?: match.groups[3]?.value?.takeIf { it.isNotEmpty() }
                ?: ""

            val tail12 = if (beforeTail.length > 12) beforeTail.takeLast(12) else beforeTail
            val tail10 = if (beforeTail.length > 10) beforeTail.takeLast(10) else beforeTail

            if (hasAnyWord(BALANCE_WORDS, tail12) || hasAnyWord(FEE_WORDS, tail10)) {
                continue
            }

            val rawNumber = match.groups[2]?.value?.replace(",", "") ?: continue
            val value = rawNumber.toLongOrNull() ?: continue

            if (sign == "-") {
                return ParsedSms(null, "debit", text, fp, bankCode, bankTitle)
            }
            if (sign == "+" || (creditMarked && !debitMarked)) {
                credits.add(value)
            } else if (debitMarked && !creditMarked) {
                return ParsedSms(null, "debit", text, fp, bankCode, bankTitle)
            } else if (creditMarked && debitMarked) {
                return ParsedSms(null, "mixed_direction", text, fp, bankCode, bankTitle)
            }
        }

        if (credits.isEmpty()) {
            val reason = if (!debitMarked) "no_credit_amount" else "debit"
            return ParsedSms(null, reason, text, fp, bankCode, bankTitle)
        }

        if (credits.toSet().size > 1) {
            return ParsedSms(null, "several_amounts", text, fp, bankCode, bankTitle)
        }

        var amount = credits[0]
        if (text.contains("تومان") && !text.contains("ریال") && !text.contains("ريال")) {
            amount *= 10L
        }

        return ParsedSms(
            amountMinor = amount,
            reason = "",
            normalizedText = text,
            fingerprint = fp,
            bankCode = bankCode,
            bankTitleFa = bankTitle
        )
    }

    fun formatToman(amountRial: Long): String {
        val toman = amountRial / 10L
        return NumberFormat.getNumberInstance(Locale.US).format(toman)
    }

    fun formatRial(amountRial: Long): String {
        return NumberFormat.getNumberInstance(Locale.US).format(amountRial)
    }

    fun reasonLabelFa(reason: String): String = when (reason) {
        "" -> "واریز معتبر (تشخیص مبلغ دقیق)"
        "test" -> "پیام تست اتصال به درگاه"
        "empty" -> "متن پیامک خالی است"
        "debit" -> "پیامک برداشت / خرید (نادیده گرفته‌شده توسط سرور)"
        "mixed_direction" -> "هم‌زمان کلمات واریز و برداشت بدون علامت +/-"
        "no_credit_amount" -> "مبلغ واریز یافت نشد"
        "several_amounts" -> "چند مبلغ متفاوت در یک پیامک (مبهم)"
        else -> reason
    }
}
