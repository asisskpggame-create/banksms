# مشخصات «تأیید سمت اپ» — ASiS pannel

هدف: خواندن پیامک، تشخیص واریز، تطبیق با سفارش و **زدن دکمهٔ تأیید** همه داخل اپ اندروید انجام شود؛ سرور فقط
«اجرا و نگهبانی» کند، نه «تصمیم‌گیری».

> این فایل به `ROADMAP.md` و `API_CONTRACT.md` اضافه می‌شود و در بخش‌هایی که تناقض دارد، **این فایل مقدم است**.
> منطق مرجع همان `reference/sms_parse.py` و `reference/sms_gateway.py` است؛ اپ باید دقیقاً همان رفتار را بازتولید کند.

---

## ۰) تغییر معماری

| مرحله | قبل | بعد |
|---|---|---|
| خواندن/پارس پیامک | سرور (`sms_parse.parse`) | **اپ** (`SmsParser.kt`) |
| ضد تکرار (fingerprint) | سرور | اپ (محلی) + سرور (قفل نهایی) |
| تطبیق واریز با سفارش | سرور (`sweep`, `plan_matches`) | **اپ** (`Matcher.kt`) |
| فرمان تأیید | سرور خودش | **اپ** با `POST /sms-approve` |
| قفل‌های ایمنی نهایی | سرور | **همچنان سرور** (بخش ۵ — اجباری) |

---

## ۱) جریان کار

```
SMS_RECEIVED
  └─ SmsReceiver: چسباندن PDUها، ثبت sender، زمان دریافت
       └─ SmsParser.normalize + fingerprint
            ├─ تکراری در Room؟ → رد
            └─ SmsParser.parse → Parsed(amountMinor | null, reason)
                 ├─ amount == null → ذخیره با status=IGNORED (+reason)، تمام
                 └─ amount != null → status=NEW، صف Matcher را بیدار کن

MatcherWorker (بعد از هر SMS جدید + هر ۳۰ ثانیه + وقتی اینترنت وصل شد)
  1. GET /sms-pending            ← لیست پرداخت‌های منتظر
  2. Matcher.planMatches(deposits, items)
  3. برای هر جفت:  POST /sms-approve   (idempotent)
  4. برای هر ambiguous: وضعیت AMBIGUOUS + نوتیفیکیشن «دستی بررسی کنید»
```

---

## ۲) خواندن پیامک (`SmsReceiver.kt`)

- از `Telephony.Sms.Intents.getMessagesFromIntent(intent)` استفاده کن و **همهٔ PDUها را به ترتیب به هم بچسبان**
  (`messages.joinToString("") { it.messageBody ?: "" }`). پیام چندبخشی را فقط **یک بار** پردازش کن.
- `bank = messages[0].originatingAddress ?: ""` (حداکثر ۳۲ کاراکتر).
- `receivedAtMs = messages[0].timestampMillis` و جداگانه `System.currentTimeMillis()` را هم ذخیره کن.
- هیچ trim/تبدیل/فیلتری روی **متن خام ذخیره‌شده** نکن؛ نرمال‌سازی فقط روی کپی انجام می‌شود.
- پیش‌فرض: همهٔ فرستنده‌ها پردازش شوند (فیلتر بانک فقط اگر کاربر صراحتاً فعالش کند).
- دریافت‌کننده را با `goAsync()` یا مستقیم WorkManager `OneTimeWork` اجرا کن تا پردازش وسط کار کشته نشود.

---

## ۳) پارسر — پورت دقیق `sms_parse.py` (`SmsParser.kt`)

قانون طلایی (عیناً از سرور): **اگر مطمئن نیستی که پیام «یک واریز با یک مبلغ» است، `null` برگردان.**
تأییدِ اشتباه = مشتری بدون پرداخت پنل می‌گیرد؛ ردِ اشتباه = ادمین یک کلیک می‌زند.

