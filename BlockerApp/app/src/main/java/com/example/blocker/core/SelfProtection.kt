package com.example.blocker.core

/**
 * منطق خالص «محافظت از خودِ اپ» (راهِ بدون فکتوری‌ریست). دو سطح با دو قاعده‌ی متفاوت:
 *
 * ۱) گارد دیالوگ (حذف و توقف اجباری): هر وقت کلید خودمحافظتی روشن است — حتی بدون
 *    سشن — دیالوگ تأیید حذفِ «سد» (در نصاب یا تنظیمات) و دیالوگ تأیید توقف اجباریِ
 *    «سد» (در تنظیمات) با back بسته می‌شود. سشن لازم نیست، چون کاربر وقتی این
 *    کلید را روشن می‌کند دقیقاً همین را می‌خواهد: «نشه حذفش کرد، نشه فورس استاپش کرد».
 *    در سشن Strict، گارد برای همان سشن اجباری است حتی اگر کلید خاموش بوده باشد
 *    (isGuardEffective)؛ مقدار ذخیره‌شده دست نمی‌خورد.
 *
 * ۲) گارد صفحه‌ی سرویس دسترسی: فقط وسط سشن. صفحه‌ی جزئیات سرویسِ خودمان در
 *    تنظیمات (جایی که دکمه‌ی خاموش/روشن سرویس است) فقط وقتی سشنی فعال است بسته
 *    می‌شود؛ بیرون از سشن این صفحه همیشه باز است تا کاربر بتواند سرویس را مدیریت
 *    کند. این تفکیک، ریل ایمنیِ «اپ گوشی را قفل نکند» است.
 *
 * ۳) بستن صفحه‌ی اطلاعات اپ — فقط در Strict: بیرون از Strict، صفحه‌ی اطلاعات اپ
 *    هرگز بسته نمی‌شود؛ فقط خودِ دیالوگ. ولی در سشن Strict، خودِ صفحه‌ی اطلاعات
 *    «سد» هم با back بسته می‌شود تا دکمه‌های حذف و توقف اصلاً در دسترس نباشند،
 *    نه فقط دیالوگِ بعد از زدن دکمه.
 *
 * تفکیک صفحه از دیالوگ — مهم: صفحه‌ی اطلاعات اپ («سد» + دکمه‌های حذف و توقف
 * اجباری) خودش هر دو کلمه را دارد. پس گارد دیالوگ فقط وقتی عمل می‌کند که دقیقاً
 * یکی از دو کلمه باشد، نه هر دو: دیالوگ حذف فقط «حذف» دارد، دیالوگ توقف فقط
 * «توقف اجباری» دارد، و صفحه‌ی اطلاعات با این شرط از گارد دیالوگ کنار گذاشته
 * می‌شود (اما در Strict، خودِ صفحه با گارد جداگانه‌ی سطح ۳ بسته می‌شود).
 *
 * شناسایی دیالوگ توقف برای اپِ خودمان: دیالوگ AOSP نام اپ را ندارد، پس علاوه بر
 * تطبیق لیبل، یک پرچمِ «صفحه‌ی اطلاعاتِ خودِ اپ تازه دیده شده» هم قبول می‌شود:
 * سرویس وقتی صفحه‌ای در تنظیمات را می‌بیند که هم لیبل ما و هم یکی از دو دکمه را
 * دارد، زمانش را ثبت می‌کند؛ دیالوگی که تا ۵ دقیقه بعد در تنظیمات باز شود و فقط
 * کلمه‌ی توقف را داشته باشد، دیالوگِ ماست. هر صفحه‌ی دیگری در تنظیمات که این
 * ترکیب را نداشته باشد پرچم را پاک می‌کند — پس برای توقف اپِ دیگری، اول باید به
 * صفحه‌ی اطلاعات همان اپ رفت که پرچم را پاک می‌کند. در شک، گارد عمل نمی‌کند
 * (fail-open).
 *
 * ریل‌های ایمنی مشترک: عمل فقط GLOBAL_ACTION_BACK است؛ هرگز HOME یا قفل صفحه.
 * ضربه‌گیر ۱.۵ ثانیه. فرارهای همیشه‌باز: خاموش کردن کلید از داخل خودِ اپ
 * (بیرون از Strict؛ در Strict کلید قفل است)، پایان سشن (برای گاردهای سشنی)،
 * Safe Mode، ADB، صفحه‌ی ریکاوری اضطراری.
 * صداقت فنی: این «اصطکاک» است، نه قفل سیستم — از Safe Mode یا adb دور زده می‌شود
 * و بعد از Force Stop تا باز شدن دوباره‌ی اپ کار نمی‌کند، و وقتی سرویس دسترسی
 * خاموش است هیچ گاردی کار نمی‌کند. قفل واقعی فقط با Device Owner است.
 */
