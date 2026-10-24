package com.example.blocker.core

/**
 * قاعده‌ی «کدام متن برای تطبیق کلمه‌ی کلیدی حساب می‌شود» (خالص، بدون اندروید).
 *
 * مشکل: تطبیق روی کل متن پنجره، صرفِ دیده شدن کلمه در لیست تاریخچه، پیشنهادهای
 * جستجو یا فید را هم بلاک می‌کرد و کاربر نمی‌توانست چیز دیگری سرچ کند.
 * قاعده‌ی جدید: کلمه فقط در دو جا حساب می‌شود —
 *  ۱) متن ورودی: جعبه‌های جستجو و نوار آدرس (گره‌های قابل‌ویرایش)؛
 *  ۲) متن محتوا: متن معمولی صفحه، به‌جز وقتی داخل یک ظرف لیستی است
 *     (ListView/RecyclerView/GridView: تاریخچه، پیشنهادها، فید).
 * پس تاریخچه به‌تنهایی هرگز بلاک نمی‌کند، ولی جستجوی فعال و محتوای صفحه هنوز
 * بلاک می‌شوند.
 */
object KeywordScope {
    /** آیا کلاس گره، یک ظرف لیستی است؟ */
    fun isListContainerClass(className: String?): Boolean {
        if (className.isNullOrBlank()) return false
        val simple = className.substringAfterLast('.')
        return simple == "ListView" || simple == "RecyclerView" || simple == "GridView"
    }

    /** آیا گره یک فیلد ورودی است؟ (EditText یا هر گره‌ی قابل‌ویرایش) */
    fun isInputNode(className: String?, isEditable: Boolean): Boolean {
        if (isEditable) return true
        if (className.isNullOrBlank()) return false
        return className.substringAfterLast('.') == "EditText"
    }
}
