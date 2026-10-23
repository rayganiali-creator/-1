package com.example.blocker.ui

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.blocker.PrefsManager
import com.example.blocker.core.FocusProfile
import com.example.blocker.service.SessionForegroundService
import com.example.blocker.session.SessionManager

/** پروفایل‌های فوکوس: preset ها + ساخت پروفایل از کانفیگ فعلی + شروع سریع. */
class ProfilesActivity : AppCompatActivity() {

    private lateinit var column: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "پروفایل‌های فوکوس"
        val (scroll, col) = UiKit.screen(this)
        column = col
        setContentView(scroll)
        render()
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        column.removeAllViews()
        PrefsManager.getProfiles(this).forEach { p ->
            column.addView(UiKit.card(this) { c ->
                c.addView(UiKit.title(this, p.name + if (p.builtin) "" else " (شخصی)", 17f))
                c.addView(UiKit.body(this, "مدت: ${UiKit.formatMinutes(p.durationMillis / 60_000L)} • حالت: ${if (p.mode.name == "STRICT") "Strict" else "عادی"} • دیلی: ${p.delaySeconds} ثانیه"))
                if (p.keywordCategories.isNotEmpty()) c.addView(UiKit.body(this, "دسته‌ها: ${p.keywordCategories.joinToString("، ")}", 12f))
                if (p.blockedApps.isNotEmpty()) c.addView(UiKit.body(this, "برنامه‌های خاص این پروفایل: ${p.blockedApps.size}", 12f))
                c.addView(UiKit.tonalButton(this, "شروع با این پروفایل") {
                    if (SessionManager.activeSession(this) != null) {
                        Toast.makeText(this, "یک سشن فعال است؛ اول آن را تمام کنید.", Toast.LENGTH_LONG).show()
                        return@tonalButton
                    }
                    startActivity(Intent(this, SessionActivity::class.java))
                })
                if (!p.builtin) {
                    c.addView(UiKit.outlinedButton(this, "حذف پروفایل شخصی") {
                        PrefsManager.deleteCustomProfile(this, p.id)
                        render()
                    })
                }
            })
        }
        column.addView(UiKit.primaryButton(this, "ساخت پروفایل از کانفیگ فعلی") { createFromCurrent() })
        column.addView(UiKit.body(this, "پروفایل از اپ‌های مسدود، دسته‌های قوانین و تنظیمات فعلی ساخته می‌شود؛ مدت و حالت را هنگام شروع سشن هم می‌توانید تنظیم کنید.", 12f))
    }

    private fun createFromCurrent() {
        val nameIn = EditText(this).apply { hint = "نام پروفایل" }
        AlertDialog.Builder(this)
            .setTitle("پروفایل جدید")
            .setView(nameIn)
            .setPositiveButton("ساخت") { _, _ ->
                val name = nameIn.text.toString().trim()
                if (name.isEmpty()) { Toast.makeText(this, "نام لازم است", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val profile = FocusProfile(
                    id = PrefsManager.newId(),
                    name = name,
                    blockedApps = PrefsManager.getBlockedApps(this),
                    keywordCategories = PrefsManager.categories(this).toSet(),
                    delaySeconds = PrefsManager.getDelaySeconds(this),
                    coolingOff = PrefsManager.getDefaultCoolingOff(this)
                )
                PrefsManager.saveCustomProfile(this, profile)
                render()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }
}