object SelfProtection {

    /** پکیج‌هایی که صفحه‌ی موردنظرمان ممکن است در آن‌ها باز شود. */
    val SETTINGS_PACKAGES = setOf("com.android.settings")
    val PACKAGE_INSTALLER_PACKAGES = setOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        // One UI سامسونگ ممکن است نصاب را با این پکیج‌ها نشان دهد؛ اگر وجود نداشته
        // باشند هیچ‌وقت تطبیق نمی‌شوند، پس بودنشان بی‌خطر است.
        "com.samsung.android.packageinstaller",
        "com.sec.android.packageinstaller"
    )

    /**
     * نشانه‌ی توضیح سرویس: بخشی از رشته‌ی accessibility_service_description که فقط
     * روی صفحه‌ی جزئیاتِ همین سرویس نمایش داده می‌شود. اگر متن توضیح عوض شد، این
     * نشانه هم باید زیررشته‌ی توضیح جدید بماند؛ در غیر این صورت گارد در جهت امن
     * (عدم فعال‌شدن) از کار می‌افتد، نه در جهت قفل کردن.
     */
    const val DESCRIPTION_MARKER = "مسدود کردن برنامه‌ها"

    /** پنجره‌ی تازگیِ پرچم صفحه‌ی اطلاعات اپ: دیالوگ باید بلافاصله بعد از آن باز شده باشد. */
    const val APP_INFO_FRESH_WINDOW_MS = 5 * 60_000L

    /**
     * آیا گارد در این لحظه اثر دارد؟ کلیدِ ذخیره‌شده، یا سشن Strict فعال:
     * Strict یعنی حداکثر محافظت برای همان سشن، حتی اگر کلید خاموش بوده باشد.
     * مقدار ذخیره‌شده دست نمی‌خورد و بعد از پایان سشن دوباره همان مقدار اثر دارد.
     */
    fun isGuardEffective(persistedOn: Boolean, strictActive: Boolean): Boolean =
        persistedOn || strictActive

    /** آیا پکیجِ پیش‌رو اصلاً کاندیدای بررسی خودمحافظتی است؟ */
    fun isRelevantForeground(packageName: String): Boolean =
        packageName in SETTINGS_PACKAGES || packageName in PACKAGE_INSTALLER_PACKAGES

    /** آیا متن، کلمه‌ی حذف را دارد؟ (فارسی «حذف» همه‌ی صورت‌های حذف/حذف نصب/حذف برنامه را می‌گیرد.) */
    fun containsUninstallWord(screenText: String): Boolean =
        screenText.contains("حذف") || screenText.contains("uninstall", ignoreCase = true)

    /** آیا متن، عبارت توقف اجباری را دارد؟ */
    fun containsForceStopWord(screenText: String): Boolean =
        screenText.contains("توقف اجباری") || screenText.contains("force stop", ignoreCase = true)

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
     * آیا این صفحه، صفحه‌ی اطلاعاتِ خودِ اپ در تنظیمات است؟ لیبل ما + یکی از دو
     * دکمه‌ی حذف/توقف. صفحه‌ی اطلاعات اپِ دیگر لیبل ما را ندارد و پاک‌کننده‌ی پرچم است.
     */
    fun isOwnAppInfoPage(screenText: String, ownPackage: String, ownAppLabel: String): Boolean {
        if (!screenTargetsOwnApp(screenText, ownPackage, ownAppLabel)) return false
        return containsUninstallWord(screenText) || containsForceStopWord(screenText)
    }

    /** آیا پرچم صفحه‌ی اطلاعات اپ هنوز تازه است؟ */
    fun isFlagFresh(lastOwnAppInfoPageTime: Long, now: Long): Boolean =
        lastOwnAppInfoPageTime > 0 && now - lastOwnAppInfoPageTime <= APP_INFO_FRESH_WINDOW_MS

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
     * جزئیاتِ سرویسِ خودمان. این تنها گاردی است که سشن می‌خواهد.
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
     * تصمیم نهایی برای دیالوگ حذف در نصاب. فقط کلید لازم است، سشن لازم نیست.
     * دیالوگ نصاب همیشه نام اپ را دارد، پس تطبیق لیبل/پکیج + کلمه‌ی حذف کافی است.
     */
    fun shouldGuardInstallerDialog(
        selfProtectionEnabled: Boolean,
        foregroundPackage: String,
        ownPackage: String,
        ownAppLabel: String,
        screenText: String
    ): Boolean {
        if (!selfProtectionEnabled) return false
        if (foregroundPackage == ownPackage) return false
        if (foregroundPackage !in PACKAGE_INSTALLER_PACKAGES) return false
        if (!containsUninstallWord(screenText)) return false
        return screenTargetsOwnApp(screenText, ownPackage, ownAppLabel)
    }

    /**
     * تصمیم نهایی برای دیالوگ حذفِ میزبانی‌شده در تنظیمات (برخی OEMها دیالوگ را
     * در خودِ تنظیمات نشان می‌دهند). فقط کلید لازم است. شرط «نه توقف» صفحه‌ی
     * اطلاعات اپ را کنار می‌گذارد، چون آن صفحه هر دو کلمه را دارد.
     */
    fun shouldGuardSettingsUninstallDialog(
        selfProtectionEnabled: Boolean,
        foregroundPackage: String,
        ownPackage: String,
        ownAppLabel: String,
        screenText: String,
        ownAppInfoPageFresh: Boolean
    ): Boolean {
        if (!selfProtectionEnabled) return false
        if (foregroundPackage == ownPackage) return false
        if (foregroundPackage !in SETTINGS_PACKAGES) return false
        if (!containsUninstallWord(screenText)) return false
        if (containsForceStopWord(screenText)) return false
        return screenTargetsOwnApp(screenText, ownPackage, ownAppLabel) || ownAppInfoPageFresh
    }

    /**
     * تصمیم نهایی برای دیالوگ تأیید توقف اجباری در تنظیمات. فقط کلید لازم است.
     * شرط «نه حذف» صفحه‌ی اطلاعات اپ را کنار می‌گذارد. دیالوگ AOSP نام اپ را
     * ندارد، پس پرچمِ تازه‌ی صفحه‌ی اطلاعات اپ هم به‌جای لیبل قبول می‌شود.
     */
    fun shouldGuardForceStopDialog(
        selfProtectionEnabled: Boolean,
        foregroundPackage: String,
        ownPackage: String,
        ownAppLabel: String,
        screenText: String,
        ownAppInfoPageFresh: Boolean
    ): Boolean {
        if (!selfProtectionEnabled) return false
        if (foregroundPackage == ownPackage) return false
        if (foregroundPackage !in SETTINGS_PACKAGES) return false
        if (!containsForceStopWord(screenText)) return false
        if (containsUninstallWord(screenText)) return false
        return screenTargetsOwnApp(screenText, ownPackage, ownAppLabel) || ownAppInfoPageFresh
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
