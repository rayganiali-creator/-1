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

        column.addView(UiKit.tonalButton(this, "سلامت و تشخیص محافظت") { startActivity(Intent(this, DiagnosticsActivity::class.java)) })
        column.addView(UiKit.tonalButton(this, "بکاپ و بازیابی کانفیگ") { startActivity(Intent(this, BackupActivity::class.java)) })
        column.addView(UiKit.tonalButton(this, "درباره، حریم خصوصی و محدودیت‌ها") { startActivity(Intent(this, AboutActivity::class.java)) })
        column.addView(UiKit.outlinedButton(this, "ریکاوری اضطراری و عیب‌یابی") { startActivity(Intent(this, RecoveryActivity::class.java)) })
    }

    private fun renderSelfProtectionCard() {
        val enabled = PrefsManager.getSelfProtectionEnabled(this)
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "محافظت از حذف — بدون فکتوری‌ریست", 17f))
            c.addView(UiKit.body(
                this,
                "اگر اپ معمولی است و Device Owner نیست، حذف واقعی قفل نمی‌شود. این دو لایه فقط " +
                        "حذفِ لحظه‌ایِ از روی وسوسه را سخت می‌کنند:"
            ))
            c.addView(UiKit.body(
                this,
                "۱) Device Admin: برای حذف، اول باید در تنظیمات > امنیت، ادمین را غیرفعال کنی؛ یک مرحله‌ی اضافه.",
                13f
            ))
            c.addView(UiKit.body(
                this,
                "۲) خودمحافظتی در سشن: وقتی سشنی فعال است، فقط صفحه‌ی جزئیات سرویس «سد» " +
                        "در تنظیمات (همان صفحه‌ای که دکمه‌ی روشن/خاموش سرویس در آن است) بسته می‌شود؛ " +
                        "صفحه‌ی اصلی تنظیمات، لیست سرویس‌ها و صفحه‌ی سرویس‌های دیگر دست نمی‌خورند. " +
                        "دیالوگ حذف هم وسط سشن بسته می‌شود.",
                13f
            ))
            c.addView(UiKit.body(
                this,
                "محدودیت صادقانه: این اصطکاک است، نه قفل. از Safe Mode یا adb دور زده می‌شود و بعد از Force Stop " +
                        "تا باز شدن دوباره‌ی اپ کار نمی‌کند. قفل واقعی فقط با Device Owner است (راهنما در «درباره»).",
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
            val toggleText = if (enabled) "✅ خودمحافظتی در سشن: فعال" else "خودمحافظتی در سشن: غیرفعال"
            c.addView(UiKit.tonalButton(this, toggleText) {
                PrefsManager.setSelfProtectionEnabled(this, !enabled)
                Toast.makeText(
                    this,
                    if (!enabled) "خودمحافظتی فعال شد؛ در سشن فعال بعدی اثر دارد."
                    else "خودمحافظتی غیرفعال شد.", Toast.LENGTH_SHORT
                ).show()
                render()
            })
        })
    }
}
