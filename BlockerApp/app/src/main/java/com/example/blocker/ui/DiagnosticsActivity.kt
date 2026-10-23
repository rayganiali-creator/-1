package com.example.blocker.ui

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
