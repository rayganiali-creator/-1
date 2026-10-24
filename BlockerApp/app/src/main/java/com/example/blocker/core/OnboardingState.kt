package com.example.blocker.core

/**
 * ماشین وضعیت راه‌اندازی «سد» — خالص و تست‌پذیر؛ هیچ وابستگی به اندروید ندارد.
 *
 * اصول (از بازطراحی آنبوردینگ):
 * - هر قدم فقط با «راستی‌آزمایی واقعی وضعیت» کامل می‌شود، نه با زدن دکمه یا
 *   برگشت از تنظیمات. بازگشت از تنظیمات فقط باعث بازخوانی وضعیت می‌شود.
 * - قدم «تنظیمات محدودشده» (Allow restricted settings) فقط وقتی نشان داده
 *   می‌شود که سیگنال‌های قابل‌اعتماد بگویند لازم است: اندروید ۱۳ به بالا و
 *   نصب از منبعی غیر از فروشگاه‌های شناخته‌شده. روی دستگاهی که لازم نیست،
 *   این قدم اصلاً ظاهر نمی‌شود (نه اینکه «اجباری» نشان داده شود).
 * - قدم‌های اختیاری (باتری، نوتیفیکیشن، آلارم دقیق، پیکربندی) قابل رد کردن‌اند؛
 *   قدم‌های ضروری (دسترسی) قابل رد کردن نیستند.
 * - پیشرفت از وضعیت‌های راستی‌آزمایی‌شده بازمحاسبه می‌شود؛ پس برگشت، ری‌استارت
 *   یا لغو بعدیِ دسترسی، پیشرفتِ معتبر را پاک نمی‌کند — فقط همان قدم را دوباره
 *   «اقدام لازم» نشان می‌دهد (جریان تعمیر متمرکز، نه تکرار کل آموزش).
 */
object OnboardingState {

    enum class StepId { RESTRICTED_SETTINGS, ACCESSIBILITY, NOTIFICATIONS, BATTERY, EXACT_ALARMS, CONFIG }

    enum class StepStatus { DONE, ACTION_REQUIRED, NOT_APPLICABLE }

    data class SetupStep(
        val id: StepId,
        val titleFa: String,
        val required: Boolean,
        val status: StepStatus
    )

    /** سیگنال‌های دستگاه که از لایه‌ی اندروید می‌آیند (نه حدس). */
    data class DeviceSignals(
        val apiLevel: Int,
        /** پکیج نصب‌کننده از PackageManager؛ null یعنی نصب سایدلود/نامشخص. */
        val installerPackage: String?,
        val manufacturer: String
    )

    /** وضعیت‌های راستی‌آزمایی‌شده در لحظه‌ی رندر. */
    data class VerifiedStates(
        val a11y: Boolean,
        val notifications: Boolean,
        val battery: Boolean,
        val exactAlarms: Boolean,
        val hasConfig: Boolean,
        val apiLevel: Int
    )

    private val STORE_INSTALLERS = setOf(
        "com.android.vending", // Google Play
        "ir.mservices.market", // Myket
        "com.farsitel.bazaar" // Bazaar
    )

    /**
     * آیا قدم «تنظیمات محدودشده» احتمالاً لازم است؟
     * - اندروید ۱۳ (API 33) به بعد این قفل را دارد؛ قبل از آن اصلاً مطرح نیست.
     * - نصب از فروشگاه شناخته‌شده → قفل اعمال نمی‌شود → لازم نیست.
     * - نصب سایدلود (installer نامشخص/مرورگر/مدیر فایل) → احتمالاً لازم است.
     * این «احتمال» است نه قطعیت؛ شاخه‌ی عیب‌یابی همیشه در دسترس است.
     */
    fun restrictedSettingsLikely(signals: DeviceSignals): Boolean {
        if (signals.apiLevel < 33) return false
        val installer = signals.installerPackage ?: return true
        return installer !in STORE_INSTALLERS
    }

