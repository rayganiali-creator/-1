package com.example.blocker.core

/**
 * موتور معنایی روی دستگاه — بدون مدل عصبی، بدون اینترنت، بدون خروج متن از گوشی.
 *
 * هدف: فهمیدن «کلمه» به‌جای تطبیق خشک رشته‌ای، با دقت بالا (کمترین بلاک الکی):
 * - ریشه‌یابی سبک فارسی: «قمارها»، «قمارباز»، «قماربازی» همه به ریشه‌ی «قمار» می‌رسند.
 * - تحمل غلط املایی و دست‌کاری (فاصله‌گذاری عمدیِ حروف با تکرار حروف): «قمماار» هم گرفته می‌شود.
 * - خوشه‌ی موضوعی: کلمه‌های مرتبط با قانون (مثلاً کازینو و پوکر برای قانون قمار)
 *   هر کدام ۰٫۶ امتیاز دارند و «تکی» هرگز بلاک نمی‌کنند؛ دو سیگنال مرتبط با هم
 *   یعنی موضوع واقعاً همان موضوع است، نه یک کلمه‌ی تصادفی.
 * - زمینه‌ی ترک/درمان: اگر متن درباره‌ی ترک، درمان یا کمک‌خواهی باشد، امتیاز کم
 *   می‌شود تا «ترک قمار» مثل «قمار» بلاک نشود (کلاسیک‌ترین بلاک اشتباه).
 * - یادگیری از بازخورد: وقتی کاربر روی صفحه‌ی بلاک «این بلاک اشتباه بود» را می‌زند،
 *   همان عبارت برای همان قانون دیگر بلاک نمی‌کند.
 *
 * امتیازدهی برای هر قانون:
 *   ریشه‌ی دقیق = ۱٫۰ | فازی با فاصله‌ی ۱ (کلمات ۴ حرف به بالا) = ۱٫۰ |
 *   فازی با فاصله‌ی ۲ = ۰٫۷ | هر کلمه‌ی مرتبط متمایز = ۰٫۶ |
 *   هر کلمه‌ی ترک/درمان متمایز (حداکثر ۲ تا) = ‎−۰٫۸
 *   آستانه‌ی بلاک = ۱٫۰
 *
 * کم‌مصرفی: متن ورودی به ۲۰۰۰ کاراکتر سقف می‌خورد، فازی فقط روی کلمات ۴ حرف
 * به بالا و با اختلاف طول حداکثر ۲ اجرا می‌شود، و موتور فقط روی متنی که قبلاً
 * (در KeywordScope) محدود شده اجرا می‌شود — نه روی کل صفحه.
 */
object SemanticEngine {

    const val THRESHOLD = 1.0
    private const val EXACT_SCORE = 1.0
    private const val FUZZY_D1_SCORE = 1.0
    private const val FUZZY_D2_SCORE = 0.7
    private const val RELATED_SCORE = 0.6
    private const val RECOVERY_PENALTY = 0.8
    private const val MAX_TEXT_CHARS = 2000
    private const val MIN_FUZZY_LEN = 4
    private const val MIN_STEM_LEN = 3

    data class ScoreResult(val rule: KeywordRule, val score: Double, val evidence: String)

    /** پسوندهای فارسی برای ریشه‌یابی سبک؛ در هر تکرار بلندترین تطبیق اول امتحان می‌شود.
     * «ایم» و «اید» عمداً بیرون‌اند: در «کتابهایم» (= کتاب + ها + ی + م) چسبیدن
     * «ایم» جلوی «ها» را می‌گیرد و «کتابه» می‌سازد؛ حذف تک‌حرفی تکراری («م» بعد «ی»)
     * همان پایان‌های فعلی را هم درست ریشه می‌کند. */
    private val SUFFIXES = listOf(
        "هایی", "های", "ترین", "باز",
        "ها", "تر", "یی", "ی",
        "مان", "تان", "شان", "ات", "ان", "ون", "ین",
        "ام", "ای", "اند",
        "م", "ت", "ش", "را", "است"
    )

