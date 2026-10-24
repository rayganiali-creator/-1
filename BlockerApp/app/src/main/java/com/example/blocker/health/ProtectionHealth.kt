package com.example.blocker.health

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import com.example.blocker.BlockAccessibilityService
import com.example.blocker.admin.DeviceOwnerHelper
import com.example.blocker.core.ProtectionLevel
import com.example.blocker.service.SessionForegroundService
import com.example.blocker.session.SessionManager

enum class HealthStatus(val labelFa: String) {
    WORKING("در حال کار"), NEEDS_ATTENTION("نیاز به توجه"), UNSUPPORTED("پشتیبانی نمی‌شود"), UNKNOWN("نامشخص")
}

enum class FixAction { OPEN_ACCESSIBILITY, OPEN_NOTIFICATIONS, OPEN_BATTERY, OPEN_EXACT_ALARM, OPEN_APP_SETTINGS, NONE }

data class HealthCheck(
    val name: String,
    val status: HealthStatus,
    val detail: String,
    val fix: FixAction = FixAction.NONE
)

/** همه‌ی وضعیت‌ها از state واقعی سیستم خوانده می‌شوند؛ هیچ «سبز» الکی نداریم. */
object ProtectionHealth {

    fun isAccessibilityEnabled(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            enabled.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
        } catch (e: Exception) {
            // fallback: رشته‌ی تنظیمات امن
            try {
                val flat = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                flat.split(':').any {
                    ComponentName.unflattenFromString(it)?.packageName == context.packageName
                }
            } catch (e2: Exception) { false }
        }
    }

    fun checks(context: Context): List<HealthCheck> {
        val out = mutableListOf<HealthCheck>()
        val a11y = isAccessibilityEnabled(context)
        out += HealthCheck(
            "سرویس دسترسی (Accessibility)",
            if (a11y) HealthStatus.WORKING else HealthStatus.NEEDS_ATTENTION,
            if (a11y) "فعال است و رخدادهای صفحه را می‌بیند." else "خاموش است؛ بدون آن هیچ بلاکی انجام نمی‌شود.",
            FixAction.OPEN_ACCESSIBILITY
        )
        val lastSeen = context.getSharedPreferences("blocker_diag", Context.MODE_PRIVATE).getLong("a11y_last_seen", 0L)
        val heartbeatFresh = lastSeen > 0 && System.currentTimeMillis() - lastSeen < 120_000L
        out += HealthCheck(
            "زنده بودن سرویس بلاک",
            when { !a11y -> HealthStatus.UNKNOWN; heartbeatFresh || BlockAccessibilityService.instanceAlive -> HealthStatus.WORKING; else -> HealthStatus.UNKNOWN },
            when {
                !a11y -> "چون سرویس خاموش است قابل بررسی نیست."
                heartbeatFresh || BlockAccessibilityService.instanceAlive -> "سرویس در حال اجراست و heartbeat تازه دارد."
                else -> "سرویس فعال است ولی heartbeat تازه‌ای دیده نشد؛ یک بار صفحه را عوض کنید."
            }
        )
        val notifOk = NotificationManagerCompat.from(context).areNotificationsEnabled()
        out += HealthCheck(
            "مجوز نوتیفیکیشن",
            if (notifOk) HealthStatus.WORKING else HealthStatus.NEEDS_ATTENTION,
            if (notifOk) "نوتیفیکیشن سشن نمایش داده می‌شود." else "نوتیفیکیشن سشن دیده نمی‌شود (خود بلاک کار می‌کند).",
            FixAction.OPEN_NOTIFICATIONS
        )
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryOk = pm.isIgnoringBatteryOptimizations(context.packageName)
        out += HealthCheck(
            "معافیت از بهینه‌سازی باتری",
            if (batteryOk) HealthStatus.WORKING else HealthStatus.NEEDS_ATTENTION,
            if (batteryOk) "اپ از بهینه‌سازی باتری معاف است." else "ممکن است سیستم سرویس را در پس‌زمینه بخواباند؛ محدودیت سازنده‌ها متفاوت است.",
            FixAction.OPEN_BATTERY
        )
        val session = SessionManager.currentRaw(context)
        out += HealthCheck(
            "سرویس پیش‌زمینه‌ی سشن",
            when { session == null -> HealthStatus.UNKNOWN; SessionForegroundService.running -> HealthStatus.WORKING; else -> HealthStatus.NEEDS_ATTENTION },
            when {
                session == null -> "سشن فعالی نیست که سرویس بخواهد."
                SessionForegroundService.running -> "در حال اجراست."
                else -> "سشن فعال است ولی سرویس پیش‌زمینه اجرا نیست؛ صفحه‌ی سشن را باز کنید."
            }
        )
        out += HealthCheck(
            "ذخیره‌سازی و ریکاوری سشن",
            if (SessionManager.isCorrupt(context)) HealthStatus.NEEDS_ATTENTION else HealthStatus.WORKING,
            if (SessionManager.isCorrupt(context)) "داده‌ی سشن خراب تشخیص داده شد؛ از صفحه‌ی ریکاوری اضطراری استفاده کنید."
            else "وضعیت سشن سالم و قابل بازیابی است و آینه‌ی ریکاوری نوشته می‌شود."
        )
        val bootEnabled = try {
            context.packageManager.getComponentEnabledSetting(
                ComponentName(context, com.example.blocker.service.BootReceiver::class.java)
            ) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } catch (e: Exception) { false }
        out += HealthCheck(
            "ریکاوری بعد از ریبوت",
            if (bootEnabled) HealthStatus.WORKING else HealthStatus.NEEDS_ATTENTION,
            if (bootEnabled) "گیرنده‌ی بوت فعال است؛ بعد از ریبوت سشن و زمان‌بندی‌ها بازیابی می‌شوند." else "گیرنده‌ی بوت غیرفعال است."
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val exact = am.canScheduleExactAlarms()
            out += HealthCheck(
                "آلارم دقیق زمان‌بندی",
                if (exact) HealthStatus.WORKING else HealthStatus.NEEDS_ATTENTION,
                if (exact) "زمان‌بندی‌ها دقیق اجرا می‌شوند." else "بدون این مجوز زمان‌بندی‌ها تقریبی اجرا می‌شوند.",
                FixAction.OPEN_EXACT_ALARM
            )
        }
        val owner = DeviceOwnerHelper.isDeviceOwner(context)
        out += HealthCheck(
            "مدیریت دستگاه (Device Owner)",
            if (owner) HealthStatus.WORKING else HealthStatus.UNSUPPORTED,
            if (owner) "اپ Device Owner است؛ بلاک حذف در Strict قابل اعمال است."
            else "اپ Device Owner نیست؛ در این حالت هیچ اپی نمی‌تواند جلوی حذف یا Force Stop را بگیرد. راهنمای پروویژن در صفحه‌ی «درباره و محدودیت‌ها» است."
        )
        val admin = DeviceOwnerHelper.isDeviceAdminActive(context)
        out += HealthCheck(
            "مدیر دستگاه (Device Admin)",
            if (admin) HealthStatus.WORKING else HealthStatus.NEEDS_ATTENTION,
            if (admin) "فعال است؛ حذف سد یک مرحله‌ی اضافه در تنظیمات > امنیت لازم دارد (اصطکاک، نه قفل سیستم)."
            else "فعال نیست؛ برای سخت‌تر شدن حذفِ لحظه‌ای، از صفحه‌ی تنظیمات فعالش کن."
        )
        val selfProt = com.example.blocker.PrefsManager.getSelfProtectionEnabled(context)
        val sessionNow = SessionManager.activeSession(context) != null
        out += HealthCheck(
            "خودمحافظتی در سشن",
            when {
                !selfProt -> HealthStatus.NEEDS_ATTENTION
                sessionNow && !a11y -> HealthStatus.NEEDS_ATTENTION
                else -> HealthStatus.WORKING
            },
            when {
                !selfProt -> "خاموش است؛ در سشن صفحه‌ی جزئیات سرویس سد در تنظیمات بسته نمی‌شود."
                sessionNow && !a11y -> "روشن است ولی سرویس Accessibility خاموش است؛ بدون آن خودمحافظتی کار نمی‌کند."
                sessionNow -> "فعال است و سشنی در جریان است؛ صفحه‌ی جزئیات سرویس سد در تنظیمات بسته می‌شود."
                else -> "روشن است؛ از سشن بعدی، صفحه‌ی جزئیات سرویس سد در تنظیمات بسته می‌شود."
            }
        )
        return out
    }

    /** سطح واقعی محافظت بر اساس چک‌های واقعی سیستم. */
    fun protectionLevel(context: Context): ProtectionLevel {
        if (!isAccessibilityEnabled(context)) return ProtectionLevel.DEGRADED
        val sessionActive = SessionManager.activeSession(context) != null
        if (sessionActive && !BlockAccessibilityService.instanceAlive &&
            context.getSharedPreferences("blocker_diag", Context.MODE_PRIVATE).getLong("a11y_last_seen", 0L) == 0L
        ) return ProtectionLevel.DEGRADED
        if (DeviceOwnerHelper.isDeviceOwner(context)) return ProtectionLevel.MANAGED
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val enhanced = pm.isIgnoringBatteryOptimizations(context.packageName) &&
            (!sessionActive || SessionForegroundService.running)
        return if (enhanced) ProtectionLevel.ENHANCED else ProtectionLevel.STANDARD
    }
}
