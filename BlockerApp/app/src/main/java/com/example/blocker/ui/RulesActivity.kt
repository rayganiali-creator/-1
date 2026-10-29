package com.example.blocker.ui

import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.blocker.PrefsManager
import com.example.blocker.core.KeywordMatcher
import com.example.blocker.core.KeywordRule
import com.example.blocker.core.RuleDelays
import com.example.blocker.session.SessionManager
import com.google.android.material.switchmaterial.SwitchMaterial

/** مدیریت قوانین کلمات کلیدی + دامنه‌ها. در Strict فعال، ویرایش قفل است. */
class RulesActivity : AppCompatActivity() {

    private lateinit var column: LinearLayout
    private var locked: Boolean = false
    private var searchQuery = ""
    private val ruleCardViews = mutableListOf<Pair<KeywordRule, View>>()

    private fun faDigits(s: String): String {
        val fa = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        return s.map { if (it in '0'..'9') fa[it - '0'] else it }.joinToString("")
    }

    /** فیلتر کارت‌های قانون بر اساس جستجو، بدون بازسازی صفحه (تا فوکوس فیلد نپرد). */
    private fun applyRuleFilter() {
        val q = searchQuery.trim()
        ruleCardViews.forEach { (rule, view) ->
            view.visibility = if (q.isEmpty() || rule.text.contains(q, ignoreCase = true) ||
                rule.category.contains(q, ignoreCase = true)
            ) View.VISIBLE else View.GONE
        }
    }

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

