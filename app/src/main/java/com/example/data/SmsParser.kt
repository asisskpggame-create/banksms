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
        // بانک‌های دولتی، تجاری و تخصصی
        BankInfo(
            code = "mellat",
            titleFa = "بانک ملت",
            senderKeywords = listOf("mellat", "bmellat", "98200033", "200033", "100033", "300033", "98100033", "98300033"),
            bodyKeywords = listOf("بانک ملت", "بانك ملت", "ملت")
        ),
        BankInfo(
            code = "melli",
            titleFa = "بانک ملی ایران",
            senderKeywords = listOf("melli", "bmi", "bam", "98600060", "600060", "100060", "200060", "98100060", "700789"),
            bodyKeywords = listOf("بانک ملی", "بانك ملي", "ملی ایران", "ملي ايران", "سامانه بام")
        ),
        BankInfo(
            code = "saderat",
            titleFa = "بانک صادرات ایران",
            senderKeywords = listOf("saderat", "bsi", "sepehr", "98200069", "200069", "600069", "100069", "300069", "98600069"),
            bodyKeywords = listOf("بانک صادرات", "بانك صادرات", "صادرات ایران", "صادرات", "سپهر")
        ),
        BankInfo(
            code = "tejarat",
            titleFa = "بانک تجارت",
            senderKeywords = listOf("tejarat", "bti", "98200070", "200070", "100070", "300070", "98100070"),
            bodyKeywords = listOf("بانک تجارت", "بانك تجارت", "تجارت")
        ),
        BankInfo(
            code = "sepah",
            titleFa = "بانک سپه",
            senderKeywords = listOf("sepah", "banksepah", "98200022", "200022", "300022", "100022", "98300022"),
            bodyKeywords = listOf("بانک سپه", "بانك سپه", "سپه")
        ),
        BankInfo(
            code = "keshavarzi",
            titleFa = "بانک کشاورزی",
            senderKeywords = listOf("keshavarzi", "bki", "agribank", "98200090", "200090", "98200091", "200091", "500022", "100090", "300090"),
            bodyKeywords = listOf("بانک کشاورزی", "بانك كشاورزي", "کشاورزی", "كشاورزي")
        ),
        BankInfo(
            code = "maskan",
            titleFa = "بانک مسکن",
            senderKeywords = listOf("maskan", "bankmaskan", "98200014", "200014", "300014", "100014", "98300014", "700714"),
            bodyKeywords = listOf("بانک مسکن", "بانك مسكن", "مسکن")
        ),
        BankInfo(
            code = "refah",
            titleFa = "بانک رفاه کارگران",
            senderKeywords = listOf("refah", "rb24", "refahbank", "98200000", "200000", "100000", "300000", "98300000", "200026"),
            bodyKeywords = listOf("بانک رفاه", "بانك رفاه", "رفاه کارگران", "رفاه كارگران")
        ),
        BankInfo(
            code = "postbank",
            titleFa = "پست بانک ایران",
            senderKeywords = listOf("postbank", "pbi", "98200084", "200084", "100084", "300084"),
            bodyKeywords = listOf("پست بانک", "پست بانك", "پست‌بانک")
        ),
        BankInfo(
            code = "tosee_taavon",
            titleFa = "بانک توسعه تعاون",
            senderKeywords = listOf("ttbank", "taavon", "toseetaavon", "98200099", "200099", "100099", "300099", "300027"),
            bodyKeywords = listOf("توسعه تعاون", "بانک توسعه تعاون", "بانك توسعه تعاون")
        ),
        BankInfo(
            code = "sanat_madan",
            titleFa = "بانک صنعت و معدن",
            senderKeywords = listOf("bim", "sanatmadan", "98200011", "200011", "100011", "300011", "200083"),
            bodyKeywords = listOf("صنعت و معدن", "بانک صنعت و معدن", "بانك صنعت و معدن")
        ),
        BankInfo(
            code = "edbi",
            titleFa = "بانک توسعه صادرات",
            senderKeywords = listOf("edbi", "eximbank", "98200027", "200027", "100027", "3000270", "200020"),
            bodyKeywords = listOf("توسعه صادرات", "بانک توسعه صادرات", "بانك توسعه صادرات")
        ),
        BankInfo(
            code = "cbi",
            titleFa = "بانک مرکزی ج.ا.ا",
            senderKeywords = listOf("cbi", "centralbank", "98200038", "200038", "100038"),
            bodyKeywords = listOf("بانک مرکزی", "بانك مركزي")
        ),

        // بانک‌های خصوصی
        BankInfo(
            code = "pasargad",
            titleFa = "بانک پاسارگاد",
            senderKeywords = listOf("pasargad", "bpi", "982000888", "2000888", "500040", "1000888", "3000888"),
            bodyKeywords = listOf("بانک پاسارگاد", "بانك پاسارگاد", "پاسارگاد")
        ),
        BankInfo(
            code = "parsian",
            titleFa = "بانک پارسیان",
            senderKeywords = listOf("parsian", "pec", "parsianbank", "98200040", "200040", "100040", "300040", "98300040"),
            bodyKeywords = listOf("بانک پارسیان", "بانك پارسيان", "پارسیان", "پارسيان")
        ),
        BankInfo(
            code = "saman",
            titleFa = "بانک سامان",
            senderKeywords = listOf("saman", "sb24", "samanbank", "9820000", "20000", "10000", "30000", "200024"),
            bodyKeywords = listOf("بانک سامان", "بانك سامان", "سامان")
        ),
        BankInfo(
            code = "enbank",
            titleFa = "بانک اقتصاد نوین",
            senderKeywords = listOf("enbank", "eghtesadnovin", "98200050", "200050", "100050", "300050"),
            bodyKeywords = listOf("اقتصاد نوین", "اقتصادنوین", "اقتصاد نوين", "بانک اقتصاد نوین")
        ),
        BankInfo(
            code = "ayandeh",
            titleFa = "بانک آینده",
            senderKeywords = listOf("ayandeh", "ba24", "ayandehbank", "98200049", "200049", "100049", "300049"),
            bodyKeywords = listOf("بانک آینده", "بانك آينده", "آینده", "آينده")
        ),
        BankInfo(
            code = "shahr",
            titleFa = "بانک شهر",
            senderKeywords = listOf("shahr", "shahrbank", "98200086", "200086", "100086", "300086", "982000861"),
            bodyKeywords = listOf("بانک شهر", "بانك شهر")
        ),
        BankInfo(
            code = "sina",
            titleFa = "بانک سینا",
            senderKeywords = listOf("sina", "sinabank", "982000727", "2000727", "1000727", "3000727"),
            bodyKeywords = listOf("بانک سینا", "بانك سينا", "سینا")
        ),
        BankInfo(
            code = "gardeshgari",
            titleFa = "بانک گردشگری",
            senderKeywords = listOf("gardeshgari", "tourism", "tourismbank", "982000181", "2000181", "1000181", "3000181"),
            bodyKeywords = listOf("بانک گردشگری", "بانك گردشگري", "گردشگری", "گردشگري")
        ),
        BankInfo(
            code = "karafarin",
            titleFa = "بانک کارآفرین",
            senderKeywords = listOf("karafarin", "kb24", "karafarinbank", "98200087", "200087", "100087", "300087"),
            bodyKeywords = listOf("بانک کارآفرین", "بانك كارآفرين", "کارآفرین", "كارآفرين")
        ),
        BankInfo(
            code = "khavarmianeh",
            titleFa = "بانک خاورمیانه",
            senderKeywords = listOf("mebank", "khavarmianeh", "98200058", "200058", "100058", "300058"),
            bodyKeywords = listOf("بانک خاورمیانه", "بانك خاورميانه", "خاورمیانه", "خاورميانه")
        ),
        BankInfo(
            code = "day",
            titleFa = "بانک دی",
            senderKeywords = listOf("day", "daybank", "bankday", "98200092", "200092", "100092", "300092", "200019"),
            bodyKeywords = listOf("بانک دی", "بانك دي")
        ),
        BankInfo(
            code = "sarmayeh",
            titleFa = "بانک سرمایه",
            senderKeywords = listOf("sarmayeh", "sbank", "sarmayebank", "98200075", "200075", "100075", "300075", "200048"),
            bodyKeywords = listOf("بانک سرمایه", "بانك سرمايه", "سرمایه")
        ),
        BankInfo(
            code = "iranzamin",
            titleFa = "بانک ایران زمین",
            senderKeywords = listOf("iranzamin", "izbank", "98200068", "200068", "100068", "300068", "3000690"),
            bodyKeywords = listOf("ایران زمین", "ايران زمين", "بانک ایران زمین", "بانك ايران زمين")
        ),

        // بانک‌های قرض‌الحسنه، موسسات اعتباری و بانک‌های مشترک
        BankInfo(
            code = "resalat",
            titleFa = "بانک قرض‌الحسنه رسالت",
            senderKeywords = listOf("resalat", "rqbank", "982000069", "2000069", "1000069", "3000069"),
            bodyKeywords = listOf("رسالت", "قرض الحسنه رسالت", "قرض‌الحسنه رسالت", "بانک رسالت")
        ),
        BankInfo(
            code = "mehr",
            titleFa = "بانک قرض‌الحسنه مهر ایران",
            senderKeywords = listOf("mehr", "qmb", "mehriran", "982000444", "2000444", "1000444", "3000444"),
            bodyKeywords = listOf("مهر ایران", "مهر ايران", "قرض الحسنه مهر", "قرض‌الحسنه مهر", "بانک مهر ایران")
        ),
        BankInfo(
            code = "melal",
            titleFa = "موسسه اعتباری ملل",
            senderKeywords = listOf("melal", "melalbank", "askariyeh", "982000755", "2000755", "1000755", "3000755", "20000755"),
            bodyKeywords = listOf("موسسه اعتباری ملل", "موسسه ملل", "اعتباری ملل", "بانک ملل")
        ),
        BankInfo(
            code = "noor",
            titleFa = "موسسه اعتباری نور",
            senderKeywords = listOf("noor", "noorbank", "98200095", "200095", "100095", "300095", "200059"),
            bodyKeywords = listOf("موسسه اعتباری نور", "موسسه نور", "اعتباری نور", "بانک نور")
        ),
        BankInfo(
            code = "caspian",
            titleFa = "موسسه اعتباری کاسپین",
            senderKeywords = listOf("caspian", "98200047", "200047", "100047", "300047"),
            bodyKeywords = listOf("موسسه اعتباری کاسپین", "موسسه کاسپین", "کاسپین")
        ),
        BankInfo(
            code = "tosee",
            titleFa = "موسسه اعتباری توسعه",
            senderKeywords = listOf("tosee", "98200071", "200071", "100071"),
            bodyKeywords = listOf("موسسه اعتباری توسعه", "اعتباری توسعه")
        ),
        BankInfo(
            code = "ivbb",
            titleFa = "بانک مشترک ایران و ونزوئلا",
            senderKeywords = listOf("ivbb", "98200098", "200098", "100098"),
            bodyKeywords = listOf("ایران و ونزوئلا", "ايران و ونزوئلا", "بانک مشترک ایران و ونزوئلا")
        ),
        BankInfo(
            code = "eihbank",
            titleFa = "بانک تجارتی ایران و اروپا",
            senderKeywords = listOf("eihbank", "eih"),
            bodyKeywords = listOf("ایران و اروپا", "تجارتی ایران و اروپا")
        ),

        // بانک‌ها و موسسات ادغامی در بانک سپه (جهت پشتیبانی از سرشماره‌ها و نام‌های قدیمی)
        BankInfo(
            code = "ansar",
            titleFa = "بانک انصار (سپه)",
            senderKeywords = listOf("ansar", "ansarbank", "98200055", "200055", "100055", "300055"),
            bodyKeywords = listOf("بانک انصار", "بانك انصار", "انصار")
        ),
        BankInfo(
            code = "ghavamin",
            titleFa = "بانک قوامین (سپه)",
            senderKeywords = listOf("ghavamin", "98200025", "200025", "100025", "300025"),
            bodyKeywords = listOf("بانک قوامین", "بانك قوامين", "قوامین")
        ),
        BankInfo(
            code = "hekmat",
            titleFa = "بانک حکمت ایرانیان (سپه)",
            senderKeywords = listOf("hekmat", "hib", "98200029", "200029", "100029", "300029"),
            bodyKeywords = listOf("حکمت ایرانیان", "حكمت ايرانيان", "بانک حکمت")
        ),
        BankInfo(
            code = "mehr_eghtesad",
            titleFa = "بانک مهر اقتصاد (سپه)",
            senderKeywords = listOf("mehreghtesad", "meb", "98200077", "200077", "100077", "300077"),
            bodyKeywords = listOf("مهر اقتصاد", "مهراقتصاد", "بانک مهر اقتصاد")
        ),
        BankInfo(
            code = "kosar",
            titleFa = "موسسه کوثر (سپه)",
            senderKeywords = listOf("kosar", "kosarbank", "98200044", "200044", "100044", "300044"),
            bodyKeywords = listOf("موسسه کوثر", "اعتباری کوثر", "بانک کوثر")
        ),

        // نئوبانک‌ها و بانک‌های دیجیتال ایرانی
        BankInfo(
            code = "blu",
            titleFa = "بلوبانک (سامان)",
            senderKeywords = listOf("blu", "blubank", "98200084210", "200084210"),
            bodyKeywords = listOf("بلوبانک", "بلو بانک", "حساب بلو", "بلو")
        ),
        BankInfo(
            code = "wepod",
            titleFa = "ویپاد (پاسارگاد)",
            senderKeywords = listOf("wepod", "9820008881", "20008881"),
            bodyKeywords = listOf("ویپاد", "ويپاد", "wepod", "ترابانک پاسارگاد", "ترابانک")
        ),
        BankInfo(
            code = "zypod",
            titleFa = "زیپاد (پاسارگاد)",
            senderKeywords = listOf("zypod"),
            bodyKeywords = listOf("زیپاد", "زيپاد", "zypod")
        ),
        BankInfo(
            code = "bankino",
            titleFa = "بانکینو (خاورمیانه)",
            senderKeywords = listOf("bankino", "982000581", "2000581"),
            bodyKeywords = listOf("بانکینو", "بانكينو", "bankino")
        ),
        BankInfo(
            code = "abank",
            titleFa = "آبانک (آینده)",
            senderKeywords = listOf("abank", "982000491", "2000491"),
            bodyKeywords = listOf("آبانک", "آبانك", "abank")
        ),
        BankInfo(
            code = "tobank",
            titleFa = "توبانک (گردشگری)",
            senderKeywords = listOf("tobank", "9820001811", "20001811"),
            bodyKeywords = listOf("توبانک", "توبانك", "tobank")
        ),
        BankInfo(
            code = "hibank",
            titleFa = "های‌بانک (کارآفرین)",
            senderKeywords = listOf("hibank", "982000871", "2000871"),
            bodyKeywords = listOf("های بانک", "های‌بانک", "هاي بانك", "hibank")
        ),
        BankInfo(
            code = "omidbank",
            titleFa = "امیدبانک (سپه)",
            senderKeywords = listOf("omidbank", "omid", "982000221", "2000221"),
            bodyKeywords = listOf("امیدبانک", "امید بانک", "اميدبانك", "omidbank")
        ),
        BankInfo(
            code = "qbank",
            titleFa = "کیوبانک (مهر ایران)",
            senderKeywords = listOf("qbank"),
            bodyKeywords = listOf("کیوبانک", "کیو بانک", "qbank")
        ),
        BankInfo(
            code = "lenz",
            titleFa = "نئوبانک لنز (ایران زمین)",
            senderKeywords = listOf("lenz", "lenzbank"),
            bodyKeywords = listOf("نئوبانک لنز", "بانک لنز")
        )
    )

    private val NEOBANK_CODES = setOf(
        "blu", "wepod", "zypod", "bankino", "abank", "tobank", "hibank", "omidbank", "qbank", "lenz"
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

        // ۱. اگر متن پیامک صراحتاً نام یکی از نئوبانک‌ها را دارد (که گاه از سرشمارهٔ بانک مادر ارسال می‌شوند)
        if (cleanBody.isNotEmpty()) {
            KNOWN_BANKS.filter { it.code in NEOBANK_CODES }
                .firstOrNull { bank ->
                    bank.bodyKeywords.any { kw -> cleanBody.contains(kw.lowercase(Locale.ROOT)) }
                }?.let { return it }
        }

        // ۲. تطبیق دقیق یا طولانی‌ترین سرشماره (تا سرشماره‌های کوتاه‌تر مثل 20000 روی 200000 یا 2000069 نیفتند)
        if (cleanSender.isNotEmpty()) {
            val strippedSender = cleanSender.removePrefix("+").removePrefix("0098").removePrefix("98")
            KNOWN_BANKS.firstOrNull { bank ->
                bank.senderKeywords.any { kw ->
                    val lowKw = kw.lowercase(Locale.ROOT)
                    val strippedKw = lowKw.removePrefix("98")
                    cleanSender == lowKw || (strippedSender.isNotEmpty() && strippedSender == strippedKw)
                }
            }?.let { return it }

            var bestMatch: BankInfo? = null
            var bestLen = 0
            for (bank in KNOWN_BANKS) {
                for (kw in bank.senderKeywords) {
                    val lowKw = kw.lowercase(Locale.ROOT)
                    if (cleanSender.contains(lowKw) && lowKw.length > bestLen) {
                        bestMatch = bank
                        bestLen = lowKw.length
                    }
                }
            }
            if (bestMatch != null) return bestMatch
        }

        // ۳. تطبیق بر اساس نام بانک در متن پیامک (طولانی‌ترین کلیدواژهٔ منطبق)
        if (cleanBody.isNotEmpty()) {
            var bestBodyBank: BankInfo? = null
            var bestBodyLen = 0
            for (bank in KNOWN_BANKS) {
                for (kw in bank.bodyKeywords) {
                    val lowKw = kw.lowercase(Locale.ROOT)
                    if (cleanBody.contains(lowKw) && lowKw.length > bestBodyLen) {
                        bestBodyBank = bank
                        bestBodyLen = lowKw.length
                    }
                }
            }
            if (bestBodyBank != null) return bestBodyBank
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