    /**
     * خوشه‌های موضوعی (کلمات تک‌توکنی و بدون ابهام؛ کلمات مبهم مثل «گل» و «شیشه»
     * عمداً بیرون مانده‌اند چون بلاک الکی می‌سازند). هر خوشه با ریشه‌ها مقایسه می‌شود.
     */
    private val CLUSTERS = listOf(
        setOf("قمار", "قمارباز", "قماربازی", "شرطبندی", "شرطبند", "کازینو", "پوکر", "رولت", "اسلات", "لاتاری", "بت"),
        setOf("تریاک", "حشیش", "کوکائین", "هروئین", "ماریجوانا", "کراک", "متادون", "مورفین"),
        setOf("مشروب", "شراب", "آبجو", "ویسکی", "ودکا", "الکل"),
        setOf("پورن", "پورنوگرافی", "سکس", "سکسی")
    )

    /** کلمات زمینه‌ی ترک/درمان/کمک‌خواهی — حضورشان امتیاز را کم می‌کند، نه زیاد. */
    private val RECOVERY_WORDS = setOf(
        "ترک", "درمان", "بهبودی", "مشاوره", "کمپ", "رهایی",
        "پاکی", "اعتیادزدایی", "روان‌درمانی", "گمنام", "کمک"
    )

    private val clusterStems: List<Set<String>> by lazy {
        CLUSTERS.map { cluster -> cluster.map { stem(TextNormalizer.normalize(it)) }.toSet() }
    }
    private val recoveryStems: Set<String> by lazy {
        RECOVERY_WORDS.map { stem(TextNormalizer.normalize(it)) }.toSet()
    }

    /** کلید یادگیری: «شناسه‌ی قانون | ریشه‌ی عبارت» — برای اجازه‌های آموخته‌شده. */
    fun evidenceKey(evidence: String): String = stem(TextNormalizer.normalize(evidence))

    /** ریشه‌یابی سبک: فشرده‌سازی تکرار حروف به یکی، بعد حذف تکراری پسوندها (ریشه حداقل ۳ حرف).
     * فشرده‌سازی به یکی امن است چون قانون و متن هر دو یکسان ریشه می‌شوند؛ در فارسی
     * نوشتاری، دوتاییِ واقعیِ حروف (مثل «الله») بعد از حذف تشدیدِ نرمال‌سازی
     * عملاً تک‌حرفی است و برخورد دو کلمه‌ی متفاوت به یک ریشه نادر است. */
    fun stem(word: String): String {
        if (word.length <= MIN_STEM_LEN) return word
        val collapsed = StringBuilder(word.length)
        var prev = '\u0000'
        for (ch in word) {
            if (ch == prev) continue
            prev = ch
            collapsed.append(ch)
        }
        var current = collapsed.toString()
        while (current.length > MIN_STEM_LEN) {
            val suffix = SUFFIXES.firstOrNull { current.endsWith(it) && current.length - it.length >= MIN_STEM_LEN }
                ?: break
            current = current.dropLast(suffix.length)
        }
        return current
    }

