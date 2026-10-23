package com.example.blocker.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.blocker.PrefsManager
import com.example.blocker.service.SessionForegroundService
import com.example.blocker.session.SessionManager

/**
 * اجرای یک زمان‌بندی. سیاست تداخل (شفاف و مستند): اگر سشنی فعال باشد،
 * زمان‌بندی جدید شروع نمی‌شود؛ رویداد به‌عنوان «از دست‌رفته به علت تداخل»
 * در گزارش‌ها ثبت می‌شود و نوبت بعدی ثبت می‌گردد.
 * زمان‌بندی هرگز به‌تنهایی Device Owner یا پالیسی مدیریتی فعال نمی‌کند.
 */
class ScheduleReceiver : BroadcastReceiver() {

    companion object { const val EXTRA_SCHEDULE_ID = "schedule_id" }

    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_SCHEDULE_ID) ?: return
        val schedule = PrefsManager.getSchedules(context).firstOrNull { it.id == id } ?: return
        if (!schedule.enabled) return

        val profile = PrefsManager.getProfiles(context).firstOrNull { it.id == schedule.profileId }
        if (profile == null) {
            PrefsManager.recordProtectionFailure(context, "schedule_profile_missing")
            ScheduleManager.scheduleOne(context, schedule)
            return
        }

        if (SessionManager.activeSession(context) != null) {
            PrefsManager.recordProtectionFailure(context, "schedule_conflict_active_session")
            ScheduleManager.scheduleOne(context, schedule)
            return
        }

        SessionManager.startSession(
            context = context,
            mode = profile.mode,
            durationMillis = profile.durationMillis,
            profile = profile,
            delaySeconds = profile.delaySeconds,
            coolingOff = profile.coolingOff
        )
        SessionForegroundService.start(context)
        ScheduleManager.scheduleOne(context, schedule) // نوبت بعدی
    }
}
