package com.example.blocker.core

/**
 * نرمال‌سازی متن فارسی/عربی/انگلیسی برای تطبیق کلمات کلیدی.
 * خالص و بدون وابستگی به اندروید تا با تست یونیت قابل بررسی باشد.
 *
 * کارها:
 * - یکدست‌سازی حروف عربی/فارسی: ي→ی، ك→ک، ة→ه، ۀ→ه، أ/إ/ٱ→ا، ؤ→و، ئ→ی
 * - حذف اعراب (فتحه/ضمه/کسره/تشدید و …) و تطویل (ـ)
 * - تبدیل ارقام عربی (٠-٩) و فارسی (۰-۹) به ارقام لاتین
 * - تبدیل نیم‌فاصله (ZWNJ) و انواع فاصله به فاصله‌ی معمولی، و فشرده‌سازی فاصله‌های تکراری
 * - کوچک‌کردن حروف لاتین
 */
object TextNormalizer {

    fun normalize(input: String): String {
        if (input.isEmpty()) return ""
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                'ي', 'ئ' -> sb.append('ی')
                'ك' -> sb.append('ک')
                'ة', 'ۀ' -> sb.append('ه')
                'أ', 'إ', 'ٱ' -> sb.append('ا')
                'ؤ' -> sb.append('و')
                'ـ' -> Unit // تطویل: حذف
                '‌', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', '　' -> sb.append(' ')
                in 'ً'..'ٟ', 'ٰ' -> Unit // اعراب و نشانه‌های قرآنی: حذف
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                else -> sb.append(ch.lowercaseChar())
            }
        }
        // فشرده‌سازی فاصله‌های پشت سر هم و trim
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    /** آیا کاراکتر جزو حروف/ارقام یک «کلمه» حساب می‌شود (برای مرزبندی whole-word). */
    fun isWordChar(ch: Char): Boolean = ch.isLetterOrDigit() || ch == '_'
}