    /** فاصله‌ی لونشتاین با سقف؛ اگر از سقف بگذرد همان سقف+۱ برمی‌گردد (زودقطع‌کن). */
    fun levenshtein(a: String, b: String, max: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                if (curr[j] < rowMin) rowMin = curr[j]
            }
            if (rowMin > max) return max + 1
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }

    fun tokenize(normalized: String): List<String> =
        normalized.split(Regex("[^\\p{L}\\p{N}_]+")).filter { it.isNotEmpty() }

    private fun relatedStems(ruleStem: String): Set<String> {
        val out = mutableSetOf<String>()
        for (cluster in clusterStems) {
            if (ruleStem in cluster) out.addAll(cluster)
        }
        out.remove(ruleStem)
        return out
    }

    private fun scoreRule(
        rule: KeywordRule,
        tokens: List<String>,
        stems: List<String>,
        normalizedText: String,
        recoveryCount: Int,
        learned: Set<String>
    ): ScoreResult? {
        val needle = TextNormalizer.normalize(rule.text.trim())
        if (needle.isEmpty()) return null
        // قانون چندکلمه‌ای (عبارت): مثل قبل، تطبیق دقیق مرزی؛ بدون گسترش معنایی.
        if (needle.contains(' ')) {
            val exact = KeywordMatcher.match(rule, normalizedText) ?: return null
            val score = EXACT_SCORE - RECOVERY_PENALTY * minOf(recoveryCount, 2)
            return if (score >= THRESHOLD) ScoreResult(rule, score, exact.matchedText) else null
        }
        val ruleStem = stem(needle)
        if (ruleStem.isEmpty()) return null
        // حذف توکن‌هایی که کاربر برای همین قانون «اشتباه» اعلام کرده است.
        val kept = tokens.indices.filter { "${rule.id}|${stems[it]}" !in learned }
        if (kept.isEmpty()) return null
        var base = 0.0
        var evidence = rule.text
        // ۱) ریشه‌ی دقیق
        val exactIdx = kept.firstOrNull { stems[it] == ruleStem }
        if (exactIdx != null) {
            base = EXACT_SCORE
            evidence = tokens[exactIdx]
        } else {
            // ۲) فازی: فقط کلمات ۴ حرف به بالا، فاصله‌ی ۱ = قطعی، ۲ = نیم‌امتیاز.
            var bestDist = Int.MAX_VALUE
            var bestIdx: Int? = null
            for (idx in kept) {
                val s = stems[idx]
                if (s.length < MIN_FUZZY_LEN || kotlin.math.abs(s.length - ruleStem.length) > 2) continue
                val d = levenshtein(s, ruleStem, 2)
                if (d < bestDist) {
                    bestDist = d
                    bestIdx = idx
                    if (d <= 1) break
                }
            }
            if (bestIdx != null) {
                when (bestDist) {
                    1 -> { base = FUZZY_D1_SCORE; evidence = tokens[bestIdx] }
                    2 -> { base = FUZZY_D2_SCORE; evidence = tokens[bestIdx] }
                }
            }
        }
        // ۳) کلمات مرتبط متمایز (تکی بلاک نمی‌کند؛ جفتی یعنی موضوع).
        val related = relatedStems(ruleStem)
        val relatedTokens = kept.mapNotNull { idx ->
            if (stems[idx] in related) tokens[idx] else null
        }.distinct()
        val score = base + RELATED_SCORE * relatedTokens.size - RECOVERY_PENALTY * minOf(recoveryCount, 2)
        if (score < THRESHOLD) return null
        if (base == 0.0 && relatedTokens.isNotEmpty()) evidence = relatedTokens.first()
        return ScoreResult(rule, score, evidence)
    }

    /**
     * اولین قانون بلاکی که با امتیاز کافی روی متن صدق می‌کند؛ مثل قبل، اگر هر
     * قانون allow با امتیاز کافی صدق کند، نتیجه null است (اجازه بر بلاک غلبه می‌کند).
     */
    fun firstSemanticMatch(
        rules: List<KeywordRule>,
        rawText: String,
        packageName: String? = null,
        learned: Set<String> = emptySet()
    ): ScoreResult? {
        val normalized = TextNormalizer.normalize(rawText.take(MAX_TEXT_CHARS))
        if (normalized.isEmpty()) return null
        val tokens = tokenize(normalized)
        if (tokens.isEmpty()) return null
        val stems = tokens.map { stem(it) }
        val recoveryCount = stems.filter { it in recoveryStems }.distinct().size
        val applicable = rules.filter { r ->
            r.enabled && (r.targetPackages.isEmpty() || (packageName != null && packageName in r.targetPackages))
        }
        for (rule in applicable) {
            if (rule.isAllow && scoreRule(rule, tokens, stems, normalized, recoveryCount, learned) != null) {
                return null
            }
        }
        for (rule in applicable) {
            if (!rule.isAllow) {
                scoreRule(rule, tokens, stems, normalized, recoveryCount, learned)?.let { return it }
            }
        }
        return null
    }
}
