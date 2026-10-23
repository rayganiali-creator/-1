package com.example.blocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.blocker.scheduling.ScheduleManager
import com.example.blocker.session.SessionManager

/**
 * ریکاوری بعد از بوت:
 * - گذارهای زمانی سشن اعمال می‌شود (اگر سشن در زمان خاموشی تمام شده، COMPLETED می‌شود).
 * - اگر سشن هنوز فعال است، سرویس پیش‌زمینه دوباره بالا می‌آید.
 * - همه‌ی زمان‌بندی‌ها دوباره ثبت می‌شوند.
 * نکته: بعد از Force Stop اندروید اجازه‌ی اجرای این گیرنده را هم نمی‌دهد تا وقتی کاربر اپ را دستی باز کند؛ این محدودیت پلتفرم است.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val active = SessionManager.activeSession(context)
        if (active != null) {
            SessionForegroundService.start(context)
        }
        ScheduleManager.rescheduleAll(context)
    }
}
