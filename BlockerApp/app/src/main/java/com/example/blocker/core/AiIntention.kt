package com.example.blocker.core

/**
 * نیت کاربر به زبان خودش — نسخه‌ی ۱، صادقانه و محدود:
 * - نام، توضیح، مثال‌های «باید بلاک شود» و «نباید بلاک شود».
 * - مثال‌ها برای «آزمایش» نیت‌اند (صفحه‌ی آزمایش آن‌ها را از موتور رد می‌کند و
 *   نتیجه را نشان می‌دهد)؛ مثال‌ها مدل را آموزش نمی‌دهند و قانون تازه نمی‌سازند.
 *   قانون‌گذاری هنوز از صفحه‌ی قوانین انجام می‌شود.
 * - یک نیت فعال ذخیره می‌شود (نه فهرست) تا نسخه‌ی ۱ ساده و قابل‌آزمایش بماند.
 *
 * سری‌سازی عمداً بدون org.json نوشته شده: نسخه‌ی android.jar فقط استاب است و در
 * تست JVM کار نمی‌کند؛ این فرمت فقط چهار فیلد رشته‌ای دارد و تجزیه‌کننده‌ی
 * کوچکِ خودش برایش کافی است. ورودی خراب → نیت خالی، نه سقوط.
 */
data class AiIntention(
    val name: String = "",
    val description: String = "",
    val blockExamples: List<String> = emptyList(),
    val allowExamples: List<String> = emptyList()
) {
    fun toJsonString(): String {
        fun q(s: String): String = buildString {
            append('"')
            for (ch in s) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
                }
            }
            append('"')
        }
        return "{\"name\":${q(name)},\"description\":${q(description)}," +
            "\"blockExamples\":${q(blockExamples.joinToString("\n"))}," +
            "\"allowExamples\":${q(allowExamples.joinToString("\n"))}}"
    }

    companion object {
        fun fromJsonString(json: String?): AiIntention {
            val map = parseStringObject(json) ?: return AiIntention()
            fun lines(key: String): List<String> =
                (map[key] ?: "").lines().map { it.trim() }.filter { it.isNotEmpty() }
            return AiIntention(
                name = map["name"] ?: "",
                description = map["description"] ?: "",
                blockExamples = lines("blockExamples"),
                allowExamples = lines("allowExamples")
            )
        }

        /** تجزیه‌کننده‌ی حداقلی برای آبجکتِ {رشته: رشته}؛ خراب → null. */
        private fun parseStringObject(json: String?): Map<String, String>? {
            if (json.isNullOrBlank()) return null
            val s = json.trim()
            var i = 0
            fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }
            fun readString(): String? {
                if (i >= s.length || s[i] != '"') return null
                i++
                val sb = StringBuilder()
                while (i < s.length) {
                    val ch = s[i++]
                    if (ch == '"') return sb.toString()
                    if (ch == '\\') {
                        if (i >= s.length) return null
                        when (val e = s[i++]) {
                            '\\' -> sb.append('\\')
                            '"' -> sb.append('"')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) return null
                                val code = s.substring(i, i + 4).toIntOrNull(16) ?: return null
                                sb.append(code.toChar())
                                i += 4
                            }
                            else -> return null
                        }
                    } else sb.append(ch)
                }
                return null
            }
            skipWs()
            if (i >= s.length || s[i] != '{') return null
            i++
            val out = mutableMapOf<String, String>()
            skipWs()
            if (i < s.length && s[i] == '}') return out
            while (true) {
                skipWs()
                val key = readString() ?: return null
                skipWs()
                if (i >= s.length || s[i] != ':') return null
                i++
                skipWs()
                val value = readString() ?: return null
                out[key] = value
                skipWs()
                if (i >= s.length) return null
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return out
                    else -> return null
                }
            }
        }
    }
}
