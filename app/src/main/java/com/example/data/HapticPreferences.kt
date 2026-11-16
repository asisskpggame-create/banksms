package com.example.data

enum class HapticIntensity(
    val storageKey: String,
    val titleFa: String,
    val scale: Float,
    val baseAmplitude: Int
) {
    LIGHT(
        storageKey = "light",
        titleFa = "ملایم",
        scale = 0.45f,
        baseAmplitude = 95
    ),
    MEDIUM(
        storageKey = "medium",
        titleFa = "متوسط",
        scale = 0.75f,
        baseAmplitude = 175
    ),
    STRONG(
        storageKey = "strong",
        titleFa = "قوی",
        scale = 1.0f,
        baseAmplitude = 255
    );

    fun scaledAmplitude(rawAmplitude: Int): Int {
        if (rawAmplitude <= 0) return 0
        return (rawAmplitude * scale).toInt().coerceIn(30, 255)
    }

    companion object {
        fun fromStorageKey(key: String?): HapticIntensity =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: MEDIUM
    }
}

enum class HandshakeHapticPattern(
    val storageKey: String,
    val titleFa: String,
    val subtitleFa: String
) {
    CRISP_CLICK(
        storageKey = "crisp_click",
        titleFa = "تک‌پالس دقیق",
        subtitleFa = "یک ضربه کوتاه و واضح هنگام برقراری هندشیک موفق"
    ),
    DOUBLE_PULSE(
        storageKey = "double_pulse",
        titleFa = "دوپالس تأیید",
        subtitleFa = "دو ضربه سبک متوالی برای حس اتصال پایدار به سرور"
    ),
    RISING_CONFIRM(
        storageKey = "rising_confirm",
        titleFa = "تأیید پلکانی",
        subtitleFa = "سه پالس افزایشی نرم برای تکمیل موفق همگام‌سازی"
    );

    companion object {
        fun fromStorageKey(key: String?): HandshakeHapticPattern =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: DOUBLE_PULSE
    }
}

enum class FailedSyncHapticPattern(
    val storageKey: String,
    val titleFa: String,
    val subtitleFa: String
) {
    DOUBLE_THUD(
        storageKey = "double_thud",
        titleFa = "دوضربه‌ای سنگین",
        subtitleFa = "دو ضربه متمایز برای اعلام ناکامی در همگام‌سازی"
    ),
    TRIPLE_ALERT(
        storageKey = "triple_alert",
        titleFa = "هشدار سه‌ضربه‌ای",
        subtitleFa = "سه پالس سریع هشدار هنگام خطای ارسال یا قطع اتصال"
    ),
    LONG_BUZZ(
        storageKey = "long_buzz",
        titleFa = "لرزش ممتد خطا",
        subtitleFa = "یک ویبره پیوسته واضح برای تلاش‌های ناموفق همگام‌سازی"
    );

    companion object {
        fun fromStorageKey(key: String?): FailedSyncHapticPattern =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: TRIPLE_ALERT
    }
}

data class HapticWaveformSpec(
    val timingsMs: LongArray,
    val amplitudes: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HapticWaveformSpec) return false
        return timingsMs.contentEquals(other.timingsMs) && amplitudes.contentEquals(other.amplitudes)
    }

    override fun hashCode(): Int {
        var result = timingsMs.contentHashCode()
        result = 31 * result + amplitudes.contentHashCode()
        return result
    }

    companion object {
        fun forHandshake(
            pattern: HandshakeHapticPattern,
            intensity: HapticIntensity
        ): HapticWaveformSpec {
            val base = intensity.baseAmplitude
            return when (pattern) {
                HandshakeHapticPattern.CRISP_CLICK -> HapticWaveformSpec(
                    timingsMs = longArrayOf(0L, 26L),
                    amplitudes = intArrayOf(0, base)
                )
                HandshakeHapticPattern.DOUBLE_PULSE -> HapticWaveformSpec(
                    timingsMs = longArrayOf(0L, 22L, 55L, 32L),
                    amplitudes = intArrayOf(0, intensity.scaledAmplitude(210), 0, base)
                )
                HandshakeHapticPattern.RISING_CONFIRM -> HapticWaveformSpec(
                    timingsMs = longArrayOf(0L, 18L, 45L, 26L, 45L, 40L),
                    amplitudes = intArrayOf(
                        0,
                        intensity.scaledAmplitude(130),
                        0,
                        intensity.scaledAmplitude(195),
                        0,
                        base
                    )
                )
            }
        }

        fun forFailedSync(
            pattern: FailedSyncHapticPattern,
            intensity: HapticIntensity
        ): HapticWaveformSpec {
            val base = intensity.baseAmplitude
            return when (pattern) {
                FailedSyncHapticPattern.DOUBLE_THUD -> HapticWaveformSpec(
                    timingsMs = longArrayOf(0L, 50L, 70L, 65L),
                    amplitudes = intArrayOf(0, base, 0, base)
                )
                FailedSyncHapticPattern.TRIPLE_ALERT -> HapticWaveformSpec(
                    timingsMs = longArrayOf(0L, 38L, 55L, 38L, 55L, 62L),
                    amplitudes = intArrayOf(
                        0,
                        intensity.scaledAmplitude(220),
                        0,
                        intensity.scaledAmplitude(220),
                        0,
                        base
                    )
                )
                FailedSyncHapticPattern.LONG_BUZZ -> HapticWaveformSpec(
                    timingsMs = longArrayOf(0L, 125L),
                    amplitudes = intArrayOf(0, base)
                )
            }
        }
    }
}