```kotlin
package com.asis.panel.sms

import java.security.MessageDigest

data class Parsed(val amountMinor: Long?, val reason: String)   // ریال؛ reason خالی = موفق

object SmsParser {
    private const val MAX_TEXT = 2000

    // (?U) = UNICODE_CHARACTER_CLASS تا \w و \s مثل پایتون حروف فارسی را هم بشناسند.
    private fun rx(p: String) = Regex("(?U)$p")

    private val NOISE = listOf(
        rx("""\b\d{2,4}[/\-.]\d{1,2}[/\-.]\d{1,4}\b"""),                 // تاریخ
        rx("""\b\d{1,2}:\d{2}(?::\d{2})?\b"""),                           // ساعت
        rx("""\b\d{4}[\s\-]?\d{4}[\s\-]?\d{4}[\s\-]?\d{4}\b"""),          // کارت ۱۶ رقمی
        rx("""\d{4,6}\s?[*xX•#]{2,}\s?\d{2,4}"""),                        // 603799******1234
        rx("""[*xX•#]{2,}\s?\d{2,6}"""),                                  // ***1234
        rx("""(?:\+98|0098|98)?9\d{9}\b"""),                              // موبایل
        rx("""\b0\d{8,}\b"""),                                            // شمارهٔ حساب
    )

    private val NUM = rx("""(?<![\w.])([+\-])?\s?(\d{1,3}(?:,\d{3})+|[1-9]\d{3,11})(?!\d)\s?([+\-])?(?![\w])""")

    private val CREDIT  = listOf("واریز", "واريز", "دریافت", "ایداع", "إيداع", "افزایش", "بستانکار", "deposit", "credit", "variz")
    private val DEBIT   = listOf("برداشت", "پرداخت", "خرید", "کسر", "انتقال از", "بدهکار", "پایا", "ساتنا", "قبض", "withdraw", "debit", "purchase")
    private val BALANCE = listOf("مانده", "موجودی", "باقیمانده", "balance", "bal")
    private val FEE     = listOf("کارمزد", "wage", "fee")

    private val INVISIBLE = "\u200c\u200d\u200e\u200f\u202a\u202b\u202c\u202d\u202e\u00a0\ufeff".toSet()

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

    fun fingerprint(bank: String?, text: String): String {
        val raw = "${(bank ?: "").trim().lowercase()}\n${normalize(text)}".lowercase()
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun has(words: List<String>, chunk: String): Boolean {
        val low = chunk.lowercase()
        return words.any { low.contains(it) }
    }

    fun parse(raw: String): Parsed {
        val text = normalize(raw)
        if (text.isEmpty()) return Parsed(null, "empty")

        var cleaned = text
        for (p in NOISE) cleaned = p.replace(cleaned) { " ".repeat(it.value.length) } // طول حفظ شود

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
}
```

**تست واحد (الزامی، با JUnit):** حداقل این موارد:
- واریز ساده تومانی → ×۱۰؛ واریز ریالی → بدون ضرب.
- ارقام فارسی و عربی، جداکنندهٔ ٬ و ،
- پیام با «مانده» و «کارمزد» → فقط مبلغ واریز شمرده شود.
- برداشت/خرید → `debit`؛ پیام مختلط → `mixed_direction`؛ دو مبلغ متفاوت → `several_amounts`.
- تاریخ/ساعت/شمارهٔ کارت/حساب/موبایل هرگز مبلغ حساب نشوند.
- fingerprint پیام با ارقام فارسی = fingerprint همان پیام با ارقام لاتین.
- **تست توازن (parity):** حداقل ۳۰ پیامک نمونه را هم با `sms_parse.py` و هم با `SmsParser.kt` اجرا کن و خروجی‌ها
  باید یکی باشند.

---

## ۴) تطبیق (`Matcher.kt`) — پورت `plan_matches`

```kotlin
data class Deposit(val id: Long, val amountMinor: Long, val at: Long)            // epoch ms
data class Item(val kind: String, val publicId: String, val dueMinor: Long,
                val at: Long, val early: Boolean = false)

object Matcher {
    fun plan(deposits: List<Deposit>, items: List<Item>): Pair<List<Pair<Deposit, Item>>, List<Deposit>> {
        val pairs = mutableListOf<Pair<Deposit, Item>>()
        val ambiguous = mutableListOf<Deposit>()
        for (amount in deposits.map { it.amountMinor }.toSortedSet()) {
            var ds = deposits.filter { it.amountMinor == amount }.sortedBy { it.at }   // قدیمی‌ترین اول
            var its = items.filter { it.dueMinor == amount }
            its = its.filter { i -> ds.any { d -> d.at >= i.at } }        // بعد از ساخته‌شدنش واریز شده
            ds  = ds.filter { d -> its.any { i -> d.at >= i.at } }        // و واقعاً می‌تواند چیزی را بپردازد
            if (its.isEmpty()) continue
            its = its.sortedBy { it.at }
            if (its.size <= ds.size) pairs += ds.zip(its) else ambiguous += ds
        }
        return pairs to ambiguous
    }
}
```

