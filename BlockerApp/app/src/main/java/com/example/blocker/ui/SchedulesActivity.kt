package com.example.blocker.ui

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.blocker.PrefsManager
import com.example.blocker.core.SessionSchedule
import com.example.blocker.scheduling.ScheduleManager
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** زمان‌بندی سشن‌ها (یک‌باره یا هفتگی). تداخل با سشن فعال = رد شدن و ثبت در گزارش. */
class SchedulesActivity : AppCompatActivity() {

    private lateinit var column: LinearLayout
    private val dayNames = listOf(1 to "یکشنبه", 2 to "دوشنبه", 3 to "سه‌شنبه", 4 to "چهارشنبه", 5 to "پنجشنبه", 6 to "جمعه", 7 to "شنبه")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "زمان‌بندی سشن‌ها"
        val (scroll, col) = UiKit.screen(this)
        column = col
        setContentView(scroll)
        render()
    }

    private fun render() {
        column.removeAllViews()
        val profiles = PrefsManager.getProfiles(this)
        fun profileName(id: String) = profiles.firstOrNull { it.id == id }?.name ?: "(پروفایل حذف شده)"
        val schedules = PrefsManager.getSchedules(this)
        if (schedules.isEmpty()) column.addView(UiKit.body(this, "زمان‌بندی‌ای ثبت نشده است."))
        schedules.forEach { s ->
            column.addView(UiKit.card(this) { c ->
                val sw = SwitchMaterial(this).apply {
                    text = "${profileName(s.profileId)} — ساعت %02d:%02d".format(s.hour, s.minute)
                    isChecked = s.enabled
                    setOnCheckedChangeListener { _, checked ->
                        val updated = PrefsManager.getSchedules(this@SchedulesActivity).map {
                            if (it.id == s.id) it.copy(enabled = checked) else it
                        }
                        PrefsManager.setSchedules(this@SchedulesActivity, updated)
                        ScheduleManager.scheduleOne(this@SchedulesActivity, s.copy(enabled = checked))
                        render()
                    }
                }
                c.addView(sw)
                c.addView(UiKit.body(this, if (s.daysOfWeek.isEmpty()) "یک‌باره (نزدیک‌ترین زمان)" else "روزها: " + s.daysOfWeek.sorted().joinToString("، ") { d -> dayNames.firstOrNull { it.first == d }?.second ?: "$d" }, 12f))
                if (s.enabled) {
                    val next = ScheduleManager.nextTrigger(this, s)
                    c.addView(UiKit.body(this, "اجرای بعدی: " + SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(next)), 12f))
                }
                c.addView(UiKit.outlinedButton(this, "حذف زمان‌بندی") {
                    ScheduleManager.cancel(this, s)
                    PrefsManager.setSchedules(this, PrefsManager.getSchedules(this).filterNot { it.id == s.id })
                    render()
                })
            })
        }
        column.addView(UiKit.primaryButton(this, "＋ زمان‌بندی جدید") { addSchedule(profiles) })
        column.addView(UiKit.body(this, "اگر وقت اجرا سشن دیگری فعال باشد، این زمان‌بندی رد و در گزارش‌ها ثبت می‌شود. اجرای دقیق به مجوز آلارم دقیق و سیاست باتری سازنده بستگی دارد.", 12f))
    }

    private fun addSchedule(profiles: List<com.example.blocker.core.FocusProfile>) {
        if (profiles.isEmpty()) return
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@SchedulesActivity, android.R.layout.simple_spinner_dropdown_item, profiles.map { it.name })
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
            addView(spinner)
        }
        val dayChecks = dayNames.map { (num, name) ->
            CheckBox(this).apply { text = name; tag = num }
        }
        dayChecks.forEach { box.addView(it) }
        AlertDialog.Builder(this)
            .setTitle("زمان‌بندی جدید — اول پروفایل و روزها، بعد ساعت")
            .setView(box)
            .setPositiveButton("انتخاب ساعت") { _, _ ->
                val profile = profiles[spinner.selectedItemPosition]
                TimePickerDialog(this, { _, hour, minute ->
                    val days = dayChecks.filter { it.isChecked }.map { it.tag as Int }.toSet()
                    val schedule = SessionSchedule(
                        id = PrefsManager.newId(), profileId = profile.id,
                        hour = hour, minute = minute, daysOfWeek = days, enabled = true
                    )
                    PrefsManager.setSchedules(this, PrefsManager.getSchedules(this) + schedule)
                    ScheduleManager.scheduleOne(this, schedule)
                    Toast.makeText(this, "زمان‌بندی ثبت شد", Toast.LENGTH_SHORT).show()
                    render()
                }, 20, 0, true).show()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }
}
