package com.example.blocker.core

/** یک قانون کلمه‌کلیدی نسخه‌دار؛ جایگزین StringSet ساده‌ی قبلی. */
/** تأخیر بلاکِ هر قانون بعد از شناسایی: ۱- = دنباله‌روی دیلی سراسری/سشن، ۰ = فوری، ۱ تا ۵ = ثانیه. */
object RuleDelays {
    const val FOLLOW_GLOBAL = -1
    val ALLOWED_SECONDS = listOf(0, 1, 2, 3, 4, 5)

    fun sanitize(seconds: Int): Int =
        if (seconds == FOLLOW_GLOBAL || seconds in ALLOWED_SECONDS) seconds else FOLLOW_GLOBAL

    fun labelFa(seconds: Int): String = when (seconds) {
        FOLLOW_GLOBAL -> "دیلی سراسری"
        0 -> "فوری"
        else -> "$seconds ثانیه"
    }
}

data class KeywordRule(
    val id: String,
    val text: String,
    val category: String = "عمومی",
    val enabled: Boolean = true,
    val wholeWord: Boolean = false,
    val caseSensitive: Boolean = false,
    /** اگر true باشد، این قانون «اجازه» است و بر بلاک غلبه می‌کند (allowlist). */
    val isAllow: Boolean = false,
    /** پکیج‌هایی که قانون فقط روی آن‌ها اعمال می‌شود؛ خالی = همه‌ی برنامه‌ها. */
    val targetPackages: Set<String> = emptySet(),
    /** تأخیر بلاک این قانون بعد از شناسایی؛ ‎-1 یعنی دنباله‌روی دیلی سراسری/سشن. */
    val delaySeconds: Int = RuleDelays.FOLLOW_GLOBAL
) {
    /** تأخیر مؤثر این قانون: مقدار خودش، یا اگر روی «دیلی سراسری» بود، مقدار سراسری/سشن. */
    fun effectiveDelaySeconds(fallback: Int): Int = if (delaySeconds >= 0) delaySeconds else fallback
}

/** نتیجه‌ی تطبیق یک قانون با یک متن. */
data class MatchResult(val rule: KeywordRule, val matchedText: String, val startIndex: Int)

/**
 * موتور تطبیق کلمات کلیدی؛ خالص و تست‌پذیر.
 * متن و قانون هر دو اول نرمال می‌شوند (مگر قانون caseSensitive باشد که فقط
 * نرمال‌سازی فارسی/ارقام اعمال می‌شود و lowercase نمی‌شود… در عمل برای فارسی
 * تفاوتی ندارد؛ برای لاتین حساسیت به بزرگی حروف حفظ می‌شود).
 */
object KeywordMatcher {

    private fun prepare(text: String, caseSensitive: Boolean): String {
        val n = TextNormalizer.normalize(text)
        return if (caseSensitive) {
            // normalize حروف را lowercase کرده؛ برای حالت حساس، نسخه‌ی بدون lowercase لازم است.
            // چون normalize داخلی lowercase می‌کند، در حالت caseSensitive متن اصلی را
            // فقط از نظر حروف عربی/فارسی و ارقام یکدست می‌کنیم:
            normalizeLettersOnly(text)
        } else n
    }

    /** نرمال‌سازی فقط در سطح حروف/ارقام/فاصله، بدون تغییر case. */
    fun normalizeLettersOnly(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                'ي', 'ئ' -> sb.append('ی')
                'ك' -> sb.append('ک')
                'ة', 'ۀ' -> sb.append('ه')
                'أ', 'إ', 'ٱ' -> sb.append('ا')
                'ؤ' -> sb.append('و')
                'ـ' -> Unit
                '‌', ' ' -> sb.append(' ')
                in 'ً'..'ٟ', 'ٰ' -> Unit
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    /**
     * بررسی یک قانون روی یک متن. اگر قانون غیرفعال یا خالی باشد null برمی‌گردد.
     * wholeWord=true یعنی دو طرف تطبیق نباید حرف/رقم باشد (مرز کلمه).
     */
    fun match(rule: KeywordRule, rawText: String): MatchResult? {
        if (!rule.enabled) return null
        val needleRaw = rule.text.trim()
        if (needleRaw.isEmpty()) return null
        val haystack = prepare(rawText, rule.caseSensitive)
        val needle = prepare(needleRaw, rule.caseSensitive)
        if (needle.isEmpty()) return null

        var from = 0
        while (true) {
            val idx = haystack.indexOf(needle, from)
            if (idx < 0) return null
            if (!rule.wholeWord || isWholeWordAt(haystack, idx, needle.length)) {
                return MatchResult(rule, needle, idx)
            }
            from = idx + 1
        }
    }

    private fun isWholeWordAt(text: String, start: Int, len: Int): Boolean {
        val beforeOk = start == 0 || !TextNormalizer.isWordChar(text[start - 1])
        val end = start + len
        val afterOk = end >= text.length || !TextNormalizer.isWordChar(text[end])
        return beforeOk && afterOk
    }

    /**
     * اولین قانون بلاکی که روی متن صدق می‌کند را برمی‌گرداند؛
     * اگر هر قانون allow روی متن صدق کند، نتیجه null است (اجازه بر بلاک غلبه می‌کند).
     * [packageName] برای قوانین محدود به برنامه‌ی خاص استفاده می‌شود.
     */
    fun firstBlockMatch(rules: List<KeywordRule>, rawText: String, packageName: String? = null): MatchResult? {
        val applicable = rules.filter { r ->
            r.targetPackages.isEmpty() || (packageName != null && packageName in r.targetPackages)
        }
        for (rule in applicable) {
            if (rule.isAllow && match(rule, rawText) != null) return null
        }
        for (rule in applicable) {
            if (!rule.isAllow) {
                match(rule, rawText)?.let { return it }
            }
        }
        return null
    }

    /** پیش‌نمایش: همه‌ی قانون‌هایی که روی متن نمونه صدق می‌کنند (برای صفحه‌ی قوانین). */
    fun previewMatches(rules: List<KeywordRule>, sample: String, packageName: String? = null): List<MatchResult> {
        return rules.filter { r ->
            r.targetPackages.isEmpty() || (packageName != null && packageName in r.targetPackages)
        }.mapNotNull { match(it, sample) }
    }
}
