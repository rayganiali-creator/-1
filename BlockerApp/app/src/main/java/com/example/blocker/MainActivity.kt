package com.example.blocker

import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.example.blocker.core.SessionMode
import com.example.blocker.health.ProtectionHealth
import com.example.blocker.session.SessionManager
import com.example.blocker.ui.AboutActivity
import com.example.blocker.ui.DiagnosticsActivity
import com.example.blocker.ui.OnboardingActivity
import com.example.blocker.ui.ProfilesActivity
import com.example.blocker.ui.ReportsActivity
import com.example.blocker.ui.RulesActivity
import com.example.blocker.ui.SchedulesActivity
import com.example.blocker.ui.SessionActivity
import com.example.blocker.ui.SettingsActivity
import com.example.blocker.ui.UiKit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** داشبورد اصلی: وضعیت واقعی محافظت، سشن جاری، شمارنده‌ها و مسیر همه‌ی بخش‌ها. */
class MainActivity : AppCompatActivity() {

    private lateinit var column: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (scroll, col) = UiKit.screen(this)
        column = col
        setContentView(scroll)
        if (!PrefsManager.isOnboardingDone(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        if (!::column.isInitialized) return
        column.removeAllViews()
        column.addView(UiKit.title(this, "بلاکر", 26f))

        val level = ProtectionHealth.protectionLevel(this)
        val a11y = ProtectionHealth.isAccessibilityEnabled(this)
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.row(this, UiKit.title(this, level.labelFa, 18f),
                UiKit.statusDot(this, if (level == com.example.blocker.core.ProtectionLevel.DEGRADED) false else true)))
            c.addView(UiKit.body(this, level.descriptionFa))
            if (!a11y) {
                c.addView(UiKit.body(this, "⚠ سرویس دسترسی خاموش است؛ در این وضعیت هیچ بلاکی اجرا نمی‌شود."))
                c.addView(UiKit.tonalButton(this, "فعال‌سازی سرویس دسترسی") {
                    startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                })
            }
        })

        val session = SessionManager.activeSession(this)
        column.addView(UiKit.card(this) { c ->
            if (session != null) {
                c.addView(UiKit.title(this, if (session.mode == SessionMode.STRICT) "سشن Strict فعال" else "سشن فعال", 18f))
                c.addView(UiKit.body(this, "پروفایل: ${session.profileName}"))
                c.addView(UiKit.body(this, "زمان باقی‌مانده: " + UiKit.formatDuration(SessionManager.remainingMillis(this@MainActivity, session))))
                if (session.status == com.example.blocker.core.SessionStatus.EXIT_PENDING) {
                    c.addView(UiKit.body(this, "درخواست خروج ثبت شده و محافظت تا پایان آرام‌سازی برقرار است."))
                }
                c.addView(UiKit.primaryButton(this, "مشاهده‌ی سشن") { startActivity(Intent(this@MainActivity, SessionActivity::class.java)) })
            } else {
                c.addView(UiKit.title(this, "شروع سریع (Quick Start)", 18f))
                c.addView(UiKit.body(this, "یک سشن فوکوس با پروفایل یا تنظیم دستی شروع کنید."))
                c.addView(UiKit.primaryButton(this, "شروع سشن") { startActivity(Intent(this@MainActivity, SessionActivity::class.java)) })
                val next = nextScheduleText()
                if (next != null) c.addView(UiKit.body(this, "زمان‌بندی بعدی: $next", 12f))
            }
        })

        val rules = PrefsManager.getRules(this)
        val appsCount = PrefsManager.getBlockedApps(this).size
        val sitesCount = PrefsManager.getBlockedWebsites(this).size
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "پیکربندی فعلی", 17f))
            c.addView(UiKit.body(this, "قوانین کلمات کلیدی: ${rules.size} (${rules.count { it.enabled }} فعال)"))
            c.addView(UiKit.body(this, "برنامه‌های هدف: $appsCount"))
            c.addView(UiKit.body(this, "دامنه‌های مسدود: $sitesCount"))
            c.addView(UiKit.body(this, "دیلی بلاک: ${PrefsManager.getDelaySeconds(this@MainActivity)} ثانیه"))
        })

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "مجوزها و سرویس‌ها", 17f))
            fun line(label: String, ok: Boolean) {
                c.addView(UiKit.row(this, UiKit.body(this, label), UiKit.statusDot(this, ok)))
            }
            line("سرویس دسترسی", a11y)
            line("نوتیفیکیشن", NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled())
            line("معافیت باتری", pm.isIgnoringBatteryOptimizations(packageName))
            line("Device Owner", com.example.blocker.admin.DeviceOwnerHelper.isDeviceOwner(this@MainActivity))
            c.addView(UiKit.tonalButton(this, "تشخیص کامل") { startActivity(Intent(this@MainActivity, DiagnosticsActivity::class.java)) })
        })

        column.addView(UiKit.title(this, "بخش‌ها", 18f))
        column.addView(UiKit.tonalButton(this, "قوانین و کلمات کلیدی") { startActivity(Intent(this, RulesActivity::class.java)) })
        column.addView(UiKit.tonalButton(this, "برنامه‌های هدف") { startActivity(Intent(this, AppsListActivity::class.java)) })
        column.addView(UiKit.tonalButton(this, "پروفایل‌های فوکوس") { startActivity(Intent(this, ProfilesActivity::class.java)) })
        column.addView(UiKit.tonalButton(this, "زمان‌بندی‌ها") { startActivity(Intent(this, SchedulesActivity::class.java)) })
        column.addView(UiKit.tonalButton(this, "گزارش‌ها") { startActivity(Intent(this, ReportsActivity::class.java)) })
        column.addView(UiKit.tonalButton(this, "تنظیمات و بکاپ") { startActivity(Intent(this, SettingsActivity::class.java)) })
        column.addView(UiKit.outlinedButton(this, "راهنمای راه‌اندازی") { startActivity(Intent(this, OnboardingActivity::class.java)) })
        column.addView(UiKit.outlinedButton(this, "درباره و محدودیت‌ها") { startActivity(Intent(this, AboutActivity::class.java)) })
    }

    private fun nextScheduleText(): String? {
        val enabled = PrefsManager.getSchedules(this).filter { it.enabled }
        if (enabled.isEmpty()) return null
        val next = enabled.minOf { com.example.blocker.scheduling.ScheduleManager.nextTrigger(this, it) }
        return SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(next))
    }
}
