package com.example.blocker.session

import android.content.Context
import com.example.blocker.core.SessionRecord
import com.example.blocker.core.SessionStatus

/**
 * آینه‌ی حداقلی و مستقل از وضعیت سشن برای ریکاوری اضطراری.
 * فایل جدا، کلیدهای جدا، نوشتن همگام (commit). RecoveryActivity عمداً فقط
 * به همین فایل و به پاک‌کردن مستقیم فایل‌های دیگر تکیه می‌کند تا حتی اگر
 * مدل سشن یا دیتابیس اصلی خراب شد، مسیر نجات کار کند.
 */
object RecoveryStore {
    const val PREFS = "blocker_recovery"
    const val KEY_SESSION_ID = "session_id"
    const val KEY_END_WALL = "end_wall"
    const val KEY_MODE = "mode"
    const val KEY_STATUS = "status"
    const val KEY_UPDATED = "updated_at"

    fun writeMirror(context: Context, record: SessionRecord) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SESSION_ID, record.id)
            .putLong(KEY_END_WALL, record.endWallMillis)
            .putString(KEY_MODE, record.mode.name)
            .putString(KEY_STATUS, record.status.name)
            .putLong(KEY_UPDATED, System.currentTimeMillis())
            .commit()
    }

    fun hasMirror(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .contains(KEY_SESSION_ID)

    fun describe(context: Context): String {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains(KEY_SESSION_ID)) return "هیچ سشن فعالی در آینه‌ی ریکاوری ثبت نشده است."
        val mode = p.getString(KEY_MODE, "?")
        val status = p.getString(KEY_STATUS, "?")
        val end = p.getLong(KEY_END_WALL, 0L)
        return "سشن ثبت‌شده: حالت $mode، وضعیت $status، پایان برنامه‌ریزی‌شده: " +
            java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.US).format(java.util.Date(end))
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }
}
