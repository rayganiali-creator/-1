package com.example.blocker.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.blocker.PrefsManager
import com.example.blocker.admin.DeviceAdminSetup
import com.example.blocker.admin.DeviceOwnerHelper
import com.example.blocker.core.BlockingDelays
import com.example.blocker.core.CoolingOff
import com.example.blocker.session.SessionManager

/** تنظیمات عمومی + مسیر ریکاوری و درباره. در Strict فعال، تنظیمات مؤثر روی سشن قفل‌اند. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var column: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "تنظیمات"
        val (scroll, col) = UiKit.screen(this)
        column = col
        setContentView(scroll)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        column.removeAllViews()
        val strict = SessionManager.isStrictActive(this)
        if (strict) column.addView(UiKit.body(this, "🔒 سشن Strict فعال است؛ تغییر این تنظیمات فقط روی سشن‌های بعدی اثر دارد، نه سشن جاری."))

        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "دیلی قبل از بلاک", 17f))
            c.addView(UiKit.body(this, "بین شناسایی تریگر و بستن صفحه چقدر صبر شود. در این فاصله اگر از آن صفحه بروید، بلاک کنسل می‌شود."))
            val group = RadioGroup(this)
            BlockingDelays.ALLOWED_SECONDS.forEach { sec ->
                group.addView(RadioButton(this).apply {
                    text = if (sec == 0) "بدون تأخیر (فوری)" else "$sec ثانیه"
                    tag = sec
                    isChecked = PrefsManager.getDelaySeconds(this@SettingsActivity) == sec
                })
            }
            group.setOnCheckedChangeListener { _, checkedId ->
                val rb = findViewById<RadioButton>(checkedId)
                (rb?.tag as? Int)?.let {
                    PrefsManager.setDelaySeconds(this, it)
                    Toast.makeText(this, "دیلی روی $it ثانیه تنظیم شد", Toast.LENGTH_SHORT).show()
                }
            }
            c.addView(group)
        })

        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "آرام‌سازی پیش‌فرض خروج از Strict", 17f))
            val group = RadioGroup(this)
            CoolingOff.values().forEach { co ->
                group.addView(RadioButton(this).apply {
                    text = co.labelFa
                    tag = co
                    isChecked = PrefsManager.getDefaultCoolingOff(this@SettingsActivity) == co
                })
            }
            group.setOnCheckedChangeListener { _, checkedId ->
                val rb = findViewById<RadioButton>(checkedId)
                (rb?.tag as? CoolingOff)?.let { PrefsManager.setDefaultCoolingOff(this, it) }
            }
            c.addView(group)
            c.addView(UiKit.body(this, "این مقدار هنگام ساخت هر سشن Strict داخل سشن قفل می‌شود؛ تغییرش سشن جاری را عوض نمی‌کند.", 12f))
        })

        renderSelfProtectionCard()
        renderDeviceAdminCard()
        renderBatteryCard()

        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "رنگ پس‌زمینه", 17f))
            c.addView(UiKit.body(this, "این رنگ به عنوان پس‌زمینه‌ی صفحه‌ی بلاک استفاده می‌شود."))
            val dots = listOf(
                0xFF102A43.toInt(),
                0xFF1E3A8A.toInt(),
                0xFF4C1D95.toInt(),
                0xFF7C2D12.toInt(),
                0xFF14181D.toInt()
            )
            val selected = PrefsManager.getBlockedBg(this)
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            dots.forEach { color ->
                val dot = android.view.View(this).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(color)
                        if (color == selected) {
                            setStroke(UiKit.dp(this@SettingsActivity, 3), 0xFF7CFFD9.toInt())
                        }
                    }
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        UiKit.dp(this@SettingsActivity, 44),
                        UiKit.dp(this@SettingsActivity, 44)
                    ).apply { marginEnd = UiKit.dp(this@SettingsActivity, 12) }
                    setOnClickListener {
                        PrefsManager.setBlockedBg(this@SettingsActivity, color)
                        recreate()
                    }
                }
                row.addView(dot)
            }
            c.addView(row)
        })

        column.addView(UiKit.tonalButton(this, "سلامت و تشخیص محافظت") { startActivity(Intent(this, DiagnosticsActivity::class.java)) })
        column.addView(UiKit.tonalButton(this, "هوش روی دستگاه") { startActivity(Intent(this, AiSettingsActivity::class.java)) })
        column.addView(UiKit.tonalButton(this, "راه‌اندازی و دسترسی‌ها (بازبینی)") {
            startActivity(Intent(this, OnboardingActivity::class.java).apply {
                putExtra(OnboardingActivity.EXTRA_REPAIR, true)
            })
        })
        column.addView(UiKit.tonalButton(this, "بکاپ و بازیابی کانفیگ") { startActivity(Intent(this, BackupActivity::class.java)) })
        column.addView(UiKit.tonalButton(this, "درباره، حریم خصوصی و محدودیت‌ها") { startActivity(Intent(this, AboutActivity::class.java)) })
        column.addView(UiKit.outlinedButton(this, "ریکاوری اضطراری و عیب‌یابی") { startActivity(Intent(this, RecoveryActivity::class.java)) })
    }

    private fun renderSelfProtectionCard() {
        val enabled = PrefsManager.getSelfProtectionEnabled(this)
        val strict = SessionManager.isStrictActive(this)
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "خودمحافظتی", 17f))
            c.addView(UiKit.body(this,
                if (strict) "فعال در سشن Strict"
                else if (enabled) "فعال" else "غیرفعال", 13f))
            c.addView(UiKit.body(
                this,
                "اگر اپ معمولی است و Device Owner نیست، حذف واقعی قفل نمی‌شود. این لایه‌ها فقط " +
                        "حذف و توقفِ لحظه‌ایِ از روی وسوسه را سخت می‌کنند:"
            ))
            c.addView(UiKit.body(
                this,
                "۱) Device Admin: برای حذف، اول باید در تنظیمات > امنیت، ادمین را غیرفعال کنی؛ یک مرحله‌ی اضافه.",
                13f
            ))
            c.addView(UiKit.body(
                this,
                "۲) گارد دیالوگ: هر وقت این کلید روشن است — حتی بدون سشن — دیالوگ تأیید حذفِ «سد» " +
                        "(در نصاب یا تنظیمات) و دیالوگ تأیید توقف اجباریِ «سد» با back بسته می‌شوند. " +
                        "صفحه‌ی اطلاعات اپ بسته نمی‌شود؛ فقط خودِ دیالوگ.",
                13f
            ))
            c.addView(UiKit.body(
                this,
                "۳) گارد صفحه‌ی سرویس: فقط وسط سشن، صفحه‌ی جزئیات سرویس «سد» در تنظیمات " +
                        "بسته می‌شود تا وسط سشن نتوانی سرویس را خاموش کنی؛ بیرون از سشن این صفحه " +
                        "همیشه باز است تا خودت سرویس را مدیریت کنی.",
                13f
            ))
            c.addView(UiKit.body(
                this,
                "محدودیت صادقانه: این اصطکاک است، نه قفل. از Safe Mode یا adb دور زده می‌شود؛ " +
                        "بعد از Force Stop تا باز شدن دوباره‌ی اپ کار نمی‌کند؛ و وقتی سرویس دسترسی " +
                        "خاموش است هیچ گاردی کار نمی‌کند. قفل واقعی فقط با Device Owner است (راهنما در «درباره»).",
                12f
            ))
            c.addView(UiKit.divider(this))
            if (strict) {
                c.addView(UiKit.body(this,
                    "🔒 در سشن Strict محافظت کامل برقرار است و کلید قفل است؛ حتی اگر کلید خاموش " +
                    "بوده باشد، در طول سشن دیالوگ‌های حذف و توقف بسته می‌شوند و صفحه‌ی اطلاعات " +
                    "اپ باز نمی‌شود. بعد از پایان سشن، کلید به وضعیت قبلی‌اش برمی‌گردد.", 13f))
            }
            val toggleText = if (strict) "🔒 خودمحافظتی: فعال در سشن Strict"
            else if (enabled) "✅ خودمحافظتی: فعال" else "خودمحافظتی: غیرفعال"
            c.addView(UiKit.tonalButton(this, toggleText) {
                if (SessionManager.isStrictActive(this)) {
                    Toast.makeText(this, "در سشن Strict خودمحافظتی قفل است و تا پایان سشن قابل تغییر نیست.", Toast.LENGTH_SHORT).show()
                    render()
                    return@tonalButton
                }
                PrefsManager.setSelfProtectionEnabled(this, !enabled)
                Toast.makeText(
                    this,
                    if (!enabled) "خودمحافظتی فعال شد؛ دیالوگ‌های حذف و توقف اجباری از همین حالا بسته می‌شوند."
                    else "خودمحافظتی غیرفعال شد.", Toast.LENGTH_SHORT
                ).show()
                render()
            }.apply { isEnabled = !strict })
        })
    }

    /** کارت جدای Device Admin موکاپ: وضعیت + دکمه‌ی فعال‌سازی. */
    private fun renderDeviceAdminCard() {
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "Device Admin", 17f))
            c.addView(UiKit.body(this, "وضعیت Device Admin: " + DeviceAdminSetup.statusLabelFa(this), 13f))
            if (DeviceOwnerHelper.isDeviceAdminActive(this)) {
                c.addView(UiKit.body(this, "✅ Device Admin فعال است.", 14f))
            } else {
                c.addView(UiKit.tonalButton(this, "فعال‌سازی Device Admin") {
                    DeviceAdminSetup.requestDeviceAdmin(this)
                })
            }
        })
    }

    /**
     * زنده ماندن در برابر خواب گوشی: معافیت باتری مؤثرترین اهرمِ در اختیار یک
     * برنامه‌ی معمولی در برابر کشته‌شدن توسط سامسونگ است — با تأیید خود کاربر
     * (دیالوگ سیستم) درخواست می‌شود، نه خودکار.
     */
    private fun renderBatteryCard() {
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "زنده ماندن در برابر خواب گوشی", 17f))
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            val ignoring = pm.isIgnoringBatteryOptimizations(packageName)
            if (ignoring) {
                c.addView(UiKit.body(this, "✅ معافیت باتری فعال است؛ اندروید «سد» را در خواب عمیق نمی‌کشد.", 13f))
            } else {
                c.addView(UiKit.body(this, "⚠️ معافیت باتری فعال نیست؛ سامسونگ بعد از چند ساعت بی‌کاری ممکن است «سد» را متوقف کند.", 13f))
                c.addView(UiKit.tonalButton(this, "درخواست معافیت باتری") {
                    try {
                        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        })
                    } catch (e: Exception) {
                        try {
                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        } catch (ignored: Exception) {
                        }
                    }
                })
            }
            c.addView(UiKit.body(this, "نکته‌ی سامسونگ: در تنظیمات گوشی هم «سد» را به Never sleeping apps اضافه کن و در صفحه‌ی برنامه‌های اخیر روی «سد» قفل بگذار.", 12f))
        })
    }
}
