package com.example.blocker.core

enum class SessionMode { NORMAL, STRICT }

enum class SessionStatus { ACTIVE, EXIT_PENDING, COMPLETED, INTERRUPTED }

/** گزینه‌های cooling-off خروج از Strict؛ مقدار در لحظه‌ی فعال‌سازی داخل سشن قفل می‌شود. */
enum class CoolingOff(val millis: Long, val labelFa: String) {
    MIN_30(30L * 60_000L, "۳۰ دقیقه"),
    HOURS_2(2L * 60L * 60_000L, "۲ ساعت"),
    HOURS_24(24L * 60L * 60_000L, "۲۴ ساعت")
}

/** گزینه‌های مجاز دیلی بلاک (ثانیه). پیش‌فرض ۵. */
object BlockingDelays {
    val ALLOWED_SECONDS = listOf(0, 3, 5, 10, 15)
    const val DEFAULT_SECONDS = 5
    fun sanitize(seconds: Int): Int = if (seconds in ALLOWED_SECONDS) seconds else DEFAULT_SECONDS
}

/**
 * رکورد یک سشن فوکوس. snapshot کانفیگ در لحظه‌ی شروع ذخیره می‌شود تا تغییر
 * پروفایل/قوانین در میانه‌ی سشن Strict نتواند آن را بی‌صدا ضعیف کند.
 *
 * سیاست ساعت (مستند و تست‌شده):
 * - در همان بوت، زمان سپری‌شده با ساعت monotonic ‏(elapsedRealtime) حساب می‌شود.
 * - اگر بوت عوض شده باشد (ریبوت)، مبنا ساعت دیواری (wall clock) است: سشن دقیقاً
 *   در [endWallMillis] تمام می‌شود، چه گوشی روشن بوده باشد چه خاموش.
 * - تغییر دستی ساعت دیواری در همان بوت اثری ندارد چون مبنا monotonic است؛
 *   بعد از ریبوت تنها مبنای موجود wall clock است و این محدودیت مستند است.
 */
data class SessionRecord(
    val id: String,
    val mode: SessionMode,
    val status: SessionStatus,
    val profileName: String,
    val durationMillis: Long,
    val startWallMillis: Long,
    val startElapsedMillis: Long,
    /** شمارنده‌ی بوت دستگاه (Settings.Global.BOOT_COUNT) در لحظه‌ی شروع؛ ‎-1 اگر در دسترس نبود. برای تشخیص ریبوت در محاسبه‌ی زمان باقی‌مانده. */
    val startBootWallMillis: Long,
    val endWallMillis: Long,
    val delaySeconds: Int,
    val coolingOff: CoolingOff,
    /** snapshot قوانین/اپ‌ها/سایت‌ها به‌صورت JSON (ساخته‌شده توسط لایه‌ی داده). */
    val configSnapshotJson: String,
    val exitRequestedAtWallMillis: Long = 0L,
    val exitDeadlineWallMillis: Long = 0L,
    val completedAtWallMillis: Long = 0L,
    val schemaVersion: Int = 1
)

/** محاسبات زمان سشن؛ خالص و تست‌پذیر. همه‌ی ورودی‌ها را لایه‌ی اندروید می‌دهد. */
object SessionTimeCalculator {

    /**
     * زمان باقی‌مانده.
     * @param sameBoot آیا از لحظه‌ی شروع تا الان ریبوتی رخ داده؟ لایه‌ی اندروید با
     * مقایسه‌ی [SessionRecord.startElapsedMillis] و elapsed فعلی + پرچم بوت ذخیره‌شده تعیین می‌کند.
     */
    fun remainingMillis(
        session: SessionRecord,
        nowWallMillis: Long,
        nowElapsedMillis: Long,
        sameBoot: Boolean
    ): Long {
        return if (sameBoot) {
            val elapsed = nowElapsedMillis - session.startElapsedMillis
            (session.durationMillis - elapsed).coerceAtLeast(0L)
        } else {
            (session.endWallMillis - nowWallMillis).coerceAtLeast(0L)
        }
    }

    fun isExpired(
        session: SessionRecord,
        nowWallMillis: Long,
        nowElapsedMillis: Long,
        sameBoot: Boolean
    ): Boolean = remainingMillis(session, nowWallMillis, nowElapsedMillis, sameBoot) <= 0L

    /**
     * تشخیص ریبوت: اگر elapsed فعلی از elapsed لحظه‌ی شروع کوچک‌تر باشد، حتماً ریبوت رخ داده.
     * (elapsed بعد از ریبوت از صفر شروع می‌شود و در یک سشن معقول از مقدار شروع جلو نمی‌زند،
     * مگر سشن خیلی طولانی؛ برای اطمینان، لایه‌ی اندروید boot count را هم نگه می‌دارد و
     * sameBoot را خودش حساب می‌کند — این تابع فقط fallback منطقی است.)
     */
    fun likelySameBoot(session: SessionRecord, nowElapsedMillis: Long): Boolean =
        nowElapsedMillis >= session.startElapsedMillis

    /** آیا درخواست خروج از Strict به deadline رسیده و می‌تواند نهایی شود؟ */
    fun exitReady(session: SessionRecord, nowWallMillis: Long): Boolean =
        session.status == SessionStatus.EXIT_PENDING &&
            session.exitDeadlineWallMillis > 0L &&
            nowWallMillis >= session.exitDeadlineWallMillis

    /** deadline خروج = لحظه‌ی درخواست + cooling-off قفل‌شده در سشن. */
    fun exitDeadline(session: SessionRecord, requestedAtWallMillis: Long): Long =
        requestedAtWallMillis + session.coolingOff.millis
}

/** سطح واقعی محافظت که در UI نمایش داده می‌شود؛ هیچ‌وقت «نشکن» نداریم. */
enum class ProtectionLevel(val labelFa: String, val descriptionFa: String) {
    STANDARD("محافظت استاندارد", "سرویس دسترسی فعال است؛ اپ معمولی می‌تواند از تنظیمات خاموش یا حذف شود."),
    ENHANCED("مقاومت پس‌زمینه‌ی تقویت‌شده", "سرویس دسترسی + سرویس پیش‌زمینه + معافیت باتری و ریکاوری بوت فعال‌اند."),
    MANAGED("اجرای مدیریت‌شده (Device Owner)", "اپ Device Owner است و بلاک حذف برای آن فعال و راستی‌آزمایی شده است."),
    DEGRADED("محافظت ناقص یا متوقف", "یک یا چند جزء لازم کار نمی‌کند؛ اجرا تضمین‌شده نیست.")
}
