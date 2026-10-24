package com.example.blocker.ui

import android.content.Intent
import android.os.Bundle
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

        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "رنگ پس‌زمینه", 17f))
            c.addView(UiKit.body(this, "پس‌زمینه‌ی همه‌ی صفحه‌ها با این رنگ عوض می‌شود."))
            val themes = listOf(
                "system" to "خودکار (متناسب با سیستم)",
                "light" to "روشن",
                "dark" to "تیره",
                "mint" to "نعنایی",
                "blue" to "آبی",
                "cream" to "کرمی"
            )
            val group = RadioGroup(this)
            themes.forEach { (value, label) ->
                group.addView(RadioButton(this).apply {
                    text = label
                    tag = value
                    isChecked = PrefsManager.getTheme(this@SettingsActivity) == value
                })
            }
            group.setOnCheckedChangeListener { _, checkedId ->
                val rb = findViewById<RadioButton>(checkedId)
                (rb?.tag as? String)?.let {
                    PrefsManager.setTheme(this, it)
                    // اعمال در کل برنامه با بازشدن دوباره از داشبورد.
                    startActivity(Intent(this, com.example.blocker.MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    })
                    finish()
                }
            }
            c.addView(group)
        })

        column.addView(UiKit.tonalButton(this, "سلامت و تشخیص محافظت") { startActivity(Intent(this, DiagnosticsActivity::class.java)) })
        column.addView(UiKit.tonalButton(this, "بکاپ و بازیابی کانفیگ") { startActivity(Intent(this, BackupActivity::class.java)) })
        column.addView(UiKit.tonalButton(this, "درباره، حریم خصوصی و محدودیت‌ها") { startActivity(Intent(this, AboutActivity::class.java)) })
        column.addView(UiKit.outlinedButton(this, "ریکاوری اضطراری و عیب‌یابی") { startActivity(Intent(this, RecoveryActivity::class.java)) })
    }

    private fun renderSelfProtectionCard() {
        val enabled = PrefsManager.getSelfProtectionEnabled(this)
        val strict = SessionManager.isStrictActive(this)
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "محافظت از حذف و توقف اجباری — بدون فکتوری‌ریست", 17f))
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
            c.addView(UiKit.body(this, "وضعیت Device Admin: " + DeviceAdminSetup.statusLabelFa(this), 13f))
            if (DeviceOwnerHelper.isDeviceAdminActive(this)) {
                c.addView(UiKit.body(this, "✅ Device Admin فعال است.", 14f))
            } else {
                c.addView(UiKit.tonalButton(this, "فعال‌سازی Device Admin") {
                    DeviceAdminSetup.requestDeviceAdmin(this)
                })
            }
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
}
