package com.example.blocker.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.blocker.PrefsManager
import com.example.blocker.admin.DeviceOwnerHelper
import com.example.blocker.health.ProtectionHealth

/**
 * آنبوردینگ گام‌به‌گام با چک واقعی: هیچ گامی با «زدم رفت» کامل نمی‌شود؛
 * وضعیت هر گام از سیستم خوانده می‌شود. پیشرفت ذخیره می‌شود و با قطع شدن
 * اپ از همان گام ادامه پیدا می‌کند. گام‌های اختیاری قابل رد کردن‌اند
 * ولی به دروغ «کامل» علامت نمی‌خورند.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var column: LinearLayout
    private var step: Int = 0
    private val totalSteps = 7

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "راه‌اندازی بلاکر"
        step = PrefsManager.getOnboardingStep(this)
        val (scroll, col) = UiKit.screen(this)
        column = col
        setContentView(scroll)
        render()
    }

    override fun onResume() { super.onResume(); render() }

    private fun goTo(newStep: Int) {
        step = newStep.coerceIn(0, totalSteps - 1)
        PrefsManager.setOnboardingStep(this, step)
        render()
    }

    private fun nav(complete: Boolean, optional: Boolean = false) {
        column.addView(UiKit.divider(this))
        if (complete) column.addView(UiKit.body(this, "✓ این گام واقعاً کامل است (از وضعیت سیستم بررسی شد)."))
        else if (!optional) column.addView(UiKit.body(this, "این گام هنوز کامل نشده است؛ تا انجامش ندهید دکمه‌ی بعدی شما را جلو نمی‌برد مگر برگردید."))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        if (step > 0) row.addView(com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "قبلی"; setOnClickListener { goTo(step - 1) }
        })
        if (step < totalSteps - 1) {
            row.addView(com.google.android.material.button.MaterialButton(this).apply {
                text = if (optional && !complete) "رد کردن (اختیاری)" else "بعدی"
                setOnClickListener { if (complete || optional) goTo(step + 1) else render() }
            })
        }
        column.addView(row)
        if (step == totalSteps - 1) {
            column.addView(UiKit.primaryButton(this, "پایان راه‌اندازی") {
                PrefsManager.setOnboardingDone(this, true)
                PrefsManager.setOnboardingStep(this, 0)
                finish()
            })
        }
        column.addView(UiKit.outlinedButton(this, "خروج از راهنما (پیشرفت ذخیره شده)") { finish() })
    }

    private fun render() {
        column.removeAllViews()
        column.addView(UiKit.title(this, "گام ${step + 1} از $totalSteps"))
        when (step) {
            0 -> {
                column.addView(UiKit.title(this, "بلاکر چه کار می‌کند و چه کار نمی‌کند", 19f))
                column.addView(UiKit.body(this, "بلاکر با سرویس دسترسی اندروید، برنامه‌ها و دامنه‌های انتخابی و کلمات کلیدی روی متن قابل مشاهده‌ی صفحه را می‌بندد."))
                column.addView(UiKit.body(this, "صادقانه: متن داخل عکس/ویدیو و بعضی WebView ها دیده نمی‌شود؛ جست‌وجوی داخل HTTPS قابل خواندن نیست؛ و اپ معمولی نمی‌تواند جلوی حذف یا Force Stop خودش را بگیرد. این محدودیت‌ها در کل اپ همین‌طور شفاف نمایش داده می‌شوند."))
                nav(complete = true)
            }
            1 -> {
                column.addView(UiKit.title(this, "قوانین و برنامه‌های هدف", 19f))
                column.addView(UiKit.body(this, "اول مشخص کنید چه چیزی بلاک شود: کلمات و عبارات (با دسته و تطبیق کلمه‌ی کامل)، دامنه‌ها و برنامه‌های هدف. بعداً هم از داشبورد قابل تغییر است — مگر در سشن Strict."))
                column.addView(UiKit.tonalButton(this, "مدیریت قوانین") { startActivity(Intent(this, RulesActivity::class.java)) })
                column.addView(UiKit.tonalButton(this, "انتخاب برنامه‌های هدف") { startActivity(Intent(this, com.example.blocker.AppsListActivity::class.java)) })
                val hasConfig = PrefsManager.getRules(this).isNotEmpty() || PrefsManager.getBlockedApps(this).isNotEmpty() || PrefsManager.getBlockedWebsites(this).isNotEmpty()
                nav(complete = hasConfig, optional = true)
            }
            2 -> {
                val ok = ProtectionHealth.isAccessibilityEnabled(this)
                column.addView(UiKit.title(this, "سرویس دسترسی (ضروری)", 19f))
                column.addView(UiKit.body(this, "این سرویس رخدادهای صفحه را می‌بیند تا تطبیق انجام شود. بدون آن هیچ بلاکی اجرا نمی‌شود. متن صفحات جایی ذخیره یا ارسال نمی‌شود."))
                column.addView(UiKit.body(this, if (ok) "وضعیت فعلی: فعال ✓" else "وضعیت فعلی: خاموش ✗"))
                column.addView(UiKit.primaryButton(this, "باز کردن تنظیمات دسترسی") {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                })
                nav(complete = ok)
            }
            3 -> {
                val ok = androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()
                column.addView(UiKit.title(this, "نوتیفیکیشن (اختیاری ولی مفید)", 19f))
                column.addView(UiKit.body(this, "برای نمایش نوتیفیکیشن پایدار سشن (زمان باقی‌مانده و باز کردن سریع سشن) لازم است. نبودنش بلاک را متوقف نمی‌کند."))
                column.addView(UiKit.body(this, if (ok) "وضعیت فعلی: فعال ✓" else "وضعیت فعلی: غیرفعال"))
                if (Build.VERSION.SDK_INT >= 33) {
                    column.addView(UiKit.primaryButton(this, "درخواست مجوز نوتیفیکیشن") {
                        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
                    })
                }
                nav(complete = ok, optional = true)
            }
            4 -> {
                val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
                val ok = pm.isIgnoringBatteryOptimizations(packageName)
                column.addView(UiKit.title(this, "باتری و اجرای پس‌زمینه (اختیاری)", 19f))
                column.addView(UiKit.body(this, "سشن‌ها برای اجرا به UI باز وابسته نیستند؛ ولی بهینه‌سازی باتری و سیاست سازنده‌ها ممکن است سرویس را بخواباند. معافیت باتری این ریسک را کم می‌کند، حذفش نمی‌کند. ریکاوری بعد از ریبوت با گیرنده‌ی بوت انجام می‌شود."))
                column.addView(UiKit.body(this, if (ok) "وضعیت فعلی: معاف ✓" else "وضعیت فعلی: معاف نیست"))
                column.addView(UiKit.primaryButton(this, "درخواست معافیت باتری") {
                    try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }
                    catch (e: Exception) { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
                })
                nav(complete = ok, optional = true)
            }
            5 -> {
                val owner = DeviceOwnerHelper.isDeviceOwner(this)
                column.addView(UiKit.title(this, "استاندارد یا مدیریت‌شده؟", 19f))
                column.addView(UiKit.body(this, "حالت استاندارد: بلاک با سرویس دسترسی؛ کاربر مصمم می‌تواند از تنظیمات سرویس را خاموش یا اپ را حذف کند — اپ جلویش را نمی‌گیرد و این را پنهان هم نمی‌کند."))
                column.addView(UiKit.body(this, "حالت مدیریت‌شده (Device Owner): تنها راه رسمی بلاک حذف در Strict. نیازمند پروویژن رسمی است (روی گوشی شخصی معمولاً فکتوری ریست) و از داخل اپ فعال نمی‌شود. وضعیت این دستگاه: " + if (owner) "Device Owner فعال است ✓" else "فعال نیست."))
                column.addView(UiKit.body(this, "Force Stop: حتی با سرویس پیش‌زمینه هم اپ معمولی نمی‌تواند جلوی Force Stop را بگیرد؛ بعد از آن تا باز شدن دستی اپ، اجرا متوقف است و این اتفاق در گزارش/داشبورد صادقانه دیده می‌شود."))
                nav(complete = true)
            }
            6 -> {
                column.addView(UiKit.title(this, "تشخیص نهایی", 19f))
                val level = ProtectionHealth.protectionLevel(this)
                column.addView(UiKit.body(this, "سطح محافظت واقعی فعلی: ${level.labelFa}"))
                column.addView(UiKit.body(this, level.descriptionFa))
                ProtectionHealth.checks(this).take(5).forEach { chk ->
                    column.addView(UiKit.body(this, "• ${chk.name}: ${chk.status.labelFa}"))
                }
                column.addView(UiKit.tonalButton(this, "صفحه‌ی تشخیص کامل") { startActivity(Intent(this, DiagnosticsActivity::class.java)) })
                nav(complete = ProtectionHealth.isAccessibilityEnabled(this))
            }
        }
    }
}
