package com.example.blocker.ui

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.blocker.PrefsManager
import com.example.blocker.core.AiDecision
import com.example.blocker.core.AiEngine
import com.example.blocker.core.AiIntention
import com.example.blocker.core.AiTestDataset
import com.example.blocker.core.AiVerdict
import com.example.blocker.core.DecisionPolicy

/**
 * مدیریت «هوش روی دستگاه» — همه‌ی اعداد و وضعیت‌ها واقعی‌اند:
 * - وضعیت مدل عصبی صادقانه «در دسترس نیست» است؛ موتور فعال، موتور معنایی قطعی
 *   (ریشه‌یابی، فازی، خوشه‌ی موضوعی، زمینه‌ی ترک) است و همین‌طور برچسب می‌خورد.
 * - شمارنده‌ها فقط طبقه‌بندی‌های تازه را می‌شمرند (نه از کش) و هیچ متن صفحه‌ای
 *   در آن‌ها نیست.
 * - مثال‌های نیت برای آزمایش‌اند؛ مدل را آموزش نمی‌دهند و قانون تازه نمی‌سازند.
 */
class AiSettingsActivity : AppCompatActivity() {

    private lateinit var column: LinearLayout
    private var datasetResult: String? = null
    private var datasetResultView: TextView? = null
    private var policyDescView: TextView? = null
    // فیلدهای نیت یک بار ساخته و در رندرهای بعدی دوباره استفاده می‌شوند تا متن
    // تایپ‌شده‌ی کاربر با تغییر سیاست یا اجرای تست دقت پاک نشود (باگ نسخه‌ی قبل).
    private lateinit var nameField: EditText
    private lateinit var descField: EditText
    private lateinit var blockField: EditText
    private lateinit var allowField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "هوش روی دستگاه"
        val (scroll, col) = UiKit.screen(this)
        column = col
        setContentView(scroll)
        val intention = PrefsManager.getAiIntention(this)
        nameField = UiKit.textField(this, "نام نیت، مثلاً: دوری از قمار").apply { setText(intention.name) }
        descField = UiKit.textField(this, "توضیح کوتاه نیت").apply { setText(intention.description) }
        blockField = UiKit.textField(this, "مثال‌هایی که باید بلاک شوند (هر کدام در یک خط)").apply {
            setSingleLine(false); minLines = 3; setText(intention.blockExamples.joinToString("\n"))
        }
        allowField = UiKit.textField(this, "مثال‌هایی که نباید بلاک شوند (هر کدام در یک خط)").apply {
            setSingleLine(false); minLines = 3; setText(intention.allowExamples.joinToString("\n"))
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        column.removeAllViews()
        // نماهای ساخته‌شده در رندر قبل جدا شده‌اند؛ ارجاع‌های کهنه نباید به‌روز شوند.
        datasetResultView = null
        policyDescView = null
        val aiEnabled = PrefsManager.isAiEnabled(this)
        val policy = PrefsManager.getDecisionPolicy(this)
        val counts = PrefsManager.getAiCounts(this)
        val lastError = PrefsManager.getAiLastError(this)

        column.addView(UiKit.title(this, "هوش روی دستگاه", 22f))
        column.addView(UiKit.body(this,
            "تصمیم‌ها همه روی گوشی گرفته می‌شوند؛ هیچ متنی برای بلاک به سرور نمی‌رود."))

        // وضعیت موتور — بدون عدد ساختگی.
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "وضعیت موتور", 17f))
            c.addView(UiKit.body(this, "هوش معنایی: " + if (aiEnabled) "فعال ✓" else "خاموش — فقط کلمات دقیق"))
            c.addView(UiKit.body(this,
                "مدل عصبی: در دسترس نیست. دلیل اعلام‌شده: مدل سبکِ تأییدشده برای فارسی " +
                "(زیر ~۳۰ مگابایت) هنوز یکپارچه نشده و بدون گوشی واقعی نمی‌توان استنتاج آن را " +
                "راستی‌آزمایی کرد. موتور فعال فعلاً «موتور معنایی قطعی نسخه ۱» است: ریشه‌یابی فارسی، " +
                "تحمل غلط املایی، خوشه‌ی موضوعی، و جریمه‌ی زمینه‌ی ترک/درمان."))
            c.addView(UiKit.body(this,
                "زبان‌ها: فارسی کامل (ریشه‌یابی و خوشه)؛ انگلیسی فقط تطبیق دقیق و فازی، بدون خوشه‌ی معنایی.", 13f))
            c.addView(UiKit.body(this, "آفلاین: کاملاً آفلاین.", 13f))
            c.addView(UiKit.tonalButton(this, if (aiEnabled) "خاموش کردن هوش معنایی" else "روشن کردن هوش معنایی") {
                PrefsManager.setAiEnabled(this, !aiEnabled)
                render()
            })
        })

        // سیاست آستانه.
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "سیاست تصمیم", 17f))
            c.addView(UiKit.body(this, "تطبیق قطعی (کلمه‌ی دقیق، عبارت، یا یک حرف غلط) در هر سیاستی بلاک می‌کند؛ سیاست فقط روی شواهد معنایی اثر دارد."))
            val group = RadioGroup(this)
            DecisionPolicy.values().forEach { p ->
                group.addView(RadioButton(this).apply {
                    text = "${p.labelFa} (آستانه‌ی معنایی ${p.semanticThreshold})"
                    tag = p
                    isChecked = p == policy
                })
            }
            group.setOnCheckedChangeListener { _, checkedId ->
                val rb = findViewById<RadioButton>(checkedId)
                (rb?.tag as? DecisionPolicy)?.let {
                    PrefsManager.setDecisionPolicy(this, it)
                    // فقط توضیح به‌روز می‌شود؛ رندر کامل، متن تایپ‌شده‌ی نیت را پاک می‌کرد.
                    policyDescView?.text = it.descriptionFa
                    Toast.makeText(this, "سیاست روی «${it.labelFa}» تنظیم شد", Toast.LENGTH_SHORT).show()
                }
            }
            c.addView(group)
            val descView = UiKit.body(this, policy.descriptionFa, 13f)
            policyDescView = descView
            c.addView(descView)
        })

        // شمارنده‌ها و آخرین خطا.
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "فعالیت طبقه‌بندی (فقط عدد)", 17f))
            c.addView(UiKit.body(this,
                "طبقه‌بندی‌های تازه: ${counts.classifications} | بلاک: ${counts.blocks} | " +
                "اجازه: ${counts.allows} | مشکوک: ${counts.uncertain}"))
            c.addView(UiKit.body(this,
                "آخرین خطای موتور: " + (lastError ?: "خطایی ثبت نشده"), 13f))
            c.addView(UiKit.body(this,
                "بازگشت امن: اگر موتور معنایی خطا بدهد، قوانین دقیق جایگزین می‌شوند؛ خطا هرگز به معنای عبور آزاد نیست.", 13f))
        })

        // نیت کاربر (تک‌نیت نسخه‌ی ۱).
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "نیت من (به زبان خودت)", 17f))
            c.addView(UiKit.body(this,
                "مثال‌ها برای آزمایش نیت‌اند؛ مدل را آموزش نمی‌دهند و قانون تازه نمی‌سازند. قانون‌گذاری از صفحه‌ی قوانین انجام می‌شود.", 13f))
            c.addView(nameField)
            c.addView(descField)
            c.addView(blockField)
            c.addView(allowField)
            c.addView(UiKit.tonalButton(this, "ذخیره‌ی نیت") {
                val saved = AiIntention(
                    name = nameField.text.toString().trim(),
                    description = descField.text.toString().trim(),
                    blockExamples = blockField.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() },
                    allowExamples = allowField.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }
                )
                PrefsManager.setAiIntention(this, saved)
                Toast.makeText(this, "نیت ذخیره شد", Toast.LENGTH_SHORT).show()
            })
        })

        // مجموعه‌ی تست دقت با اعداد واقعی (سیاست تست همیشه Balanced تا قابل‌مقایسه بماند).
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "مجموعه‌ی تست دقت", 17f))
            c.addView(UiKit.body(this,
                "مجموعه‌ی ثابت ${AiTestDataset.cases.size} موردی (مثبت، استفاده‌ی بی‌ضرر از همان کلمه، بحث آموزشی، " +
                "نفی، نقل‌قول، غلط املایی، متن مخلوط، ورودی خالی) با سیاست Balanced اجرا می‌شود.", 13f))
            datasetResult?.let {
                val v = UiKit.body(this, it, 13f)
                datasetResultView = v
                c.addView(v)
            }
            c.addView(UiKit.tonalButton(this, "اجرای مجموعه‌ی تست") {
                val metrics = AiTestDataset.evaluate { text ->
                    AiEngine.classify(
                        AiTestDataset.rules, text, null, emptySet(),
                        DecisionPolicy.BALANCED, true, null, System.currentTimeMillis()
                    )
                }
                datasetResult = "نتیجه: ${metrics.correct} از ${metrics.total} درست | " +
                    "دقت (precision): ${"%.2f".format(metrics.precision)} | " +
                    "بازخوانی (recall): ${"%.2f".format(metrics.recall)} | " +
                    "مثبت کاذب: ${metrics.falsePositives} | منفی کاذب: ${metrics.falseNegatives} | " +
                    "مشکوک: ${metrics.uncertainPredicted}\n" +
                    "نکته: دو مثبت کاذب، نقاط ضعف شناخته‌شده‌اند (نقل‌قول و بحث آموزشی با کلمه‌ی دقیق)؛ " +
                    "عددها واقعی‌اند، نه هدف‌گذاری."
                // اگر نما از رندر جاری هست، در جا به‌روز شو؛ وگرنه رندر کن (فیلدهای
                // نیت حالا نمونه‌های ماندگارند و با رندر پاک نمی‌شوند).
                val v = datasetResultView
                if (v != null) v.text = datasetResult else render()
            })
        })

        // دامنه‌ی بررسی کلمات: جستجو پیش‌فرض؛ بررسی محتوای صفحه اختیاری.
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "محدوده‌ی بررسی کلمات", 17f))
            c.addView(UiKit.body(this,
                "پیش‌فرض فقط بعد از زدن دکمه‌ی جستجو/ارسال، متن ورودی بررسی می‌شود. " +
                "با روشن‌کردن گزینه‌ی زیر، متن قابل‌مشاهده‌ی بدنه‌ی صفحه هم بررسی می‌شود؛ " +
                "ممکن است در این حالت کلمات عادی داخل پست‌ها نیز باعث بلاک شوند.", 13f))
            val contentSwitch = android.widget.Switch(this).apply {
                text = "بررسی محتوای صفحه (اختیاری)"
                isChecked = PrefsManager.isPageContentBlockingEnabled(this@AiSettingsActivity)
                setTextColor(ThemePalette.resolve(this@AiSettingsActivity).onSurface)
                setOnCheckedChangeListener { _, checked ->
                    PrefsManager.setPageContentBlockingEnabled(this@AiSettingsActivity, checked)
                    Toast.makeText(this@AiSettingsActivity,
                        if (checked) "بررسی محتوای صفحه روشن شد" else "حالت جستجو فعال شد",
                        Toast.LENGTH_SHORT).show()
                }
            }
            c.addView(contentSwitch)
        })

        // حریم خصوصی.
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "حریم خصوصی", 17f))
            c.addView(UiKit.body(this,
                "چه متنی دیده می‌شود: فقط متن پنجره‌ی فعال که از راه سرویس دسترسی در دسترس است — " +
                "متن ورودی (جستجو/نوار آدرس) و محتوای غیرلیستی — و فقط وسط سشن فعال.\n" +
                "چه چیزی نگه داشته می‌شود: فقط شمارنده‌ها و دلیل تصمیم؛ متن صفحه هرگز ذخیره نمی‌شود.\n" +
                "چه چیزی هرگز: آپلود متن، اسکرین‌شات، OCR، شنود کلیپ‌بورد، یا تحلیل ابری برای بلاک روزمره.", 13f))
        })

        column.addView(UiKit.primaryButton(this, "آزمایش نیت") {
            startActivity(Intent(this, AiTestActivity::class.java))
        })
        column.addView(UiKit.outlinedButton(this, "بازگشت") { finish() })
    }
}
