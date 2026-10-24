package com.example.blocker.core

/**
 * مجموعه‌ی تست دقت — اعداد واقعی، نه ادعا.
 *
 * قوانین تست ثابت‌اند: یک قانون «قمار» با سیاست Balanced (آستانه‌ی ۱٫۲).
 * هر مورد یک انتظار دارد که از «سیاستِ مستند» می‌آید، نه از آرزو:
 * - BLOCK: تطبیق قطعی، فازی قوی، یا جفت مرتبط.
 * - ALLOW: بدون سیگنال، تک‌سیگنال مرتبط، یا وتوی زمینه‌ی ترک/درمان.
 * - UNCERTAIN: امتیاز در ناحیه‌ی مشکوک سیاست.
 *
 * دو مورد عمداً «نقاط ضعف شناخته‌شده»‌ی موتور فعلی‌اند و انتظارشان ALLOW است
 * ولی موتور BLOCK می‌دهد (نقل‌قول و بحث آموزشی با کلمه‌ی دقیق): این‌ها در
 * معیارها به‌عنوان مثبت کاذبِ واقعی ظاهر می‌شوند تا دقت، صادقانه زیر ۱۰۰٪ باشد.
 * بهبود آن‌ها (استثنای نقل‌قول، زمینه‌ی آموزشی) کار آینده است، نه پنهان‌کردن.
 */
object AiTestDataset {

    data class Case(val text: String, val expected: AiVerdict, val note: String)
    data class Result(
        val case: Case,
        val decision: AiDecision,
        /** true یعنی پیش‌بینی با انتظار یکی بود (UNCERTAINِ پیش‌بینی‌شده هم «ایمن» حساب می‌شود). */
        val correct: Boolean
    )
    data class Metrics(
        val total: Int,
        val correct: Int,
        val precision: Double,
        val recall: Double,
        val falsePositives: Int,
        val falseNegatives: Int,
        val uncertainPredicted: Int,
        val results: List<Result>
    )

    val rules: List<KeywordRule> = listOf(KeywordRule(id = "ds1", text = "قمار"))

    val cases: List<Case> = listOf(
        Case("قمار کردم", AiVerdict.BLOCK, "تطبیق قطعی"),
        Case("قمارها", AiVerdict.BLOCK, "ریشه‌یابی جمع"),
        Case("سایت قماز", AiVerdict.BLOCK, "فازی فاصله‌ی ۱"),
        Case("کازینو و پوکر", AiVerdict.BLOCK, "جفت مرتبط"),
        Case("شرطبندی و رولت", AiVerdict.BLOCK, "جفت مرتبط با ریشه‌یابی"),
        Case("قمار کازینو پوکر درمان", AiVerdict.BLOCK, "موضوع سنگین بر زمینه‌ی درمان غلبه می‌کند"),
        Case("قمار", AiVerdict.BLOCK, "کلمه‌ی تنها، قطعی"),
        Case("پوکر و رولت و اسلات", AiVerdict.BLOCK, "سه سیگنال مرتبط"),
        Case("قمار، قمار، قمار", AiVerdict.BLOCK, "تکرار کلمه‌ی دقیق"),
        Case("ترک قمار می‌کنم", AiVerdict.ALLOW, "زمینه‌ی ترک، وتو می‌کند"),
        Case("درمان اعتیاد به قمار", AiVerdict.ALLOW, "زمینه‌ی درمان، وتو می‌کند"),
        Case("ترک و درمان و بهبودی قمار", AiVerdict.ALLOW, "چند کلمه‌ی ترک، وتوی کامل"),
        Case("رفتم کازینو", AiVerdict.ALLOW, "تک‌سیگنال مرتبط هرگز بلاک نمی‌کند"),
        Case("کازینو", AiVerdict.ALLOW, "تک‌سیگنال مرتبط"),
        Case("فوتبال دیدم", AiVerdict.ALLOW, "بدون سیگنال"),
        Case("بازی فوتبال", AiVerdict.ALLOW, "کلمه‌ی مشابهِ بی‌ربط"),
        Case("قم", AiVerdict.ALLOW, "تکه‌کلمه‌ی کوتاه؛ فازی روی کوتاه‌ها خاموش است"),
        Case("", AiVerdict.ALLOW, "ورودی خالی"),
        Case("   ", AiVerdict.ALLOW, "فقط فاصله"),
        Case("ماهیگیری و گل", AiVerdict.ALLOW, "کلمه‌ی مبهم «گل» در خوشه نیست"),
        Case("I want to quit gambling", AiVerdict.ALLOW, "انگلیسی بدون قانون انگلیسی — خوشه‌ی معنایی فعلاً فارسی است"),
        Case("I love پوکر", AiVerdict.ALLOW, "متن مخلوط با تک‌سیگنال مرتبط"),
        Case("شرط بستم", AiVerdict.ALLOW, "تک‌سیگنال مرتبط — طبق سیاست بلاک نمی‌شود (مصالحه‌ی دقت)"),
        Case("«قمار» در لغت یعنی بخت‌آزمایی", AiVerdict.ALLOW, "نقل‌قول — نقطه‌ی ضعف شناخته‌شده: موتور نقل‌قول را مستثنی نمی‌کند"),
        Case("آموزش خطرات قمار برای نوجوانان", AiVerdict.ALLOW, "بحث آموزشی — نقطه‌ی ضعف شناخته‌شده: کلمه‌ی دقیق بدون زمینه‌ی ترک بلاک می‌شود"),
        Case("قمار کازینو درمان", AiVerdict.ALLOW, "قطعیِ وتوشده با زمینه‌ی درمان — وتو بر قطعی غلبه می‌کند"),
        Case("پوکر رولت اسلات درمان", AiVerdict.UNCERTAIN, "سه مرتبط (۱٫۸) منهای درمان (۰٫۸) = ۱٫۰ در ناحیه‌ی مشکوک Balanced")
    )

    /** ارزیابی با تابع طبقه‌بندی داده‌شده؛ سیاست تست همیشه Balanced است تا اعداد قابل‌مقایسه بمانند. */
    fun evaluate(classify: (String) -> AiDecision): Metrics {
        val results = cases.map { c ->
            val d = classify(c.text)
            val predictedAsAllow = d.verdict == AiVerdict.ALLOW || d.verdict == AiVerdict.UNCERTAIN
            val expectedAsAllow = c.expected == AiVerdict.ALLOW || c.expected == AiVerdict.UNCERTAIN
            val correct = when {
                c.expected == AiVerdict.UNCERTAIN -> d.verdict == AiVerdict.UNCERTAIN
                c.expected == AiVerdict.BLOCK -> d.verdict == AiVerdict.BLOCK
                else -> predictedAsAllow
            }
            Result(c, d, correct)
        }
        var tp = 0; var fp = 0; var fn = 0; var uncertain = 0
        for (r in results) {
            val predBlock = r.decision.verdict == AiVerdict.BLOCK
            val expBlock = r.case.expected == AiVerdict.BLOCK
            if (r.decision.verdict == AiVerdict.UNCERTAIN) uncertain++
            if (predBlock && expBlock) tp++
            if (predBlock && !expBlock) fp++
            if (!predBlock && expBlock) fn++
        }
        val precision = if (tp + fp == 0) 1.0 else tp.toDouble() / (tp + fp)
        val recall = if (tp + fn == 0) 1.0 else tp.toDouble() / (tp + fn)
        return Metrics(
            total = results.size,
            correct = results.count { it.correct },
            precision = precision,
            recall = recall,
            falsePositives = fp,
            falseNegatives = fn,
            uncertainPredicted = uncertain,
            results = results
        )
    }
}