قواعد ثابت:
- فقط مبلغ **دقیقاً برابر** (`dueMinor == amountMinor`). هیچ تقریب، رُند یا «حدوداً» وجود ندارد.
- واریز فقط پرداختی را می‌پردازد که **قبل از رسیدن پیامک** وجود داشته. ساعت گوشی ممکن است با سرور فرق کند، پس:
  `GET /sms-pending` علاوه بر لیست، `server_now_ms` برمی‌گرداند؛ اپ `offset = server_now - phone_now` را محاسبه و
  زمان SMS را با آن تصحیح می‌کند (`depositAtServerTime = receivedAtMs + offset`).
- `I > D` (چند منتظر، واریز کمتر) → **هیچ‌چیز تأیید نشود**؛ `AMBIGUOUS` + نوتیفیکیشن.
- واریزهای `NEW` که بیش از `match_window_hours` (از سرور می‌آید) گذشته‌اند → `EXPIRED`.

---

## ۵) قرارداد جدید API (باید به سرور اضافه شود)

اپ نمی‌تواند لیست سفارش‌های منتظر را بداند، پس دو endpoint جدید لازم است. هر دو مثل `/sms-in` زیر مسیر
پنل و **خارج از `/api/`** هستند، با همان توکن و همان limiter.

### `GET /<PANEL_PATH>/sms-pending?token=...` (یا هدر `X-Token`)
پاسخ `200`:
```json
{
  "ok": true,
  "server_now_ms": 1780000000000,
  "match_window_hours": 24,
  "auto_max_toman": 5000000,
  "items": [
    {"kind": "order", "public_id": "ORD123", "due_minor": 5000070,
     "created_ms": 1779990000000, "early": false}
  ]
}
```
محتوای `items` همان خروجی `_pending_items` سرور است (فقط پرداخت‌هایی که رسیدشان رسیده، یا «early» با تگ یکتا وقتی
تنظیمات اجازه بدهد).

### `POST /<PANEL_PATH>/sms-approve`
```json
{
  "token": "...",
  "kind": "order",
  "public_id": "ORD123",
  "fingerprint": "<sha256 hex>",
  "amount_minor": 5000070,
  "bank": "<sender>",
  "sms": "<متن خام کامل>",
  "sms_received_ms": 1780000000000,
  "ts": 1780000001000,
  "sig": "<HMAC-SHA256 hex>"
}
```
- `sig = HMAC_SHA256(key=token, msg = kind|public_id|fingerprint|amount_minor|ts)` تا درخواست قابل دستکاری/بازپخش
  نباشد. سرور `ts` را فقط در بازهٔ ±۵ دقیقه می‌پذیرد.
- **Idempotent بر اساس `fingerprint`:** دوباره‌ارسالی همان جواب قبلی را می‌دهد و تأیید دوم نمی‌سازد.

| کد | معنی | رفتار اپ |
|---|---|---|
| `200 {"ok":true,"status":"approved"}` | تأیید شد | واریز = `MATCHED` |
| `200 {"status":"duplicate"}` | قبلاً انجام شده | واریز = `MATCHED` |
| `409 {"status":"conflict","reason":"..."}` | سرور مخالفت کرد (بخش ۵-۱) | واریز = `MANUAL`، نوتیفیکیشن به کاربر، **ریترای نکن** |
| `401` | توکن/امضا غلط | طبق قرارداد قبلی: بدون ریترای، نوتیفیکیشن |
| `429` / 5xx / شبکه | موقت | `RETRY` با backoff طبق ROADMAP |

