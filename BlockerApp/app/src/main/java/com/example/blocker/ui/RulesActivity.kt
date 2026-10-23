package com.example.blocker.ui

import android.os.Bundle
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.blocker.PrefsManager
import com.example.blocker.core.KeywordMatcher
import com.example.blocker.core.KeywordRule
import com.example.blocker.session.SessionManager
import com.google.android.material.switchmaterial.SwitchMaterial

/** مدیریت قوانین کلمات کلیدی + دامنه‌ها. در Strict فعال، ویرایش قفل است. */
class RulesActivity : AppCompatActivity() {

    private lateinit var column: LinearLayout
    private var locked: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "قوانین و کلمات کلیدی"
        val (scroll, col) = UiKit.screen(this)
        column = col
        setContentView(scroll)
        render()
    }

    override fun onResume() { super.onResume(); render() }

    private fun guardLocked(): Boolean {
        if (locked) Toast.makeText(this, "در سشن Strict ویرایش قوانین قفل است.", Toast.LENGTH_LONG).show()
        return locked
    }

    private fun render() {
        locked = SessionManager.isStrictActive(this)
        column.removeAllViews()
        if (locked) {
            column.addView(UiKit.card(this) { c ->
                c.addView(UiKit.body(this, "🔒 سشن Strict فعال است؛ قوانین این سشن از snapshot قفل‌شده خوانده می‌شوند و ویرایش تا پایان سشن ممکن نیست."))
            })
        }

        column.addView(UiKit.row(this,
            UiKit.title(this, "کلمات و عبارات", 19f),
            TextView(this)
        ))
        val rules = PrefsManager.getRules(this)
        if (rules.isEmpty()) column.addView(UiKit.body(this, "هنوز قانونی ثبت نشده است."))
        rules.forEach { rule ->
            column.addView(UiKit.card(this) { c ->
                val sw = SwitchMaterial(this).apply {
                    isChecked = rule.enabled
                    text = rule.text
                    isEnabled = !locked
                    setOnCheckedChangeListener { _, checked ->
                        if (!guardLocked()) PrefsManager.updateRule(this@RulesActivity, rule.copy(enabled = checked))
                    }
                }
                c.addView(sw)
                val meta = buildString {
                    append("دسته: ${rule.category}")
                    if (rule.wholeWord) append(" • فقط کلمه‌ی کامل")
                    if (rule.caseSensitive) append(" • حساس به بزرگی حروف")
                    if (rule.isAllow) append(" • قانون اجازه (allowlist)")
                    if (rule.targetPackages.isNotEmpty()) append(" • فقط: ${rule.targetPackages.joinToString(", ")}")
                }
                c.addView(UiKit.body(this, meta, 12f))
                val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                btns.addView(UiKit.textButton(this, "ویرایش") { if (!guardLocked()) showRuleDialog(rule) }.apply { isEnabled = !locked })
                btns.addView(UiKit.textButton(this, "حذف") {
                    if (!guardLocked()) {
                        PrefsManager.removeRule(this@RulesActivity, rule.id)
                        render()
                    }
                }.apply { isEnabled = !locked })
                c.addView(btns)
            })
        }
        column.addView(UiKit.primaryButton(this, "＋ افزودن قانون") {
            if (!guardLocked()) showRuleDialog(null)
        })

        // پیش‌نمایش تطبیق
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "پیش‌نمایش تطبیق", 17f))
            c.addView(UiKit.body(this, "یک متن نمونه بنویسید تا ببینید کدام قانون‌ها رویش صدق می‌کنند (قبل از فعال‌سازی)."))
            val sample = EditText(this).apply { hint = "متن نمونه…" }
            val result = UiKit.body(this, "")
            c.addView(sample)
            c.addView(UiKit.tonalButton(this, "بررسی") {
                val matches = KeywordMatcher.previewMatches(PrefsManager.getRules(this@RulesActivity), sample.text.toString())
                result.text = if (matches.isEmpty()) "هیچ قانونی روی این متن صدق نمی‌کند."
                else "تطبیق‌ها: " + matches.joinToString("، ") { "${it.rule.text}${if (it.rule.isAllow) " (اجازه)" else ""}" }
            })
            c.addView(result)
        })

        // دامنه‌ها
        column.addView(UiKit.title(this, "سایت‌های مسدود (دامنه)", 19f))
        val sites = PrefsManager.getBlockedWebsites(this)
        if (sites.isEmpty()) column.addView(UiKit.body(this, "دامنه‌ای ثبت نشده است."))
        sites.sorted().forEach { domain ->
            column.addView(UiKit.card(this) { c ->
                c.addView(UiKit.row(this, UiKit.body(this, domain, 15f),
                    UiKit.textButton(this, "حذف") {
                        if (!guardLocked()) { PrefsManager.removeBlockedWebsite(this@RulesActivity, domain); render() }
                    }.apply { isEnabled = !locked }))
            })
        }
        val domainInput = EditText(this).apply { hint = "example.com"; inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI }
        column.addView(domainInput)
        column.addView(UiKit.tonalButton(this, "افزودن دامنه") {
            if (guardLocked()) return@tonalButton
            val v = domainInput.text.toString().trim().lowercase()
                .removePrefix("https://").removePrefix("http://").substringBefore('/')
            if (v.isBlank() || !v.contains(".")) {
                Toast.makeText(this, "دامنه‌ی معتبر وارد کنید (مثل example.com)", Toast.LENGTH_SHORT).show()
            } else {
                PrefsManager.addBlockedWebsite(this, v)
                render()
            }
        })
        column.addView(UiKit.body(this, "تشخیص دامنه فقط از نوار آدرس مرورگرهای شناخته‌شده انجام می‌شود؛ مرورگر ناشناس یا حالت ناشناس ممکن است پوشش داده نشود.", 12f))
    }

    private fun showRuleDialog(existing: KeywordRule?) {
        val textIn = EditText(this).apply { hint = "کلمه یا عبارت"; setText(existing?.text ?: "") }
        val catIn = EditText(this).apply { hint = "دسته"; setText(existing?.category ?: "عمومی") }
        val targetsIn = EditText(this).apply {
            hint = "محدود به پکیج‌ها (با کاما، اختیاری)"
            setText(existing?.targetPackages?.joinToString(",") ?: "")
        }
        val whole = CheckBox(this).apply { text = "فقط تطبیق کلمه‌ی کامل (داخل کلمه‌ی دیگر صدق نکند)"; isChecked = existing?.wholeWord ?: false }
        val caseS = CheckBox(this).apply { text = "حساس به بزرگی/کوچکی حروف لاتین"; isChecked = existing?.caseSensitive ?: false }
        val allow = CheckBox(this).apply { text = "قانون اجازه (allowlist) — بر بلاک غلبه می‌کند"; isChecked = existing?.isAllow ?: false }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
            addView(textIn); addView(catIn); addView(targetsIn); addView(whole); addView(caseS); addView(allow)
        }
        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "قانون جدید" else "ویرایش قانون")
            .setView(box)
            .setPositiveButton("ذخیره") { _, _ ->
                val text = textIn.text.toString().trim()
                if (text.isEmpty()) { Toast.makeText(this, "متن قانون خالی است", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val rule = KeywordRule(
                    id = existing?.id ?: PrefsManager.newId(),
                    text = text,
                    category = catIn.text.toString().trim().ifEmpty { "عمومی" },
                    enabled = existing?.enabled ?: true,
                    wholeWord = whole.isChecked,
                    caseSensitive = caseS.isChecked,
                    isAllow = allow.isChecked,
                    targetPackages = targetsIn.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                )
                if (existing == null) PrefsManager.addRule(this, rule) else PrefsManager.updateRule(this, rule)
                render()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }
}
