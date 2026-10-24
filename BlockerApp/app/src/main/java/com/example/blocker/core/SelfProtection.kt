package com.example.blocker.core

/**
 * منطق خالص «محافظت از خودِ اپ» (راهِ بدون فکتوری‌ریست):
 * وقتی سشنی فعال است، باز شدنِ دقیقاً صفحه‌ی جزئیاتِ سرویسِ دسترسیِ خودِ «سد»
 * در تنظیمات (جایی که دکمه‌ی خاموش/روشن سرویس است) با back بسته می‌شود.
 *
 * ریل‌های ایمنی — مهم، چون کاربر صراحتاً خواسته اپ باگی نداشته باشد که کل گوشی را قفل کند:
 *  - گارد فقط وقتی سشنی فعال است کار می‌کند؛ با پایان سشن (همه‌ی سشن‌ها، حتی ۹۰ روزه،
 *    بالاخره تمام می‌شوند) گارد خودبه‌خود خاموش می‌شود.
 *  - گارد فقط صفحه‌ی جزئیاتِ همین سرویس را می‌شناسد: لیبل اپ + نشانه‌ی توضیح سرویس +
 *    وجود یک Switch در درخت. صفحه‌ی اصلی تنظیمات، لیست سرویس‌ها و صفحه‌ی سرویس‌های
 *    دیگر این سه نشانه را با هم ندارند و هرگز بسته نمی‌شوند (fail-open: در شک، گارد
 *    فعال نمی‌شود).
 *  - عمل فقط GLOBAL_ACTION_BACK است؛ هرگز HOME یا قفل صفحه. بستن یک صفحه با back
 *    نمی‌تواند گوشی را قفل کند و کاربر با ورود دوباره هنوز می‌تواند به همان صفحه برسد.
 *  - گارد فقط وقتی سرویس روشن است می‌تواند اجرا شود (سرویس خاموش اصلاً رخداد نمی‌گیرد)؛
 *    پس مسیر «رفتن به همان صفحه برای روشن کردن سرویس» همیشه باز است.
 *  - فرارهای همیشه‌باز: پایان سشن، Safe Mode، ADB، صفحه‌ی ریکاوری اضطراری، و خاموش
 *    کردن گارد از داخل خودِ اپ (تنظیمات).
 * صداقت فنی: این «اصطکاک» است، نه قفل سیستم — از Safe Mode یا adb دور زده می‌شود
 * و بعد از Force Stop تا باز شدن دوباره‌ی اپ کار نمی‌کند.
 */
object SelfProtection {