### ۵-۱) قفل‌های ایمنی سرور — **اجباری، حذف‌شدنی نیست**
حتی وقتی اپ تأیید را می‌زند، سرور قبل از اجرا باید دوباره بررسی کند (چون توکن روی گوشی است و گوشی می‌تواند گم/هک شود):
1. `fingerprint` قبلاً مصرف نشده (جدول `SmsDeposit`، unique) و **قبل از تأیید** مصرف‌شده علامت می‌خورد.
2. سرور `sms` خام را با `sms_parse.parse` **دوباره پارس می‌کند** و `amount_minor` باید با ادعای اپ یکی باشد.
3. پرداخت `public_id` هنوز در وضعیت منتظر است و `due_minor == amount_minor`.
4. پرداخت قبل از زمان SMS ساخته شده (با ساعت سرور و تلورانس ۲ دقیقه).
5. سقف `SMS_AUTO_MAX_TOMAN` و `SMS_AUTO_APPROVE` همچنان اعمال شود.
6. لاگ حسابرسی: `AuditEvent(action="payment.sms_app_approve", actor=device)`.
7. سقف نرخ: مثلاً حداکثر N تأیید در ساعت از هر توکن؛ بیشتر از آن → `429` و هشدار به ادمین.

> بدون این قفل‌ها، هر کسی که توکن را از گوشی بردارد می‌تواند بدون پرداخت پنل «تأیید» کند. اپ تصمیم را می‌گیرد،
> ولی سرور اجازهٔ اجرا را می‌دهد.

---

## ۶) داده و وضعیت‌ها (Room)

`SmsRecord`: `id, fingerprint(unique), bank, body(raw), normalized, amountMinor?, reason, status, matchedKind?, matchedId?, receivedMs, decidedMs?, attempts, lastError?`

`status`: `NEW | IGNORED | MATCHED | MANUAL | AMBIGUOUS | EXPIRED | RETRY | FAILED`

- ذخیره ۳۰ روز، سپس پاک‌سازی خودکار (مثل `KEEP_DAYS`).
- `unique(fingerprint)`؛ درج تکراری = `duplicate` و بی‌صدا نادیده.
- تغییر وضعیت `NEW → MATCHED` را **قبل از** تماس شبکه به `CLAIMED` ببر و در خطای موقت برگردان (مثل «claim first» در سرور).

---

## ۷) تنظیمات اپ (اضافه بر قبلی)

- سوئیچ «تأیید خودکار در اپ» (پیش‌فرض: **خاموش** → فقط تطبیق و نوتیفیکیشن «با سفارش X می‌خواند، تأیید کنید»؛ کاربر
  با یک دکمه تأیید می‌زند).
- سقف تومانی محلی (اگر خالی بود، همان `auto_max_toman` سرور).
- دکمهٔ «تست اتصال» همان قبلی (`تست اتصال`) + یک تست دوم برای `sms-pending`.
- هیچ توکن یا آدرسی هاردکد نشود؛ `EncryptedSharedPreferences` مثل قبل.

## ۸) محدودیت‌های اندروید که باید مدیریت شود

- `SmsReceiver` + `WorkManager` (expedited) برای اطمینان از اجرا؛ `RECEIVE_BOOT_COMPLETED` برای بازیابی صف.
- Android 13+: مجوز نوتیفیکیشن؛ Android 12+: محدودیت شروع foreground service از پس‌زمینه.
- درخواست «Ignore battery optimizations» برای گوشی‌های Xiaomi/Samsung/Huawei (راهنمای داخل اپ).
- گوشی خاموش/آفلاین: واریز در صف می‌ماند و بعد از آنلاین‌شدن تطبیق انجام می‌شود (`server_now_ms` را تازه بگیر).

## ۹) چک‌لیست تست

- [ ] تست parity: ۳۰ پیامک نمونه، خروجی اپ = خروجی `sms_parse.py`.
- [ ] یک واریز + یک سفارش هم‌مبلغ → `approved`؛ یک واریز + دو سفارش هم‌مبلغ → `AMBIGUOUS`.
- [ ] واریز قدیمی‌تر از سفارش → تأیید نشود.
- [ ] ارسال دوبارهٔ همان `sms-approve` → `duplicate`، بدون تأیید دوم.
- [ ] مبلغ دستکاری‌شده در درخواست → سرور `409` بدهد (قفل شمارهٔ ۲).
- [ ] قطع اینترنت → پیامک برسد → وصل شود → خودکار تطبیق و تأیید.
- [ ] ریبوت، Android 13+، توکن اشتباه (401)، `429`.