    fun isSamsung(signals: DeviceSignals): Boolean =
        signals.manufacturer.equals("samsung", ignoreCase = true)

    /** فهرست قدم‌ها به ترتیب اجرا؛ قدم‌های نامربوط NOT_APPLICABLE می‌شوند و شمرده نمی‌شوند. */
    fun buildSteps(signals: DeviceSignals, verified: VerifiedStates): List<SetupStep> {
        val restrictedLikely = restrictedSettingsLikely(signals)
        return listOf(
            SetupStep(
                StepId.RESTRICTED_SETTINGS,
                "باز کردن قفل تنظیمات محدودشده",
                required = false, // فقط پیش‌نیاز قدم دسترسی است، نه مجوز مستقل
                status = when {
                    verified.a11y -> StepStatus.NOT_APPLICABLE // سرویس روشن است؛ قفل دیگر موضوعیت ندارد
                    restrictedLikely -> StepStatus.ACTION_REQUIRED
                    else -> StepStatus.NOT_APPLICABLE
                }
            ),
            SetupStep(
                StepId.ACCESSIBILITY,
                "روشن کردن سرویس دسترسی",
                required = true,
                status = if (verified.a11y) StepStatus.DONE else StepStatus.ACTION_REQUIRED
            ),
            SetupStep(
                StepId.NOTIFICATIONS,
                "اجازه‌ی نوتیفیکیشن",
                required = false,
                status = when {
                    verified.apiLevel < 33 -> StepStatus.NOT_APPLICABLE
                    verified.notifications -> StepStatus.DONE
                    else -> StepStatus.ACTION_REQUIRED
                }
            ),
            SetupStep(
                StepId.BATTERY,
                "معافیت باتری (توصیه‌شده)",
                required = false,
                status = if (verified.battery) StepStatus.DONE else StepStatus.ACTION_REQUIRED
            ),
            SetupStep(
                StepId.EXACT_ALARMS,
                "آلارم دقیق برای زمان‌بندی‌ها",
                required = false,
                status = when {
                    verified.apiLevel < 31 -> StepStatus.NOT_APPLICABLE
                    verified.exactAlarms -> StepStatus.DONE
                    else -> StepStatus.ACTION_REQUIRED
                }
            ),
            SetupStep(
                StepId.CONFIG,
                "انتخاب چیزهایی که بلاک شوند",
                required = false,
                status = if (verified.hasConfig) StepStatus.DONE else StepStatus.ACTION_REQUIRED
            )
        )
    }

    /** قدم‌های قابل‌نمایش (بدون NOT_APPLICABLE). */
    fun visibleSteps(steps: List<SetupStep>): List<SetupStep> =
        steps.filter { it.status != StepStatus.NOT_APPLICABLE }

    /**
     * قدم جاری ویزارد: اولین قدم «اقدام لازم» به ترتیب فهرست. ترتیب فهرست خودش
     * ترتیب اجراست (پیش‌نیاز «تنظیمات محدودشده» قبل از قدم دسترسی می‌آید)؛
     * پس «ضروری اول» لازم نیست و فقط ترتیب را خراب می‌کند. null یعنی همه کامل.
     */
    fun currentStep(steps: List<SetupStep>): SetupStep? {
        val visible = visibleSteps(steps)
        return visible.firstOrNull { it.status == StepStatus.ACTION_REQUIRED }
    }

    /** «X از Y قدم کامل شده» — فقط قدم‌های قابل‌نمایش شمرده می‌شوند. */
    fun progressText(steps: List<SetupStep>): String {
        val visible = visibleSteps(steps)
        val done = visible.count { it.status == StepStatus.DONE }
        return "$done از ${visible.size} قدم کامل شده"
    }

