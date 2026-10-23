package com.example.blocker.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.blocker.PrefsManager
import com.example.blocker.core.ScheduleCalculator
import com.example.blocker.core.SessionSchedule
import java.util.Calendar

/** ثبت آلارم زمان‌بندی‌ها با AlarmManager؛ exact فقط وقتی مجوزش هست. */
object ScheduleManager {

    fun rescheduleAll(context: Context) {
        PrefsManager.getSchedules(context).forEach { scheduleOne(context, it) }
    }

    fun scheduleOne(context: Context, schedule: SessionSchedule) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, schedule)
        am.cancel(pi)
        if (!schedule.enabled) return
        val trigger = nextTrigger(context, schedule)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            am.canScheduleExactAlarms()
        try {
            if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    fun cancel(context: Context, schedule: SessionSchedule) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, schedule))
    }

    fun nextTrigger(context: Context, schedule: SessionSchedule): Long {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val startOfToday = cal.timeInMillis
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return ScheduleCalculator.nextTriggerMillis(schedule, now, startOfToday, dow)
    }

    private fun pendingIntent(context: Context, schedule: SessionSchedule): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = "com.example.blocker.SCHEDULE_FIRE"
            putExtra(ScheduleReceiver.EXTRA_SCHEDULE_ID, schedule.id)
        }
        return PendingIntent.getBroadcast(
            context, schedule.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
