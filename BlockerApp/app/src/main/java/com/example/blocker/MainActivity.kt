package com.example.blocker

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import com.example.blocker.ui.ThemePalette
import com.example.blocker.ui.UiKit
import com.example.blocker.widget.SessionWidgetUpdater
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** داشبورد «سد»: کارت قهرمان گرادیانی، حلقه‌ی سشن، آمار و کاشی‌های بخش‌ها. */
class MainActivity : AppCompatActivity() {

    private lateinit var column: LinearLayout
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var ticking = false
    private var tickerRunnable: Runnable? = null
    private var liveRing: UiKit.LiveRingView? = null

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
        startTicker()
        SessionWidgetUpdater.updateAll(this)
    }

    override fun onPause() {
        super.onPause()
        stopTicker()
    }

    /** تیک هر ثانیه برای حلقه و عدد باقی‌مانده‌ی داشبورد (بدون بازسازی کل صفحه). */
    private fun startTicker() {
        if (ticking) return
        ticking = true
        val r = object : Runnable {
            override fun run() {
                if (!ticking) return
                val s = SessionManager.activeSession(this@MainActivity)
                if (s == null) { render(); stopTicker(); return }
                val rem = SessionManager.remainingMillis(this@MainActivity, s)
                val frac = if (s.durationMillis > 0) rem.toFloat() / s.durationMillis.toFloat() else 0f
                liveRing?.update(frac, faDigits(UiKit.formatDuration(rem)))
                handler.postDelayed(this, 1000L)
            }
        }
        tickerRunnable = r
        handler.post(r)
    }

    private fun stopTicker() {
        ticking = false
        tickerRunnable?.let { handler.removeCallbacks(it) }
        tickerRunnable = null
    }

    private fun faDigits(s: String): String {
        val map = mapOf('0' to '۰', '1' to '۱', '2' to '۲', '3' to '۳', '4' to '۴',
            '5' to '۵', '6' to '۶', '7' to '۷', '8' to '۸', '9' to '۹')
        return s.map { map[it] ?: it }.joinToString("")
    }

    private fun render() {
        if (!::column.isInitialized) return
        column.removeAllViews()
        liveRing = null

        // نوار بالا: لوگوی سد + عنوان + منوی «…»
        val appBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = UiKit.dp(this@MainActivity, 16) }
        }
        appBar.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_shield)
            imageTintList = android.content.res.ColorStateList.valueOf(
                ThemePalette.resolve(this@MainActivity).accent)
            layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@MainActivity, 28), UiKit.dp(this@MainActivity, 28))
        })
        appBar.addView(TextView(this).apply {
            text = "سد"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 26f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ThemePalette.resolve(this@MainActivity).onSurface)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { marginStart = UiKit.dp(this@MainActivity, 12) }
        })
        appBar.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })
        val menuBtn = TextView(this).apply {
            text = "⋮"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 26f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ThemePalette.resolve(this@MainActivity).onSurfaceVariant)
            setOnClickListener { v ->
                val popup = PopupMenu(this@MainActivity, v)
                popup.menu.add(0, 1, 0, "تشخیص کامل")
                popup.menu.add(0, 2, 0, "راهنمای راه‌اندازی")
                popup.menu.add(0, 3, 0, "درباره و محدودیت‌ها")
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> startActivity(Intent(this@MainActivity, DiagnosticsActivity::class.java))
                        2 -> startActivity(Intent(this@MainActivity, OnboardingActivity::class.java))
                        3 -> startActivity(Intent(this@MainActivity, AboutActivity::class.java))
                    }
                    true
                }
                popup.show()
            }
        }
        appBar.addView(menuBtn)
        column.addView(appBar)

        // قرص وضعیت محافظت (مینیمال: نقطه‌ی رنگی + عنوان + زیرنویس)
        val level = ProtectionHealth.protectionLevel(this)
        val a11y = ProtectionHealth.isAccessibilityEnabled(this)
        val session = SessionManager.activeSession(this)
        if (a11y && session != null) {
            column.addView(UiKit.statusPill(this, true, "محافظت فعال است", level.descriptionFa))
        } else if (a11y) {
            column.addView(UiKit.statusPill(this, false, "سشنی فعال نیست",
                "بلاک فقط وسط سشن انجام می‌شود؛ برای شروع بلاک، یک سشن شروع کن."))
        } else {
            column.addView(UiKit.statusPill(this, false, "محافظت خاموش است",
                "سرویس دسترسی خاموش است؛ در این وضعیت هیچ بلاکی اجرا نمی‌شود."))
            column.addView(UiKit.tonalButton(this, "فعال‌سازی سرویس دسترسی") {
                startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
        }

        // کارت سشن
        if (session != null) {
            val remaining = SessionManager.remainingMillis(this, session)
            val fraction = if (session.durationMillis > 0) (remaining.toFloat() / session.durationMillis.toFloat()) else 0f
            column.addView(UiKit.card(this) { c ->
                c.addView(UiKit.title(this,
                    if (session.mode == SessionMode.STRICT) "سشن Strict فعال" else "سشن فعال", 18f))
                val ring = UiKit.sessionRing(this, faDigits(UiKit.formatDuration(remaining)), fraction)
                liveRing = ring
                c.addView(ring)
                // ردیف پایین کارت: چیپ پروفایل + دکمه‌ی مشاهده‌ی سشن
                val sessionRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                sessionRow.addView(UiKit.chipView(this, session.profileName, false).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                })
                sessionRow.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                })
                sessionRow.addView(UiKit.textButton(this, "مشاهده‌ی سشن") {
                    startActivity(Intent(this, SessionActivity::class.java))
                })
                c.addView(sessionRow)
                if (session.status == com.example.blocker.core.SessionStatus.EXIT_PENDING) {
                    c.addView(UiKit.body(this, "درخواست خروج ثبت شده و محافظت تا پایان آرام‌سازی برقرار است."))
                }
            })
        } else {
            column.addView(UiKit.card(this) { c ->
                c.addView(UiKit.title(this, "شروع سریع", 18f))
                c.addView(UiKit.body(this, "یک سشن فوکوس با پروفایل یا تنظیم دستی شروع کنید."))
                val next = nextScheduleText()
                if (next != null) c.addView(UiKit.body(this, "زمان‌بندی بعدی: $next", 12f))
            })
        }

        // آمار
        val rules = PrefsManager.getRules(this)
        val appsCount = PrefsManager.getBlockedApps(this).size
        val sitesCount = PrefsManager.getBlockedWebsites(this).size
        column.addView(UiKit.statsPlainRow(this, listOf(
            faDigits(rules.size.toString()) to "قانون",
            faDigits(appsCount.toString()) to "برنامه",
            faDigits(sitesCount.toString()) to "دامنه"
        )))
        column.addView(UiKit.body(this,
            "دیلی بلاک: ${PrefsManager.getDelaySeconds(this)} ثانیه • قوانین فعال: ${rules.count { it.enabled }} از ${rules.size}", 12f))

        // کاشی‌های بخش‌ها
        column.addView(UiKit.title(this, "بخش‌ها", 18f))
        val grid = GridLayout(this).apply {
            columnCount = 3
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = UiKit.dp(this@MainActivity, 12) }
        }
        val tiles = listOf(
            Triple(R.drawable.ic_tile_list, "قوانین") { startActivity(Intent(this, RulesActivity::class.java)) },
            Triple(R.drawable.ic_tile_apps, "برنامه‌ها") { startActivity(Intent(this, AppsListActivity::class.java)) },
            Triple(R.drawable.ic_tile_person, "پروفایل‌ها") { startActivity(Intent(this, ProfilesActivity::class.java)) },
            Triple(R.drawable.ic_tile_clock, "زمان‌بندی") { startActivity(Intent(this, SchedulesActivity::class.java)) },
            Triple(R.drawable.ic_tile_chart, "گزارش‌ها") { startActivity(Intent(this, ReportsActivity::class.java)) },
            Triple(R.drawable.ic_tile_settings, "تنظیمات") { startActivity(Intent(this, SettingsActivity::class.java)) }
        )
        tiles.forEachIndexed { i, (icon, label, action) ->
            val tile = UiKit.tile(this, icon, label, action)
            val row = i / 3
            val col = i % 3
            tile.layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(row, 1, 1f),
                GridLayout.spec(col, 1, 1f)
            ).apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                val m = UiKit.dp(this@MainActivity, 4)
                setMargins(m, m, m, m)
            }
            grid.addView(tile)
        }
        column.addView(grid)

        // دکمه‌ی اصلی پایین
        column.addView(UiKit.primaryButton(this, if (session != null) "مشاهده‌ی سشن" else "شروع سشن") {
            startActivity(Intent(this, SessionActivity::class.java))
        })
    }

    private fun nextScheduleText(): String? {
        val enabled = PrefsManager.getSchedules(this).filter { it.enabled }
        if (enabled.isEmpty()) return null
        val next = enabled.minOf { com.example.blocker.scheduling.ScheduleManager.nextTrigger(this, it) }
        return SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(next))
    }
}