    /** عیب‌یابی هر قدم: هر مورد «یک مشکل → یک اقدام» است، نه دیوار متن. */
    fun troubleshooting(stepId: StepId, samsung: Boolean): List<Pair<String, String>> {
        val a11yListLabel = if (samsung) "Installed apps" else "Downloaded apps"
        return when (stepId) {
            StepId.RESTRICTED_SETTINGS -> listOf(
                "«سد» در لیست نیست" to "اول مطمئن شو اپ نصب شده و یک بار بازش کرده‌ای؛ بعد دوباره اطلاعات برنامه را باز کن.",
                "سه‌نقطه (⋮) دیده نمی‌شود" to "بالا سمت راست صفحه‌ی اطلاعات برنامه را بگرد؛ در بعضی نسخه‌ها منو پایین صفحه است.",
                "«Allow restricted settings» نیست" to "یعنی این قفل روی گوشی تو فعال نیست؛ قدم را رد کن و مستقیم سراغ روشن کردن سرویس برو.",
                "بعد از Allow هنوز دکمه خاکستری است" to "به عقب برگرد و دوباره وارد صفحه‌ی سرویس شو؛ گاهی یک بار خروج و ورود لازم است."
            )
            StepId.ACCESSIBILITY -> listOf(
                "«سد» در لیست سرویس‌ها نیست" to "در تنظیمات دسترسی، بخش «$a11yListLabel» (یا «نصب‌شده‌ها») را باز کن؛ سرویس‌های دانلودی آن‌جا هستند، نه در لیست اصلی.",
                "دکمه خاکستری و غیرقابل‌زدن است" to "قدم «تنظیمات محدودشده» را انجام بده (یک قدم قبل)؛ تا آن باز نشود، اندروید اجازه نمی‌دهد.",
                "هشدار امنیتی اندروید آمد" to "هشدار را بخوان: می‌گوید سد می‌تواند متن صفحه را ببیند. این دسترسی برای بلاک لازم است و متن جایی ذخیره یا ارسال نمی‌شود؛ فقط اگر راضی هستی «Allow» را بزن.",
                "روشن کردم ولی سد هنوز خاموش نشان می‌دهد" to "دکمه‌ی «بررسی دوباره» را بزن؛ اگر هنوز خاموش بود، یک بار از تنظیمات خارج شو و دوباره وارد شو.",
                "بعد از ری‌استارت یا آپدیت خاموش شد" to "بعد از نصب مجدد یا آپدیت بزرگ، اندروید گاهی دسترسی را می‌اندازد؛ همین قدم را دوباره انجام بده (جریان تعمیر)."
            )
            StepId.NOTIFICATIONS -> listOf(
                "درخواستی نمایش داده نشد" to "از تنظیمات گوشی: اطلاعات برنامه‌ی سد → Notifications را دستی روشن کن.",
                "قبلاً رد کرده‌ام" to "اندروید درخواست تکراری نشان نمی‌دهد؛ از اطلاعات برنامه، Notifications را دستی فعال کن."
            )
            StepId.BATTERY -> listOf(
                "گزینه‌ی معافیت نیست" to "در اطلاعات برنامه: Battery را باز کن و روی Unrestricted بگذار؛ همین اثر را دارد.",
                "سامسونگ هنوز می‌کشد" to "علاوه بر این، در Battery → Background usage limits → Never sleeping apps، «سد» را اضافه کن."
            )
            StepId.EXACT_ALARMS -> listOf(
                "گزینه نیست" to "در این نسخه‌ی اندروید لازم نیست؛ قدم را رد کن.",
                "بدون آن چه می‌شود؟" to "فقط زمان‌بندی‌های خودکار ممکن است با تأخیر شروع شوند؛ سشن دستی همیشه کار می‌کند."
            )
            StepId.CONFIG -> listOf(
                "نمی‌دانم چه بلاک کنم" to "با یک قانون شروع کن: همان کلمه‌ای که بیشتر وسوسه‌ات می‌کند؛ بعداً قابل تغییر است."
            )
        }
    }
}
