package com.example.blocker.core

/** بازه‌ی مجاز مدت سشن: از ۳۰ ثانیه تا ۹۰ روز. ورودیِ خارج از بازه رد می‌شود (نه clamp پنهانی). */
object DurationRange {
    const val MIN_MILLIS: Long = 30_000L
    const val MAX_MILLIS: Long = 90L * 24L * 60L * 60L * 1000L // ۹۰ روز

    fun isValid(durationMillis: Long): Boolean = durationMillis in MIN_MILLIS..MAX_MILLIS

    /** برچسب فارسی واحدهای انتخاب مدت در فرم ساخت سشن. */
    enum class Unit(val labelFa: String, val millis: Long) {
        SECOND("ثانیه", 1_000L),
        MINUTE("دقیقه", 60_000L),
        HOUR("ساعت", 3_600_000L),
        DAY("روز", 86_400_000L)
    }
}
