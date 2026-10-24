package com.example.blocker.core

/** رأی نهایی خط لوله‌ی تصمیم. UNCERTAIN یعنی «اجازه بده ولی ثبت کن» — هرگز بلاک نمی‌کند. */
enum class AiVerdict { BLOCK, ALLOW, UNCERTAIN }

/** دلیل تصمیم — برای صفحه‌ی «چرا بلاک شد؟» و صفحه‌ی آزمایش؛ متن خام هرگز ذخیره نمی‌شود. */
enum class AiReason(val labelFa: String) {
    EXACT_MATCH("تطبیق قطعی کلمه یا عبارت"),
    SEMANTIC_MATCH("تطبیق معنایی با امتیاز کافی"),
    RECOVERY_CONTEXT("زمینه‌ی ترک/درمان — بلاک نشد"),
    ALLOWLIST("قانون اجازه (allowlist)"),
    LEARNED_ALLOW("بازخورد قبلی شما: این عبارت اشتباه بلاک شده بود"),
    NO_SIGNAL("هیچ سیگنال کافی پیدا نشد"),
    POLICY_UNCERTAIN("امتیاز در ناحیه‌ی مشکوک سیاست انتخابی — بلاک نشد"),
    FALLBACK_EXACT("بازگشت به قوانین دقیق (موتور معنایی خطا داد)"),
    ENGINE_ERROR("خطای موتور — تصمیم امن: اجازه"),
    AI_DISABLED("هوش معنایی خاموش است؛ فقط کلمات دقیق")
}

/** سیاست آستانه برای شواهد معنایی (نه تطبیق قطعی — قطعی در هر سیاستی بلاک می‌کند). */
enum class DecisionPolicy(val labelFa: String, val semanticThreshold: Double, val descriptionFa: String) {
    CONSERVATIVE(
        "محتاطانه",
        1.6,
        "فقط شواهد قوی بلاک می‌کنند؛ جفت کلمه‌ی مرتبط به‌تنهایی بلاک نمی‌شود. پیش‌فرض پیشنهادی برای کمترین بلاک اشتباه."
    ),
    BALANCED(
        "متعادل",
        1.2,
        "جفت کلمه‌ی مرتبط بلاک می‌کند؛ تک‌سیگنال مرتبط و فازی ضعیف بلاک نمی‌شوند."
    ),
    STRICT(
        "پوشش بیشتر",
        1.0,
        "آستانه پایین‌تر؛ تک‌سیگنال مرتبط یا فازی ضعیف در ناحیه‌ی مشکوک قرار می‌گیرند و بلاک نمی‌شوند، ولی پوشش بیشتر است. حذف کامل خطا را تضمین نمی‌کند."
    );

    /** ناحیه‌ی مشکوک: از ۰٫۴ زیر آستانه تا خود آستانه — اجازه + ثبت، هرگز بلاک. */
    fun uncertainFloor(): Double = semanticThreshold - 0.4

    companion object {
        fun fromName(name: String?): DecisionPolicy =
            values().firstOrNull { it.name == name } ?: CONSERVATIVE
    }
}

/** نتیجه‌ی کامل یک طبقه‌بندی — خالص و تست‌پذیر. */
data class AiDecision(
    val verdict: AiVerdict,
    val score: Double,
    val reason: AiReason,
    val rule: KeywordRule?,
    val evidence: String?,
    /** true یعنی جواب از کش آمده و طبقه‌بندی تازه‌ای انجام نشده است. */
    val fromCache: Boolean = false,
    /** پیام خطا فقط وقتی موتور یا بازگشت خطا داده باشد؛ برای نمایش «آخرین خطا». */
    val error: String? = null
)