    /** پکیج‌هایی که صفحه‌ی موردنظرمان ممکن است در آن‌ها باز شود. */
    val SETTINGS_PACKAGES = setOf("com.android.settings")
    val PACKAGE_INSTALLER_PACKAGES = setOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller"
    )

    /**
     * نشانه‌ی توضیح سرویس: بخشی از رشته‌ی accessibility_service_description که فقط
     * روی صفحه‌ی جزئیاتِ همین سرویس نمایش داده می‌شود. اگر متن توضیح عوض شد، این
     * نشانه هم باید زیررشته‌ی توضیح جدید بماند؛ در غیر این صورت گارد در جهت امن
     * (عدم فعال‌شدن) از کار می‌افتد، نه در جهت قفل کردن.
     */
    const val DESCRIPTION_MARKER = "مسدود کردن برنامه‌ها"

    /** آیا پکیجِ پیش‌رو اصلاً کاندیدای بررسی خودمحافظتی است؟ */
    fun isRelevantForeground(packageName: String): Boolean =
        packageName in SETTINGS_PACKAGES || packageName in PACKAGE_INSTALLER_PACKAGES

    /**
     * آیا متنِ این صفحه‌ی سیستمی درباره‌ی همین اپ است؟
     * با مرز کلمه، تا اسمِ اپِ دیگری که فقط شامل لیبل ماست (مثل «سدها» در برابر
     * «سد») به اشتباه مسدود نشود. آستانه‌ی طول ۲ است چون لیبل خودِ اپ («سد»)
     * دو حرفی است؛ مرز کلمه همان محافظ در برابر تطبیق‌های اتفاقی است.
     */
    fun screenTargetsOwnApp(screenText: String, ownPackage: String, ownAppLabel: String): Boolean {
        if (ownPackage.isBlank()) return false
        if (containsWord(screenText, ownPackage)) return true
        val label = ownAppLabel.trim()
        return label.length >= 2 && containsWord(screenText, label)
    }

    /** جست‌وجوی عبارت با مرز کلمه (حروف/ارقام یونیکد، از جمله فارسی). */
    private fun containsWord(text: String, word: String): Boolean {
        if (word.isBlank()) return false
        var idx = text.indexOf(word)
        while (idx >= 0) {
            val before = if (idx == 0) ' ' else text[idx - 1]
            val afterIdx = idx + word.length
            val after = if (afterIdx >= text.length) ' ' else text[afterIdx]
            if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
            idx = text.indexOf(word, idx + 1)
        }
        return false
    }

    /**
     * آیا این صفحه دقیقاً صفحه‌ی جزئیاتِ سرویسِ دسترسیِ خودمان است؟
     * هر سه نشانه لازم است: لیبل اپ (کلمه‌ی کامل)، نشانه‌ی توضیح سرویس، و یک Switch.
     * لیست سرویس‌ها توضیح کامل را نشان نمی‌دهد؛ صفحه‌ی اصلی تنظیمات لیبل ما را ندارد؛
     * صفحه‌ی سرویسِ دیگر نه لیبل ما را دارد نه توضیح ما را.
     */
    fun isOwnServiceDetailPage(
        screenText: String,
        ownAppLabel: String,
        descriptionMarker: String,
        hasSwitch: Boolean
    ): Boolean {
        if (!hasSwitch) return false
        if (descriptionMarker.isBlank()) return false
        if (!screenText.contains(descriptionMarker)) return false
        val label = ownAppLabel.trim()
        return label.length >= 2 && containsWord(screenText, label)
    }

    /**
     * تصمیم نهایی برای صفحه‌های تنظیمات. فقط وقتی true برمی‌گردد که همه‌ی شرط‌ها
     * برقرار باشند: سشن فعال (هر حالت)، گارد روشن، پکیج تنظیمات، و صفحه دقیقاً
     * جزئیاتِ سرویسِ خودمان.
     */
    fun shouldGuardSettingsPage(
        sessionActive: Boolean,
        selfProtectionEnabled: Boolean,
        foregroundPackage: String,
        ownPackage: String,
        ownAppLabel: String,
        descriptionMarker: String,
        screenText: String,
        hasSwitch: Boolean
    ): Boolean {
        if (!sessionActive || !selfProtectionEnabled) return false
        if (foregroundPackage == ownPackage) return false
        if (foregroundPackage !in SETTINGS_PACKAGES) return false
        return isOwnServiceDetailPage(screenText, ownAppLabel, descriptionMarker, hasSwitch)
    }

    /**
     * تصمیم نهایی برای دیالوگ حذف در نصاب. دیالوگ گذراست و با back فقط بسته می‌شود؛
     * قاعده‌ی قدیمی (لیبل/پکیج اپ در متن دیالوگ) حفظ شده است.
     */
    fun shouldGuardInstallerDialog(
        sessionActive: Boolean,
        selfProtectionEnabled: Boolean,
        foregroundPackage: String,
        ownPackage: String,
        ownAppLabel: String,
        screenText: String
    ): Boolean {
        if (!sessionActive || !selfProtectionEnabled) return false
        if (foregroundPackage == ownPackage) return false
        if (foregroundPackage !in PACKAGE_INSTALLER_PACKAGES) return false
        return screenTargetsOwnApp(screenText, ownPackage, ownAppLabel)
    }

    /**
     * آیا سرویس دسترسی ما در رشته‌ی ENABLED_ACCESSIBILITY_SERVICES سیستم هست؟
     * ورودی‌ها هر دو شکل ثبت سرویس را پوشش می‌دهند: «pkg/.Class» و «pkg/pkg.Class».
     */
    fun isServiceEnabledInList(enabledServices: String?, ownPackage: String, serviceClass: String): Boolean {
        if (enabledServices.isNullOrBlank()) return false
        val shortName = serviceClass.removePrefix(".")
        return enabledServices.split(':').any { entry ->
            val cls = entry.trim().substringAfter('/', "")
            cls == serviceClass || cls == "$ownPackage.$shortName" || cls == shortName
        }
    }
}
