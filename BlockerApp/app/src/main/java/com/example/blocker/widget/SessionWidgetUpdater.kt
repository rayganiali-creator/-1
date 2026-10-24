package com.example.blocker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.blocker.R
import com.example.blocker.session.SessionManager
import com.example.blocker.ui.SessionActivity
import com.example.blocker.ui.UiKit

/**
 * به‌روزرسانی ویجت زمان باقی‌مانده‌ی سشن. ویجت خودش تیک ندارد؛ سرویس سشن هر ۳۰ ثانیه
 * و شروع/پایان سشن آن را تازه می‌کنند (تیک هر ثانیه برای ویجت پرهزینه و غیرضروری است).
 */
object SessionWidgetUpdater {

    fun hasWidget(context: Context): Boolean =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, SessionWidgetProvider::class.java)).isNotEmpty()

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, SessionWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val views = RemoteViews(context.packageName, R.layout.widget_session)
        val session = SessionManager.activeSession(context)
        if (session == null) {
            views.setTextViewText(R.id.widget_time, "سشنی فعال نیست")
            views.setProgressBar(R.id.widget_progress, 100, 0, false)
        } else {
            val rem = SessionManager.remainingMillis(context, session)
            views.setTextViewText(R.id.widget_time, faDigits(UiKit.formatDuration(rem)))
            val frac = if (session.durationMillis > 0)
                (rem * 100L / session.durationMillis).toInt().coerceIn(0, 100) else 0
            views.setProgressBar(R.id.widget_progress, 100, frac, false)
        }
        val intent = Intent(context, SessionActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_root, pi)
        manager.updateAppWidget(ids, views)
    }

    private fun faDigits(s: String): String {
        val map = mapOf('0' to '۰', '1' to '۱', '2' to '۲', '3' to '۳', '4' to '۴',
            '5' to '۵', '6' to '۶', '7' to '۷', '8' to '۸', '9' to '۹')
        return s.map { map[it] ?: it }.joinToString("")
    }
}
