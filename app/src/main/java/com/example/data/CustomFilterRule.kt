package com.example.data

import java.util.Locale

enum class FilterTarget(val storageKey: String, val labelFa: String, val hintFa: String) {
    BODY_KEYWORD(
        storageKey = "body_keyword",
        labelFa = "کلمه در متن پیامک",
        hintFa = "مثلاً: سود، قسط، کارمزد، تبلیغ"
    ),
    SENDER_NUMBER(
        storageKey = "sender_number",
        labelFa = "سرشماره / فرستنده",
        hintFa = "مثلاً: 985000 یا تبلیغاتی"
    ),
    BODY_OR_SENDER(
        storageKey = "body_or_sender",
        labelFa = "متن یا فرستنده",
        hintFa = "تطبیق هم‌زمان در متن و سرشماره"
    ),
    MIN_AMOUNT_TOMAN(
        storageKey = "min_amount_toman",
        labelFa = "حداقل مبلغ (تومان)",
        hintFa = "مبالغ کمتر از این مقدار فیلتر می‌شوند (مثلاً 1000)"
    );

    companion object {
        fun fromStorageKey(key: String?): FilterTarget =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: BODY_KEYWORD
    }
}

enum class FilterAction(val storageKey: String, val labelFa: String, val shortFa: String) {
    IGNORE_LOG_ONLY(
        storageKey = "ignore_log_only",
        labelFa = "ثبت در لاگ به عنوان نادیده (بدون ارسال به سرور)",
        shortFa = "فقط ثبت محلی"
    ),
    DROP_SILENTLY(
        storageKey = "drop_silently",
        labelFa = "حذف کامل و بی‌صدا (بدون ثبت در لاگ و سرور)",
        shortFa = "حذف کامل"
    );

    companion object {
        fun fromStorageKey(key: String?): FilterAction =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: IGNORE_LOG_ONLY
    }
}

data class CustomFilterRule(
    val id: String,
    val name: String,
    val pattern: String,
    val target: FilterTarget = FilterTarget.BODY_KEYWORD,
    val action: FilterAction = FilterAction.IGNORE_LOG_ONLY,
    val enabled: Boolean = true,
    val hitCount: Long = 0L,
    val createdAtMs: Long = System.currentTimeMillis()
) {
    fun displayTitle(): String = name.ifBlank {
        when (target) {
            FilterTarget.MIN_AMOUNT_TOMAN -> {
                val toman = pattern.toLongOrNull() ?: 0L
                "مبالغ کمتر از ${SmsParser.formatRial(toman)} تومان"
            }
            else -> pattern
        }
    }
}

data class CustomFilterMatchResult(
    val rule: CustomFilterRule,
    val reasonDetailFa: String
)

object SmsFilterEngine {

    /**
     * Normalizes Persian/Arabic letter variants (ي/ى -> ی, ك -> ک) and digits so keyword
     * rules match reliably regardless of bank SMS encoding.
     */
    fun normalizeForMatch(input: String?): String {
        val base = SmsParser.normalize(input)
        if (base.isEmpty()) return ""
        val sb = StringBuilder(base.length)
        for (ch in base) {
            when (ch) {
                '\u064A', '\u0649' -> sb.append('\u06CC') // ي / ى -> ی
                '\u0643' -> sb.append('\u06A9')           // ك -> ک
                else -> sb.append(ch)
            }
        }
        return sb.toString().lowercase(Locale.ROOT)
    }

    /**
     * Evaluates active custom rules against an incoming SMS.
     * Pass [amountMinor] = null before parsing (to short-circuit keyword/sender rules early),
     * and pass the parsed [amountMinor] after parsing to also evaluate [FilterTarget.MIN_AMOUNT_TOMAN].
     */
    fun evaluate(
        sender: String,
        rawBody: String,
        amountMinor: Long?,
        settings: GatewaySettings
    ): CustomFilterMatchResult? {
        if (!settings.customFilterRulesEnabled || settings.customFilterRules.isEmpty()) {
            return null
        }

        val normSender = normalizeForMatch(sender)
        val normBody = normalizeForMatch(rawBody)

        for (rule in settings.customFilterRules) {
            if (!rule.enabled) continue
            val rawPattern = rule.pattern.trim()
            if (rawPattern.isEmpty()) continue

            when (rule.target) {
                FilterTarget.BODY_KEYWORD -> {
                    val needle = normalizeForMatch(rawPattern)
                    if (needle.isNotEmpty() && normBody.contains(needle)) {
                        return CustomFilterMatchResult(
                            rule = rule,
                            reasonDetailFa = "فیلتر سفارشی (${rule.displayTitle()}): تطبیق کلمه در متن"
                        )
                    }
                }

                FilterTarget.SENDER_NUMBER -> {
                    val needle = normalizeForMatch(rawPattern)
                    if (needle.isNotEmpty() && normSender.contains(needle)) {
                        return CustomFilterMatchResult(
                            rule = rule,
                            reasonDetailFa = "فیلتر سفارشی (${rule.displayTitle()}): تطبیق سرشماره فرستنده"
                        )
                    }
                }

                FilterTarget.BODY_OR_SENDER -> {
                    val needle = normalizeForMatch(rawPattern)
                    if (needle.isNotEmpty() && (normBody.contains(needle) || normSender.contains(needle))) {
                        return CustomFilterMatchResult(
                            rule = rule,
                            reasonDetailFa = "فیلتر سفارشی (${rule.displayTitle()}): تطبیق در متن یا فرستنده"
                        )
                    }
                }

                FilterTarget.MIN_AMOUNT_TOMAN -> {
                    if (amountMinor != null) {
                        val minToman = rawPattern.replace(",", "").toLongOrNull() ?: continue
                        if (minToman > 0L && amountMinor < minToman * 10L) {
                            return CustomFilterMatchResult(
                                rule = rule,
                                reasonDetailFa = "فیلتر سفارشی: مبلغ (${SmsParser.formatToman(amountMinor)} ت) کمتر از حداقل ${SmsParser.formatRial(minToman)} تومان است"
                            )
                        }
                    }
                }
            }
        }
        return null
    }
}