        column.addView(UiKit.title(this, "قوانین", 22f))
        val search = UiKit.textField(this, "جستجوی قانون")
        search.setText(searchQuery)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString() ?: ""
                applyRuleFilter()
            }
        })
        column.addView(search)
        column.addView(UiKit.body(this, "کلمه‌ها در فیلدهای جستجو و نوار آدرس شناسایی‌شده بررسی می‌شوند؛ متن پست‌ها و پیام‌های عادی نباید باعث بلاک شود. با کلید کنار هر قانون، همان قانون را روشن یا خاموش کن. تغییرها خارج از سشن هم ذخیره می‌شوند؛ در سشن Strict تنظیمات همان سشن قفل است."))
        val rules = PrefsManager.getRules(this)
        if (rules.isEmpty()) column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.body(this, "هنوز قانونی ثبت نشده است. اولین کلمه را اضافه کن تا سد جلویش را بگیرد."))
        })
        ruleCardViews.clear()
        rules.forEach { rule ->
            val cardView = UiKit.card(this) { c ->
                // ردیف عنوان + کلید (موکاپ: عنوان درشت، کلید سمت چپ)
                val titleRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                titleRow.addView(TextView(this).apply {
                    text = rule.text
                    textSize = 18f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(ThemePalette.resolve(this@RulesActivity).onSurface)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                val sw = SwitchMaterial(this).apply {
                    isChecked = rule.enabled
                    isEnabled = !locked
                    setOnCheckedChangeListener { _, checked ->
                        if (!guardLocked()) PrefsManager.updateRule(this@RulesActivity, rule.copy(enabled = checked))
                    }
                }
                titleRow.addView(sw)
                c.addView(titleRow)
                // ردیف چیپ‌های موکاپ: کل کلمه، تأخیر، دسته‌بندی
                val chipsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                chipsRow.addView(UiKit.miniChip(this, "کل کلمه"))
                val delayChip = when (rule.delaySeconds) {
                    RuleDelays.FOLLOW_GLOBAL -> "دیلی سراسری"
                    0 -> "فوری"
                    else -> "تأخیر ${faDigits(rule.delaySeconds.toString())} ثانیه"
                }
                chipsRow.addView(UiKit.miniChip(this, delayChip))
                chipsRow.addView(UiKit.miniChip(this, rule.category))
                c.addView(chipsRow)
                val optional = mutableListOf<String>()
                if (rule.caseSensitive) optional.add("حساس به بزرگی حروف")
                if (rule.isAllow) optional.add("قانون اجازه")
                if (optional.isNotEmpty()) {
                    val chipsRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    optional.forEach { chipsRow2.addView(UiKit.miniChip(this, it)) }
                    c.addView(chipsRow2)
                }
                if (rule.targetPackages.isNotEmpty()) {
                    c.addView(UiKit.body(this, "فقط در: ${rule.targetPackages.joinToString("، ")}", 12f))
                }
                val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                btns.addView(UiKit.textButton(this, "ویرایش") { if (!guardLocked()) showRuleDialog(rule) }.apply { isEnabled = !locked })
                btns.addView(UiKit.textButton(this, "حذف") {
                    if (!guardLocked()) {
                        PrefsManager.removeRule(this@RulesActivity, rule.id)
                        render()
                    }
                }.apply { isEnabled = !locked })
                c.addView(btns)
            }
            ruleCardViews.add(rule to cardView)
            column.addView(cardView)
        }
        applyRuleFilter()
        column.addView(UiKit.primaryButton(this, "قانون جدید") {
            if (!guardLocked()) showRuleDialog(null)
        })

        // پیش‌نمایش تطبیق
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "پیش‌نمایش تطبیق", 17f))
            c.addView(UiKit.body(this, "یک متن نمونه بنویسید تا ببینید کدام قانون‌ها رویش صدق می‌کنند (قبل از فعال‌سازی)."))
            val sample = UiKit.textField(this, "متن نمونه…")
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
        val sites = PrefsManager.getAllBlockedWebsites(this)
        if (sites.isEmpty()) column.addView(UiKit.body(this, "دامنه‌ای ثبت نشده است."))
        sites.sorted().forEach { domain ->
            column.addView(UiKit.card(this) { c ->
                val active = domain in PrefsManager.getBlockedWebsites(this@RulesActivity)
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                row.addView(UiKit.title(this, domain, 15f).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                row.addView(SwitchMaterial(this).apply {
                    isChecked = active
                    isEnabled = !locked
                    contentDescription = "مسدودسازی $domain"
                    setOnCheckedChangeListener { _, checked ->
                        if (!guardLocked()) PrefsManager.setBlockedWebsiteEnabled(this@RulesActivity, domain, checked)
                    }
                })
                row.addView(UiKit.textButton(this, "حذف") {
                    if (!guardLocked()) { PrefsManager.removeBlockedWebsite(this@RulesActivity, domain); render() }
                }.apply { isEnabled = !locked })
                c.addView(row)
                c.addView(UiKit.body(this, if (active) "مسدود است" else "غیرفعال است", 12f))
            })
        }
        val domainInput = UiKit.textField(this, "example.com").apply {
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
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
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val textIn = UiKit.textField(this, "کلمه یا عبارت").apply { setText(existing?.text ?: "") }
        val catIn = UiKit.textField(this, "دسته").apply { setText(existing?.category ?: "عمومی") }
        val targetsIn = UiKit.textField(this, "محدود به پکیج‌ها (با کاما، اختیاری)").apply {
            setText(existing?.targetPackages?.joinToString(",") ?: "")
        }
        box.addView(textIn); box.addView(catIn); box.addView(targetsIn)
        fun switchOption(label: String, initial: Boolean): SwitchMaterial {
            val sw = SwitchMaterial(this).apply { isChecked = initial }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                addView(UiKit.body(this@RulesActivity, label).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(sw)
            }
            box.addView(row)
            return sw
        }
        val caseS = switchOption("حساس به بزرگی/کوچکی حروف لاتین", existing?.caseSensitive ?: false)
        val allow = switchOption("قانون اجازه (allowlist) — بر بلاک غلبه می‌کند", existing?.isAllow ?: false)
        box.addView(UiKit.body(this, "تأخیر بلاک بعد از شناسایی:", 13f))
        // تأخیر بلاک این قانون بعد از شناسایی: دیلی سراسری، فوری، یا ۱ تا ۵ ثانیه —
        // به‌صورت ردیف‌های تک‌انتخابی (به‌جای اسپینر)، با تیک تیل برای گزینه‌ی انتخاب‌شده.
        val delayOptions = listOf(RuleDelays.FOLLOW_GLOBAL) + RuleDelays.ALLOWED_SECONDS
        var selectedDelay = RuleDelays.sanitize(existing?.delaySeconds ?: RuleDelays.FOLLOW_GLOBAL)
        val checkViews = mutableListOf<TextView>()
        delayOptions.forEach { opt ->
            val check = TextView(this).apply {
                text = if (opt == selectedDelay) "✓" else ""
                textSize = 16f
                setTextColor(ThemePalette.resolve(this@RulesActivity).accent)
            }
            checkViews.add(check)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 12, 0, 12)
                addView(UiKit.body(this@RulesActivity, RuleDelays.labelFa(opt)).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(check)
                setOnClickListener {
                    selectedDelay = opt
                    delayOptions.forEachIndexed { i, o -> checkViews[i].text = if (o == selectedDelay) "✓" else "" }
                }
            }
            box.addView(row)
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
                    wholeWord = true,
                    caseSensitive = caseS.isChecked,
                    isAllow = allow.isChecked,
                    targetPackages = targetsIn.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                    delaySeconds = selectedDelay
                )
                if (existing == null) PrefsManager.addRule(this, rule) else PrefsManager.updateRule(this, rule)
                render()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }
}
