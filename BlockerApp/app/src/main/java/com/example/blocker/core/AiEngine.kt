package com.example.blocker.core

/**
 * خط لوله‌ی تصمیم روی دستگاه — ترکیب «پیش‌فیلتر قطعی سریع + طبقه‌بندی معنایی»:
 *
 * ۱) اگر هوش معنایی خاموش باشد: فقط تطبیق دقیق کلمات (رفتار قدیمی، بدون تغییر).
 * ۲) پیش‌فیلتر ارزان: متن نرمال و سقف‌خورده؛ اگر هیچ توکنی با قوانین مرتبط نیست،
 *    موتور معنایی اصلاً صدا نمی‌شود (در عمل همان نبودِ تطبیق است).
 * ۳) کش: متن تکراری (با همان سیاست و قوانین) از کش جواب می‌گیرد.
 * ۴) موتور معنایی: تطبیق قطعی (ریشه‌ی دقیق/عبارت/فازی فاصله‌ی ۱) در هر سیاستی
 *    بلاک می‌کند؛ شواهد معنایی فقط اگر به آستانه‌ی سیاست برسند بلاک می‌کنند؛
 *    امتیاز در ناحیه‌ی مشکوک → UNCERTAIN (اجازه + ثبت، هرگز بلاک).
 * ۵) بازگشت امن: اگر موتور معنایی استثنا بدهد، قوانین دقیق جایگزین می‌شوند و
 *    خطا در تصمیم برمی‌گردد تا UI «آخرین خطا» را صادقانه نشان بدهد. خطای موتور
 *    هرگز به‌معنای «اجازه‌ی بی‌قید» نیست.
 *
 * نکته‌ی نخ (threading): این طبقه‌بندی عمداً سبک و محدود است (متن سقف‌دار،
 * قوانین کم، بدون تنسور) و در حد زیر میلی‌ثانیه تمام می‌شود؛ به همین دلیل روی
 * نخ رخداد دسترسی اجرا می‌شود. اگر روزی مدل عصبی اضافه شد، آن مرحله «باید» به
 * نخ پس‌زمینه منتقل شود — این یک بدهی معماریِ ثبت‌شده است، نه یک فرض پنهان.
 */
object AiEngine {

    fun classify(
        rules: List<KeywordRule>,
        rawText: String,
        packageName: String?,
        learned: Set<String>,
        policy: DecisionPolicy,
        aiEnabled: Boolean,
        cache: DecisionCache?,
        nowMillis: Long
    ): AiDecision {
        val normalized = TextNormalizer.normalize(rawText.take(2000))
        if (normalized.isEmpty()) {
            return AiDecision(AiVerdict.ALLOW, 0.0, AiReason.NO_SIGNAL, null, null)
        }
        // مسیر قطعی (هوش خاموش): رفتار قدیمی، بدون معنایی.
        if (!aiEnabled) {
            val exact = KeywordMatcher.firstBlockMatch(rules, normalized, packageName)
            return if (exact != null) {
                AiDecision(AiVerdict.BLOCK, 1.0, AiReason.AI_DISABLED, exact.rule, exact.matchedText)
            } else {
                AiDecision(AiVerdict.ALLOW, 0.0, AiReason.AI_DISABLED, null, null)
            }
        }
        // کش (فقط وقتی هوش روشن است).
        val key = cache?.let { DecisionCache.keyFor(normalized, policy, rules, learned) }
        if (cache != null && key != null) {
            cache.get(key, nowMillis)?.let { return it.copy(fromCache = true) }
        }
        val decision = try {
            classifySemantic(rules, normalized, packageName, learned, policy)
        } catch (e: Exception) {
            // بازگشت امن به قوانین دقیق — نه عبور آزاد.
            val exact = try {
                KeywordMatcher.firstBlockMatch(rules, normalized, packageName)
            } catch (ignored: Exception) {
                null
            }
            if (exact != null) {
                AiDecision(AiVerdict.BLOCK, 1.0, AiReason.FALLBACK_EXACT, exact.rule, exact.matchedText, error = e.message)
            } else {
                AiDecision(AiVerdict.ALLOW, 0.0, AiReason.ENGINE_ERROR, null, null, error = e.message)
            }
        }
        if (cache != null && key != null) cache.put(key, decision, nowMillis)
        return decision
    }

    private fun classifySemantic(
        rules: List<KeywordRule>,
        normalized: String,
        packageName: String?,
        learned: Set<String>,
        policy: DecisionPolicy
    ): AiDecision {
        // کف را تا ناحیه‌ی مشکوک سیاست پایین می‌آوریم تا UNCERTAIN هم دیده شود.
        val match = SemanticEngine.firstSemanticMatch(
            rules, normalized, packageName, learned, minScore = policy.uncertainFloor()
        ) ?: return AiDecision(AiVerdict.ALLOW, 0.0, AiReason.NO_SIGNAL, null, null)
        // تطبیق قطعی: با امتیاز کامل (≥ ۱٫۰) در هر سیاستی بلاک؛ اگر جریمه‌ی زمینه‌ی
        // ترک امتیازش را زیر ۱٫۰ آورده باشد، وتو شده است (نه مشکوک).
        if (match.exact) {
            return if (match.score + 1e-9 >= 1.0) {
                AiDecision(AiVerdict.BLOCK, match.score, AiReason.EXACT_MATCH, match.rule, match.evidence)
            } else {
                AiDecision(AiVerdict.ALLOW, match.score, AiReason.RECOVERY_CONTEXT, match.rule, match.evidence)
            }
        }
        return when {
            match.score + 1e-9 >= policy.semanticThreshold ->
                AiDecision(AiVerdict.BLOCK, match.score, AiReason.SEMANTIC_MATCH, match.rule, match.evidence)
            match.score + 1e-9 >= policy.uncertainFloor() ->
                AiDecision(AiVerdict.UNCERTAIN, match.score, AiReason.POLICY_UNCERTAIN, match.rule, match.evidence)
            else ->
                AiDecision(AiVerdict.ALLOW, match.score, AiReason.NO_SIGNAL, match.rule, match.evidence)
        }
    }
}
