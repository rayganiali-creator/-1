package com.example.blocker.ui

import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.blocker.PrefsManager

/** گزارش‌های لوکال از داده‌ی واقعی ذخیره‌شده؛ هیچ متن صفحه یا تاریخچه‌ی مرور ذخیره نمی‌شود. */
class ReportsActivity : AppCompatActivity() {

    private lateinit var column: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "گزارش‌ها و تاریخچه"
        val (scroll, col) = UiKit.screen(this)
        column = col
        setContentView(scroll)
        render()
    }

    private fun render() {
        column.removeAllViews()
        val r = PrefsManager.getReportCounters(this)
        val started = r.optInt("sessionsStarted")
        val completed = r.optInt("sessionsCompleted")
        val interrupted = r.optInt("sessionsInterrupted")
        val totalFocus = r.optLong("totalFocusMillis")
        val rate = if (started > 0) (completed * 100 / started) else 0
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "خلاصه‌ی فوکوس", 18f))
            c.addView(UiKit.body(this, "سشن‌های شروع‌شده: $started"))
            c.addView(UiKit.body(this, "سشن‌های کامل‌شده: $completed"))
            c.addView(UiKit.body(this, "سشن‌های نیمه‌تمام/قطع‌شده: $interrupted"))
            c.addView(UiKit.body(this, "نرخ تکمیل: $rate٪"))
            c.addView(UiKit.body(this, "مجموع زمان فوکوس کامل: ${UiKit.formatMinutes(totalFocus / 60_000L)}"))
            c.addView(UiKit.body(this, "تعداد بلاک‌های اجراشده: ${r.optInt("blocksTotal")}"))
            c.addView(UiKit.body(this, "خرابی‌های محافظت ثبت‌شده: ${r.optInt("protectionFailures")}"))
        })
        val byRule = r.optJSONObject("blocksByRule")
        if (byRule != null && byRule.length() > 0) {
            column.addView(UiKit.card(this) { c ->
                c.addView(UiKit.title(this, "پرتکرارترین تریگرها", 17f))
                val keys = mutableListOf<String>()
                byRule.keys().forEachRemaining { keys += it }
                keys.sortedByDescending { byRule.optInt(it) }.take(10).forEach { k ->
                    c.addView(UiKit.body(this, "$k — ${byRule.optInt(k)} بار"))
                }
            })
        }
        val byCause = r.optJSONObject("failuresByCause")
        if (byCause != null && byCause.length() > 0) {
            column.addView(UiKit.card(this) { c ->
                c.addView(UiKit.title(this, "علل قطع محافظت", 17f))
                val keys = mutableListOf<String>()
                byCause.keys().forEachRemaining { keys += it }
                keys.forEach { k -> c.addView(UiKit.body(this, "$k — ${byCause.optInt(k)} بار")) }
            })
        }
        if (started == 0 && r.optInt("blocksTotal") == 0) {
            column.addView(UiKit.body(this, "هنوز داده‌ای ثبت نشده است؛ بعد از اولین سشن یا بلاک، این صفحه پر می‌شود."))
        }
        column.addView(UiKit.outlinedButton(this, "پاک کردن همه‌ی گزارش‌ها") {
            AlertDialog.Builder(this)
                .setTitle("پاک کردن گزارش‌ها؟")
                .setMessage("همه‌ی شمارنده‌ها و تاریخچه‌ی لوکال پاک می‌شود. کانفیگ بلاک دست نمی‌خورد.")
                .setPositiveButton("پاک کن") { _, _ -> PrefsManager.clearReports(this); render() }
                .setNegativeButton("انصراف", null)
                .show()
        })
        column.addView(UiKit.body(this, "حریم خصوصی: فقط شمارنده‌ها ذخیره می‌شوند؛ متن صفحات، عبارت‌های تایپ‌شده و تاریخچه‌ی مرور هرگز ذخیره یا ارسال نمی‌شوند.", 12f))
    }
}
