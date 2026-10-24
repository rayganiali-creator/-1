package com.example.blocker.core

/**
 * نگهبان «هرگز روی پنجره‌ی خودمان بلاک نکن»:
 * سرویس قبلاً فقط پکیجِ رخداد را با پکیج خودمان مقایسه می‌کرد؛ ولی وقتی کاربر
 * داخل خودِ اپ است، رخدادهایی با پکیج دیگر هم می‌رسند (مثلاً پنجره‌ی صفحه‌کلید
 * هنگام تایپ داخل اپ، یا بازبینیِ بلاکِ معلقِ زمان‌دار) و ریشه‌ی پنجره‌ی فعال
 * همان پنجره‌ی خودمان است. اگر متن همان پنجره خوانده شود، کلمه‌ی بلاک‌شده‌ای
 * که در لیست قوانین یا گزارش‌های خودِ اپ دیده می‌شود، باعث بسته شدن خودِ اپ
 * می‌شود. پس هر تصمیم بلاک باید هر دو را چک کند: پکیج رخداد و پکیج پنجره‌ی فعال.
 */
object OwnAppGuard {
    fun shouldSkip(eventPackage: String?, activeWindowPackage: String?, ownPackage: String): Boolean =
        eventPackage == ownPackage || (activeWindowPackage != null && activeWindowPackage == ownPackage)
}
