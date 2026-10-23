package com.example.blocker.core

/**
 * اعتبارسنجی فایل بکاپ قبل از اعمال. ورودی ساختار Map است تا codec (org.json)
 * در لایه‌ی اندروید بماند و این بخش خالص و تست‌پذیر باشد.
 * هیچ بکاپی هرگز پرمیشن یا Device Owner را برنمی‌گرداند؛ فقط کانفیگ کاربر را.
 */
object BackupValidator {
    const val CURRENT_SCHEMA = 1

    /** @return لیست خطاها؛ خالی یعنی معتبر. */
    fun validate(data: Map<String, Any?>): List<String> {
        val errors = mutableListOf<String>()
        val schema = (data["schemaVersion"] as? Number)?.toInt()
        if (schema == null) {
            errors += "فیلد schemaVersion در بکاپ وجود ندارد"
        } else if (schema != CURRENT_SCHEMA) {
            errors += "نسخه‌ی بکاپ پشتیبانی نمی‌شود: $schema"
        }
        (data["keywords"] as? List<*>)?.forEachIndexed { i, item ->
            val m = item as? Map<*, *> ?: run { errors += "قانون شماره ${i + 1} نامعتبر است"; return@forEachIndexed }
            if ((m["text"] as? String).isNullOrBlank()) errors += "قانون شماره ${i + 1} متن ندارد"
        }
        (data["websites"] as? List<*>)?.forEachIndexed { i, item ->
            val s = item as? String ?: run { errors += "سایت شماره ${i + 1} نامعتبر است"; return@forEachIndexed }
            if (s.isBlank() || s.contains(" ") || !s.contains(".")) errors += "دامنه‌ی نامعتبر: $s"
        }
        (data["apps"] as? List<*>)?.forEach { item ->
            val s = item as? String
            if (s.isNullOrBlank() || !s.contains(".")) errors += "پکیج نامعتبر در بکاپ: $item"
        }
        (data["delaySeconds"] as? Number)?.toInt()?.let {
            if (it !in BlockingDelays.ALLOWED_SECONDS) errors += "دیلی نامعتبر در بکاپ: $it"
        }
        return errors
    }
}

/**
 * مدیر دیلی بلاک: فقط یک اقدام معلق در هر لحظه؛ اقدام قدیمی هرگز نباید
 * کانتکست جدید را ببندد. خالص و تست‌پذیر؛ زمان را caller می‌دهد.
 */
class PendingBlockManager {
    data class Pending(val signature: String, val fireAtMillis: Long, val ruleId: String?)

    private var pending: Pending? = null

    /** زمان‌بندی یک بلاک؛ اگر برای همان امضا قبلاً زمان‌بندی شده، همان قبلی حفظ می‌شود (idempotent). */
    fun schedule(signature: String, ruleId: String?, nowMillis: Long, delaySeconds: Int): Pending {
        val existing = pending
        if (existing != null && existing.signature == signature) return existing
        val p = Pending(signature, nowMillis + delaySeconds * 1000L, ruleId)
        pending = p
        return p
    }

    /** وقتی کانتکست عوض/نامعتبر شد، اقدام معلق کنسل می‌شود. */
    fun cancelIfDifferent(currentSignature: String?) {
        if (pending?.signature != currentSignature) pending = null
    }

    fun cancel() { pending = null }

    fun current(): Pending? = pending

    /** اگر زمان اجرا رسیده و امضا هنوز همان است true؛ و اقدام مصرف می‌شود (یک‌بار). */
    fun consumeIfDue(nowMillis: Long, currentSignature: String?): Boolean {
        val p = pending ?: return false
        if (p.signature != currentSignature) { pending = null; return false }
        if (nowMillis < p.fireAtMillis) return false
        pending = null
        return true
    }
}
