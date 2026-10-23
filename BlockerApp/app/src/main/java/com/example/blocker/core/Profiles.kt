package com.example.blocker.core

/** پروفایل فوکوس قابل استفاده‌ی مجدد. */
data class FocusProfile(
    val id: String,
    val name: String,
    val blockedApps: Set<String> = emptySet(),
    val keywordCategories: Set<String> = emptySet(),
    val durationMillis: Long = 25L * 60_000L,
    val delaySeconds: Int = BlockingDelays.DEFAULT_SECONDS,
    val mode: SessionMode = SessionMode.NORMAL,
    val coolingOff: CoolingOff = CoolingOff.HOURS_2,
    val builtin: Boolean = false
) {
    companion object {
        fun presets(): List<FocusProfile> = listOf(
            FocusProfile("preset-study", "مطالعه", durationMillis = 50L * 60_000L, builtin = true),
            FocusProfile("preset-exam", "آمادگی امتحان", durationMillis = 90L * 60_000L, mode = SessionMode.STRICT, builtin = true),
            FocusProfile("preset-work", "کار", durationMillis = 50L * 60_000L, builtin = true),
            FocusProfile("preset-deep", "تمرکز عمیق", durationMillis = 120L * 60_000L, mode = SessionMode.STRICT, builtin = true),
            FocusProfile("preset-sleep", "خواب", durationMillis = 8L * 60L * 60_000L, builtin = true)
        )
    }
}

/** یک زمان‌بندی سشن (یک‌باره یا هفتگی تکرارشونده با ماسک روزهای هفته؛ 0=شنبه … در عمل از Calendar استفاده می‌شود). */
data class SessionSchedule(
    val id: String,
    val profileId: String,
    val hour: Int,
    val minute: Int,
    /** روزهای هفته با قرارداد Calendar: 1=Sunday … 7=Saturday؛ خالی = یک‌باره در اولین زمان ممکن. */
    val daysOfWeek: Set<Int> = emptySet(),
    val enabled: Boolean = true
)

/** محاسبه‌ی زمان اجرای بعدی یک زمان‌بندی؛ خالص و تست‌پذیر (ورودی‌ها epoch millis UTC). */
object ScheduleCalculator {
    private const val DAY = 24L * 60L * 60_000L

    /**
     * ساده و قابل اتکا: مبنا را «روز جاری به وقت محلی» می‌گیریم که لایه‌ی اندروید با
     * Calendar حساب کرده و به صورت (startOfTodayWall, dayOfWeekToday) می‌دهد.
     * تابع اولین لحظه‌ی >= now را در روزهای مجاز برمی‌گرداند.
     */
    fun nextTriggerMillis(
        schedule: SessionSchedule,
        nowWallMillis: Long,
        startOfTodayWallMillis: Long,
        todayDayOfWeek: Int // Calendar.SUNDAY=1 .. SATURDAY=7
    ): Long {
        val timeOfDay = schedule.hour * 60L * 60_000L + schedule.minute * 60_000L
        if (schedule.daysOfWeek.isEmpty()) {
            val todayAt = startOfTodayWallMillis + timeOfDay
            return if (todayAt > nowWallMillis) todayAt else todayAt + DAY
        }
        for (offset in 0..7) {
            val day = ((todayDayOfWeek - 1 + offset) % 7) + 1
            if (day in schedule.daysOfWeek) {
                val candidate = startOfTodayWallMillis + offset * DAY + timeOfDay
                if (candidate > nowWallMillis) return candidate
            }
        }
        return startOfTodayWallMillis + 7 * DAY + timeOfDay // عملاً unreachable
    }

    /** تداخل دو بازه‌ی [start, start+duration). */
    fun overlaps(aStart: Long, aDuration: Long, bStart: Long, bDuration: Long): Boolean =
        aStart < bStart + bDuration && bStart < aStart + aDuration
}
