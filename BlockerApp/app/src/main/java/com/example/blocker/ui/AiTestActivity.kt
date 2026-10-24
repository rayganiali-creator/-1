package com.example.blocker.ui

import android.os.Bundle
import android.os.SystemClock
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.blocker.PrefsManager
import com.example.blocker.core.AiDecision
import com.example.blocker.core.AiEngine
import com.example.blocker.core.AiVerdict
import com.example.blocker.core.SemanticEngine
import com.example.blocker.core.TextNormalizer

/**
 * صفحه‌ی آزمایش نیت — ابزار محلی، بدون بلاک واقعی.
 * نمونه را از همان خط لوله‌ی واقعی رد می‌کند (قوانین فعلی + سیاست فعلی + اجازه‌های
 * آموخته‌شده) و رأی، امتیاز، دلیل، قانون و زمان تحلیل را نشان می‌دهد.
 * زمان تحلیل با ساعت یکنواخت اندازه‌گیری می‌شود و روی همین دستگاه واقعی است.
 */
class AiTestActivity : AppCompatActivity() {

    private lateinit var column: LinearLayout
    private var lastResult: String? = null
    private var intentionResult: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "آزمایش نیت"
        val (scroll, col) = UiKit.screen(this)
        column = col
        setContentView(scroll)
        render()
    }

    private fun classifySample(text: String): Pair<AiDecision, Double> {
        val rules = PrefsManager.getRules(this).filter { it.enabled }
        val start = SystemClock.elapsedRealtimeNanos()
        val decision = AiEngine.classify(
            rules, text, null,
            PrefsManager.getLearnedAllow(this),
            PrefsManager.getDecisionPolicy(this),
            PrefsManager.isAiEnabled(this),
            null,
            System.currentTimeMillis()
        )
        val ms = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
        return decision to ms
    }

    private fun verdictFa(v: AiVerdict): String = when (v) {
        AiVerdict.BLOCK -> "بلاک"
        AiVerdict.ALLOW -> "اجازه"
        AiVerdict.UNCERTAIN -> "مشکوک (اجازه + ثبت)"
    }

    private fun render() {
        column.removeAllViews()
        val policy = PrefsManager.getDecisionPolicy(this)
        val intention = PrefsManager.getAiIntention(this)

        column.addView(UiKit.title(this, "آزمایش نیت", 22f))
        column.addView(UiKit.body(this,
            "این صفحه فقط آزمایش است؛ هیچ بلاک واقعی انجام نمی‌شود. تحلیل با قوانین و سیاست فعلی تو انجام می‌شود."))

        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "تحلیل یک نمونه متن", 17f))
            val field = UiKit.textField(this, "متن نمونه را بنویس…")
            field.setSingleLine(false)
            field.minLines = 3
            c.addView(field)
            lastResult?.let { c.addView(UiKit.body(this, it, 13f)) }
            c.addView(UiKit.primaryButton(this, "تحلیل") {
                val text = field.text.toString()
                val (decision, ms) = classifySample(text)
                val recoveryHint = if (SemanticEngine.hasRecoveryContext(text)) {
                    "\nزمینه‌ی ترک/درمان در متن دیده شد."
                } else ""
                lastResult = "متن نرمال‌شده: ${TextNormalizer.normalize(text.take(2000))}\n" +
                    "رأی: ${verdictFa(decision.verdict)} | امتیاز: ${"%.2f".format(decision.score)} | " +
                    "سیاست: ${policy.labelFa}\n" +
                    "دلیل: ${decision.reason.labelFa}\n" +
                    "قانون: ${decision.rule?.text ?: "—"} | شاهد: ${decision.evidence ?: "—"}$recoveryHint\n" +
                    "زمان تحلیل روی این دستگاه: ${"%.2f".format(ms)} میلی‌ثانیه" +
                    if (decision.fromCache) " (از کش)" else ""
                render()
            })
        })

        if (intention.blockExamples.isNotEmpty() || intention.allowExamples.isNotEmpty()) {
            column.addView(UiKit.card(this) { c ->
                c.addView(UiKit.title(this, "آزمایش مثال‌های نیت من", 17f))
                if (intention.name.isNotBlank()) c.addView(UiKit.body(this, "نیت: ${intention.name}", 13f))
                intentionResult?.let { c.addView(UiKit.body(this, it, 13f)) }
                c.addView(UiKit.tonalButton(this, "اجرای مثال‌ها") {
                    val lines = StringBuilder()
                    intention.blockExamples.forEach { ex ->
                        val (d, _) = classifySample(ex)
                        val ok = d.verdict == AiVerdict.BLOCK
                        lines.append(if (ok) "✓ " else "✗ ").append("باید بلاک شود → ${verdictFa(d.verdict)}: $ex\n")
                    }
                    intention.allowExamples.forEach { ex ->
                        val (d, _) = classifySample(ex)
                        val ok = d.verdict != AiVerdict.BLOCK
                        lines.append(if (ok) "✓ " else "✗ ").append("نباید بلاک شود → ${verdictFa(d.verdict)}: $ex\n")
                    }
                    intentionResult = lines.toString().trim()
                    render()
                })
            })
        }

        column.addView(UiKit.outlinedButton(this, "بازگشت") { finish() })
    }
}
