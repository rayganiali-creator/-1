package com.example.blocker.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.blocker.PrefsManager
import com.example.blocker.health.FixAction
import com.example.blocker.health.HealthStatus
import com.example.blocker.health.ProtectionHealth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** صفحه‌ی سلامت محافظت: وضعیت واقعی اجزا + اقدام اصلاحی برای هر کدام. */
class DiagnosticsActivity : AppCompatActivity() {

    private lateinit var column: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "سلامت و تشخیص محافظت"
        val (scroll, col) = UiKit.screen(this)
        column = col
        setContentView(scroll)
        render()
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        column.removeAllViews()
        PrefsManager.setLastDiagnosticAt(this, System.currentTimeMillis())
        val level = ProtectionHealth.protectionLevel(this)
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, level.labelFa, 19f))
            c.addView(UiKit.body(this, level.descriptionFa))
            val last = PrefsManager.getLastDiagnosticAt(this)
            if (last > 0) c.addView(UiKit.body(this, "آخرین بررسی: " + SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(last)), 12f))
        })

        ProtectionHealth.checks(this).forEach { check ->
            column.addView(UiKit.card(this) { c ->
                val ok: Boolean? = when (check.status) {
                    HealthStatus.WORKING -> true
                    HealthStatus.NEEDS_ATTENTION -> false
                    else -> null
                }
                c.addView(UiKit.row(this, UiKit.title(this, check.name, 16f), UiKit.statusDot(this, ok)))
                c.addView(UiKit.body(this, "وضعیت: ${check.status.labelFa}"))
                c.addView(UiKit.body(this, check.detail))
                if (check.fix != FixAction.NONE) {
                    c.addView(UiKit.tonalButton(this, "رفع / باز کردن تنظیمات") { openFix(check.fix) })
                }
            })
        }

        // تداوم سرویس و پس‌زمینه — فقط زمان‌ها و وضعیت‌ها؛ هیچ محتوای صفحه‌ای ثبت نمی‌شود.
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "تداوم سرویس و پس‌زمینه", 17f))
            val a11y = ProtectionHealth.isAccessibilityEnabled(this)
            c.addView(UiKit.body(this, "سرویس دسترسی: " + if (a11y) "فعال ✓" else "خاموش ✗ — بلاک اجرا نمی‌شود"))
            val fgsRunning = com.example.blocker.service.SessionForegroundService.running
            val session = com.example.blocker.session.SessionManager.activeSession(this)
            c.addView(UiKit.body(this, "سرویس پیش‌زمینه (تایمر سشن): " + if (fgsRunning) "در حال اجرا ✓" else "در حال اجرا نیست"))
            if (session != null && !fgsRunning) {
                c.addView(UiKit.body(this, "سشنی فعال است ولی سرویس پیش‌زمینه نیست؛ با باز شدن برنامه دوباره روشن می‌شود (و نوتیفیکیشن و ویجت برمی‌گردند).", 13f))
            }
            val batteryOk = (getSystemService(Context.POWER_SERVICE) as android.os.PowerManager)
                .isIgnoringBatteryOptimizations(packageName)
            c.addView(UiKit.body(this, "معافیت باتری: " + if (batteryOk) "فعال ✓" else "فعال نیست — سامسونگ ممکن است بعد از ساعت‌ها سرویس را متوقف کند"))
            val diag = PrefsManager.getServiceDiagnostics(this)
            fun fmt(t: Long): String = if (t > 0) {
                SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(t))
            } else "هنوز ثبت نشده"
            c.addView(UiKit.body(this, "آخرین فعالیت سرویس دسترسی: " + fmt(diag.lastHeartbeatMillis), 13f))
            c.addView(UiKit.body(this, "آخرین شروع سرویس پیش‌زمینه: " + fmt(diag.lastServiceStartMillis) +
                " (تعداد شروع‌ها: ${diag.serviceStartCount})", 13f))
            c.addView(UiKit.body(this,
                "توضیح صادقانه: Force Stop دستی، لغو دسترسی، یا حذف برنامه را هیچ سروسی نمی‌تواند دور بزند؛ " +
                "در آن حالت‌ها فقط باز کردن دوباره‌ی «سد» محافظت را برمی‌گرداند. مرگ فرایند توسط سیستم (بدون Force Stop) " +
                "با بازگشت به برنامه یا رویداد بوت/زمان‌بندی ترمیم می‌شود.", 12f))
            if (Build.MANUFACTURER?.equals("samsung", ignoreCase = true) == true) {
                c.addView(UiKit.body(this, "نکته‌ی سامسونگ: علاوه بر معافیت باتری، «سد» را به Never sleeping apps اضافه کن و در برنامه‌های اخیر رویش قفل بگذار.", 12f))
            }
            c.addView(UiKit.tonalButton(this, "تعمیر دسترسی‌ها") {
                startActivity(Intent(this, OnboardingActivity::class.java).apply {
                    putExtra(OnboardingActivity.EXTRA_REPAIR, true)
                })
            })
            if (!batteryOk) {
                c.addView(UiKit.tonalButton(this, "درخواست معافیت باتری") {
                    try {
                        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                    } catch (e: Exception) { openAppSettings() }
                })
            }
        })
        column.addView(UiKit.primaryButton(this, "اجرای دوباره‌ی تشخیص") { render() })
    }

    private fun openFix(fix: FixAction) {
        when (fix) {
            FixAction.OPEN_ACCESSIBILITY -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            FixAction.OPEN_NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                } else openAppSettings()
            }
            FixAction.OPEN_BATTERY -> {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                } catch (e: Exception) { openAppSettings() }
            }
            FixAction.OPEN_EXACT_ALARM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))) }
                    catch (e: Exception) { openAppSettings() }
                }
            }
            FixAction.OPEN_APP_SETTINGS -> openAppSettings()
            FixAction.NONE -> Unit
        }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }
}
