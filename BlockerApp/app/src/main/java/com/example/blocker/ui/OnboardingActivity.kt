package com.example.blocker.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.blocker.PrefsManager
import com.example.blocker.health.ProtectionHealth

/**
 * آموزش راه‌اندازی «سد» — یک صفحه‌ی واحد، بدون ویزارد چندمرحله‌ای:
 * هر قدم دقیقاً می‌گوید چه دسترسی‌ای، چرا، و با یک دکمه کجا باید رفت.
 * وضعیت هر قدم از سیستم خوانده می‌شود و با برگشت از تنظیمات تازه می‌شود؛
 * هیچ قدمی با «زدم رفت» کامل نمی‌شود.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var column: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "راه‌اندازی سد"
        val (scroll, col) = UiKit.screen(this)
        column = col
        setContentView(scroll)
        render()
    }

    override fun onResume() { super.onResume(); render() }

    private fun stepCard(
        number: String,
        titleText: String,
        why: String,
        done: Boolean,
        statusText: String,
        actionText: String? = null,
        onAction: (() -> Unit)? = null,
        hint: String? = null
    ) {
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "$number. $titleText", 17f))
            c.addView(UiKit.body(this, why))
            c.addView(UiKit.row(this,
                UiKit.body(this, statusText),
                UiKit.statusDot(this, done)))
            if (hint != null) c.addView(UiKit.body(this, hint, 12f))
            if (actionText != null && onAction != null && !done) {
                c.addView(UiKit.primaryButton(this, actionText, onAction))
            }
        })
    }

    private fun render() {
        column.removeAllViews()
        column.addView(UiKit.title(this, "راه‌اندازی سد — قدم‌به‌قدم", 22f))
        column.addView(UiKit.body(this,
            "برای اینکه سد دقیق کار کند، فقط چند دسترسی لازم است. هر قدم را به ترتیب انجام بده؛ " +
            "وضعیت هر قدم همین‌جا زنده چک می‌شود و با برگشت از تنظیمات تازه می‌شود."))

        // قدم ۱ — سرویس دسترسی (ضروری) + قفل «تنظیمات محدودشده» برای اپ‌های سایدلودشده.
        // چون سد از فروشگاه نصب نشده، اندروید ۱۳ به بعد اول جلوی فعال‌سازی سرویس را
        // می‌گیرد: دکمه خاکستری می‌ماند و موقع زدن رویش پیام «نمی‌شود» می‌بینی. راه‌حل
        // این است که اول Allow restricted settings را از اطلاعات برنامه فعال کنی و بعد
        // برگردی سراغ دکمه‌ی سرویس — ترتیب مهم است و برعکسش گیج‌کننده می‌شود.
        val a11y = ProtectionHealth.isAccessibilityEnabled(this)
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "۱. سرویس دسترسی (ضروری)", 17f))
            c.addView(UiKit.body(this, "بدون این، سد هیچ صفحه‌ای را نمی‌بیند و هیچ بلاکی اجرا نمی‌شود. متن صفحات جایی ذخیره یا ارسال نمی‌شود."))
            c.addView(UiKit.row(this,
                UiKit.body(this, if (a11y) "وضعیت فعلی: فعال ✓" else "وضعیت فعلی: خاموش ✗"),
                UiKit.statusDot(this, a11y)))
            if (!a11y) {
                c.addView(UiKit.body(this,
                    "قدم ۱-الف — اول قفل را باز کن (فقط یک بار لازم است): دکمه‌ی «باز کردن اطلاعات برنامه» را بزن؛ " +
                    "بالای صفحه روی ⋮ (سه‌نقطه) بزن؛ «Allow restricted settings» را انتخاب کن و «Allow» را بزن. " +
                    "تا این کار را نکنی، دکمه‌ی سرویس خاکستری و غیرقابل‌زدن می‌ماند — این قفل خود اندروید است، نه باگ سد.", 13f))
                c.addView(UiKit.primaryButton(this, "باز کردن اطلاعات برنامه") {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                })
                c.addView(UiKit.body(this,
                    "قدم ۱-ب — حالا سرویس را روشن کن: دکمه‌ی زیر را بزن؛ «سد» را در لیست سرویس‌ها پیدا کن، " +
                    "واردش شو و دکمه را روشن کن. بعد از قدم الف، دکمه دیگر خاکستری نیست.", 13f))
                c.addView(UiKit.tonalButton(this, "باز کردن تنظیمات دسترسی") {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                })
            }
        })

        // قدم ۲ — نوتیفیکیشن
        val notifOk = androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()
        stepCard(
            number = "۲",
            titleText = "نوتیفیکیشن",
            why = "برای نمایش زمان باقی‌مانده‌ی سشن و هشدار خاموش شدن سرویس وسط سشن.",
            done = notifOk,
            statusText = if (notifOk) "وضعیت فعلی: فعال ✓" else "وضعیت فعلی: غیرفعال",
            actionText = if (Build.VERSION.SDK_INT >= 33) "درخواست مجوز نوتیفیکیشن" else null,
            onAction = if (Build.VERSION.SDK_INT >= 33) {
                { requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001) }
            } else null
        )

        // قدم ۳ — معافیت باتری
        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
        val batteryOk = pm.isIgnoringBatteryOptimizations(packageName)
        stepCard(
            number = "۳",
            titleText = "معافیت باتری",
            why = "تا اندروید وسط سشن، سرویس را نخواباند. معافیت ریسک را کم می‌کند، حذفش نمی‌کند.",
            done = batteryOk,
            statusText = if (batteryOk) "وضعیت فعلی: معاف ✓" else "وضعیت فعلی: معاف نیست",
            actionText = "درخواست معافیت باتری",
            onAction = {
                try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }
                catch (e: Exception) { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
            }
        )

        // قدم ۴ — آلارم دقیق (برای زمان‌بندی‌ها)
        val alarmOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(ALARM_SERVICE) as android.app.AlarmManager).canScheduleExactAlarms()
        } else true
        stepCard(
            number = "۴",
            titleText = "آلارم دقیق (برای زمان‌بندی‌ها)",
            why = "تا زمان‌بندی‌های خودکار دقیقاً سر ساعت شروع شوند، نه با تأخیر.",
            done = alarmOk,
            statusText = if (alarmOk) "وضعیت فعلی: فعال ✓" else "وضعیت فعلی: غیرفعال",
            actionText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "باز کردن تنظیمات آلارم" else null,
            onAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                {
                    try { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))) }
                    catch (e: Exception) { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
                }
            } else null
        )

        // قدم ۵ — چی بلاک شود (اختیاری)
        val hasConfig = PrefsManager.getRules(this).isNotEmpty() ||
            PrefsManager.getBlockedApps(this).isNotEmpty() ||
            PrefsManager.getBlockedWebsites(this).isNotEmpty()
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "۵. چی بلاک شود؟ (اختیاری)", 17f))
            c.addView(UiKit.body(this, "کلمات، دامنه‌ها و برنامه‌ها را انتخاب کن؛ بعداً هم از داشبورد قابل تغییر است — مگر در سشن Strict."))
            c.addView(UiKit.row(this,
                UiKit.body(this, if (hasConfig) "وضعیت فعلی: چیزی انتخاب شده ✓" else "وضعیت فعلی: هنوز چیزی انتخاب نشده"),
                UiKit.statusDot(this, if (hasConfig) true else null)))
            c.addView(UiKit.tonalButton(this, "مدیریت قوانین") { startActivity(Intent(this, RulesActivity::class.java)) })
            c.addView(UiKit.tonalButton(this, "انتخاب برنامه‌های هدف") { startActivity(Intent(this, com.example.blocker.AppsListActivity::class.java)) })
        })

        // محدودیت‌های صادقانه
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "سد چه کار نمی‌کند", 17f))
            c.addView(UiKit.body(this,
                "• متن داخل عکس/ویدیو و بعضی WebView ها دیده نمی‌شود؛ جست‌وجوی داخل HTTPS قابل خواندن نیست.\n" +
                "• اپ معمولی نمی‌تواند جلوی حذف یا Force Stop خودش را بگیرد؛ قفل واقعی فقط با Device Owner است (درباره‌ی «سد» بخوان).\n" +
                "• خودمحافظتی وسط سشن فقط صفحه‌ی جزئیات سرویس سد در تنظیمات را می‌بندد، نه کل تنظیمات را؛ و با پایان سشن خاموش می‌شود.\n" +
                "• اگر سرویس وسط سشن خاموش شود، سد سکوت نمی‌کند؛ هشدار می‌دهد که محافظت متوقف شده است."))
        })

        column.addView(UiKit.primaryButton(this, "پایان راه‌اندازی") {
            if (!ProtectionHealth.isAccessibilityEnabled(this)) {
                Toast.makeText(this, "اول قدم ۱ (سرویس دسترسی) را فعال کن؛ بدون آن سد کار نمی‌کند.", Toast.LENGTH_LONG).show()
            } else {
                PrefsManager.setOnboardingDone(this, true)
                finish()
            }
        })
        column.addView(UiKit.outlinedButton(this, "بعداً انجام می‌دهم") { finish() })
    }
}
