package com.example.blocker.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** ویجت مینیمال «سد»: زمان باقی‌مانده‌ی سشن + نوار پیشرفت. */
class SessionWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        SessionWidgetUpdater.updateAll(context)
    }
}
