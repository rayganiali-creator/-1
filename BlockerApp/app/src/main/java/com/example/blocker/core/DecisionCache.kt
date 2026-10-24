package com.example.blocker.core

/**
 * کش محدود تصمیم‌ها: متن تکراری دوباره طبقه‌بندی نمی‌شود (صرفه‌جویی باتری و CPU).
 * - حداکثر ۱۰۰ ورودی (LRU)؛ ورودی‌های قدیمی‌تر از ۵ دقیقه منقضی می‌شوند.
 * - کلید شامل متن نرمال‌شده + سیاست + امضای قوانین + اندازه‌ی اجازه‌های آموخته‌شده
 *   است تا تغییر قانون یا سیاست، کش کهنه را باطل کند (safe invalidation).
 * - فقط تصمیم ذخیره می‌شود، نه متن خام صفحه (کلید هشِ متن است، نه خود متن).
 */
class DecisionCache(
    private val maxEntries: Int = 100,
    private val ttlMillis: Long = 5 * 60 * 1000L
) {
    private data class Entry(val decision: AiDecision, val atMillis: Long)

    private val map = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun get(key: String, nowMillis: Long): AiDecision? {
        val entry = map[key] ?: return null
        if (nowMillis - entry.atMillis > ttlMillis) {
            map.remove(key)
            return null
        }
        return entry.decision
    }

    @Synchronized
    fun put(key: String, decision: AiDecision, nowMillis: Long) {
        map[key] = Entry(decision, nowMillis)
    }

    @Synchronized fun size(): Int = map.size

    @Synchronized fun clear() = map.clear()

    companion object {
        /**
         * کلید کش: SHA-256 روی (متن نرمال‌شده + سیاست + امضای قوانین +
         * اجازه‌های آموخته‌شده) — متن خام هرگز به‌عنوان کلید نگه داشته نمی‌شود و
         * تصادم هش ۳۲بیتی هم نمی‌تواند تصمیم اشتباه برگرداند.
         */
        fun keyFor(
            normalizedText: String,
            policy: DecisionPolicy,
            rules: List<KeywordRule>,
            learned: Set<String>
        ): String {
            val rulesSig = rules.joinToString("|") { "${it.id}:${it.enabled}:${it.text}" }
            val learnedSig = learned.sorted().joinToString("|")
            val raw = "$normalizedText|${policy.name}|$rulesSig|$learnedSig"
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
