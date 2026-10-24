package com.example.blocker.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.blocker.PrefsManager
import com.example.blocker.core.CoolingOff
import com.example.blocker.core.DurationRange
import com.example.blocker.core.FocusProfile
import com.example.blocker.core.SessionMode
import com.example.blocker.core.SessionRecord
import com.example.blocker.core.SessionStatus
import com.example.blocker.health.ProtectionHealth
import com.example.blocker.service.SessionForegroundService
import com.example.blocker.session.SessionManager

/** صفحه‌ی ساخت و مدیریت سشن؛ Quick Start داشبورد هم به همین‌جا می‌آید. */
class SessionActivity : AppCompatActivity() {

    private lateinit var column: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private var ticking = false
    private var remainingView: TextView? = null
    private var exitInfoView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "سشن فوکوس"
        val (scroll, col) = UiKit.screen(this)
        column = col
        setContentView(scroll)
        render()
    }

    override fun onResume() { super.onResume(); startTicker() }
    override fun onPause() { super.onPause(); ticking = false }

    private fun startTicker() {
        if (ticking) return
        ticking = true
        handler.post(object : Runnable {
            override fun run() {
                if (!ticking) return
                val s = SessionManager.activeSession(this@SessionActivity)
                if (s == null) { render(); return }
                remainingView?.text = UiKit.formatDuration(SessionManager.remainingMillis(this@SessionActivity, s))
                if (s.status == SessionStatus.EXIT_PENDING) {
                    val left = s.exitDeadlineWallMillis - System.currentTimeMillis()
                    exitInfoView?.text = if (left > 0)
                        "خروج در انتظار است؛ محافظت همچنان فعال است. زمان باقی‌مانده تا امکان خروج: " + UiKit.formatDuration(left)
                    else "مهلت خروج رسیده است؛ در حال نهایی‌سازی…"
                    if (left <= 0) { SessionManager.current(this@SessionActivity); render(); return }
                }
                handler.postDelayed(this, 1000L)
            }
        })
    }

    private fun render() {
        column.removeAllViews()
        remainingView = null; exitInfoView = null
        val active = SessionManager.activeSession(this)
        if (active != null) renderActive(active) else renderCreate()
    }

    // ---------- سشن فعال ----------
    private fun renderActive(s: SessionRecord) {
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, if (s.mode == SessionMode.STRICT) "سشن Strict فعال است" else "سشن فعال است", 20f))
            val rem = TextView(this).apply {
                text = UiKit.formatDuration(SessionManager.remainingMillis(this@SessionActivity, s))
                textSize = 40f
                gravity = android.view.Gravity.CENTER
            }
            remainingView = rem
            c.addView(rem)
            c.addView(UiKit.body(this, "پروفایل: ${s.profileName}"))
            c.addView(UiKit.body(this, "دیلی بلاک: ${s.delaySeconds} ثانیه"))
            val apps = SessionManager.snapshotApps(s.configSnapshotJson).size
            val sites = SessionManager.snapshotWebsites(s.configSnapshotJson).size
            val rules = SessionManager.snapshotRules(s.configSnapshotJson).size
            c.addView(UiKit.body(this, "کانفیگ قفل‌شده‌ی این سشن: $apps برنامه، $sites سایت، $rules قانون"))
            val level = ProtectionHealth.protectionLevel(this)
            c.addView(UiKit.body(this, "سطح محافظت واقعی: ${level.labelFa}"))
            if (!ProtectionHealth.isAccessibilityEnabled(this)) {
                c.addView(UiKit.body(this, "⚠ سرویس دسترسی خاموش است؛ اجرا در حال حاضر تضمین‌شده نیست."))
            }
        })

        if (s.mode == SessionMode.NORMAL) {
            column.addView(UiKit.primaryButton(this, "پایان دادن سشن") {
                SessionForegroundService.stop(this)
                SessionManager.endNormalSessionNow(this)
                Toast.makeText(this, "سشن تمام شد", Toast.LENGTH_SHORT).show()
                render()
            })
        } else {
            column.addView(UiKit.card(this) { c ->
                c.addView(UiKit.title(this, "خروج از Strict", 18f))
                if (s.status == SessionStatus.ACTIVE) {
                    c.addView(UiKit.body(this, "خروج فوری وجود ندارد. با ثبت درخواست خروج، یک دوره‌ی آرام‌سازی ${s.coolingOff.labelFa} شروع می‌شود و در تمام این مدت محافظت کامل برقرار می‌ماند. هر لحظه می‌توانید درخواست را لغو کنید."))
                    c.addView(UiKit.primaryButton(this, "ثبت درخواست خروج") {
                        AlertDialog.Builder(this)
                            .setTitle("ثبت درخواست خروج از Strict؟")
                            .setMessage("محافظت تا ${s.coolingOff.labelFa} دیگر ادامه دارد و بعد از آن با باز کردن همین صفحه خروج نهایی می‌شود. این کار قابل لغو است.")
                            .setPositiveButton("ثبت درخواست") { _, _ ->
                                SessionManager.requestStrictExit(this)
                                render()
                            }
                            .setNegativeButton("انصراف", null)
                            .show()
                    })
                } else {
                    val info = UiKit.body(this, "")
                    exitInfoView = info
                    c.addView(info)
                    c.addView(UiKit.tonalButton(this, "لغو درخواست خروج") {
                        SessionManager.cancelExitRequest(this)
                        render()
                    })
                }
                c.addView(UiKit.body(this, "اگر اپ خراب شده و به دستگاهتان دسترسی ندارید، از «ریکاوری اضطراری» در تنظیمات استفاده کنید؛ آن مسیر جدا از این خروج است و برای خرابی نرم‌افزار ساخته شده."))
            })
        }
        column.addView(UiKit.outlinedButton(this, "بازگشت به داشبورد") { finish() })
    }

    // ---------- ساخت سشن ----------
    private fun renderCreate() {
        val profiles = PrefsManager.getProfiles(this)
        column.addView(UiKit.title(this, "شروع سشن فوکوس"))

        var selectedProfile: FocusProfile? = null
        var durationMillis = 25 * 60_000L
        var mode = SessionMode.NORMAL
        var cooling = PrefsManager.getDefaultCoolingOff(this)

        val profileSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@SessionActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("دستی (بدون پروفایل)") + profiles.map { it.name })
        }
        // فیلد مدت دلخواه (قبل از چیپ‌ها تعریف می‌شود تا شنونده‌هایشان به آن برسند).
        val customValue = UiKit.textField(this, "مدت دلخواه").apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val unitSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@SessionActivity, android.R.layout.simple_spinner_dropdown_item,
                DurationRange.Unit.values().map { it.labelFa })
            setSelection(DurationRange.Unit.values().indexOf(DurationRange.Unit.MINUTE))
        }
        val customRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(customValue); addView(unitSpinner)
        }

        // همگام‌سازی چیپ‌ها: چون شنونده‌های چیپ قبل از updateSummary ساخته می‌شوند،
        // بدنه‌ی این لامبداها بعد از تعریف updateSummary پر می‌شود.
        var refreshDurationChips: () -> Unit = {}
        var refreshModeChips: () -> Unit = {}
        var refreshCoolingChips: () -> Unit = {}

        val summary = UiKit.body(this, "")

        fun currentDurationMillis(): Long {
            val v = customValue.text.toString().toLongOrNull()
            if (v != null) {
                val unit = DurationRange.Unit.values().getOrNull(unitSpinner.selectedItemPosition)
                    ?: DurationRange.Unit.MINUTE
                return v * unit.millis
            }
            return durationMillis
        }

        fun splitDuration(millis: Long): Pair<Long, DurationRange.Unit> = when {
            millis % 86_400_000L == 0L -> millis / 86_400_000L to DurationRange.Unit.DAY
            millis % 3_600_000L == 0L -> millis / 3_600_000L to DurationRange.Unit.HOUR
            millis % 60_000L == 0L -> millis / 60_000L to DurationRange.Unit.MINUTE
            else -> millis / 1_000L to DurationRange.Unit.SECOND
        }

        fun updateSummary() {
            val d = currentDurationMillis()
            val m = mode
            val co = cooling
            val delay = selectedProfile?.delaySeconds ?: PrefsManager.getDelaySeconds(this)
            val validNote = if (DurationRange.isValid(d)) "" else " ⚠ مدت باید بین ۳۰ ثانیه تا ۹۰ روز باشد."
            summary.text = "خلاصه: ${UiKit.formatDuration(d)}، حالت ${if (m == SessionMode.STRICT) "Strict" else "عادی"}، دیلی بلاک $delay ثانیه" +
                (if (m == SessionMode.STRICT) "، آرام‌سازی خروج ${co.labelFa}، قوانین و تنظیمات در طول سشن قفل می‌شوند." else ".") + validNote
        }

        // پیش‌فرض‌های مدت: از ۳۰ ثانیه تا ۹۰ روز (بازه‌ی مجاز DurationRange)، در دو ردیف چیپ.
        val presets = listOf(
            "۳۰ ثانیه" to 30_000L, "۵ دقیقه" to 5 * 60_000L,
            "۲۵ دقیقه" to 25 * 60_000L, "۱ ساعت" to 3_600_000L,
            "۱ روز" to 86_400_000L, "۷ روز" to 7 * 86_400_000L,
            "۳۰ روز" to 30 * 86_400_000L, "۹۰ روز" to 90 * 86_400_000L
        )
        val durationChips = mutableListOf<TextView>()
        fun chipRow(items: List<Pair<String, Long>>): LinearLayout {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            items.forEach { (label, millis) ->
                val chip = UiKit.chipView(this@SessionActivity, label, millis == durationMillis)
                chip.tag = millis
                chip.setOnClickListener {
                    durationMillis = millis
                    refreshDurationChips()
                    customValue.setText("")
                    updateSummary()
                }
                durationChips.add(chip)
                row.addView(chip)
            }
            return row
        }
        val durationRow1 = chipRow(presets.subList(0, 4))
        val durationRow2 = chipRow(presets.subList(4, 8))

        // حالت: دو چیپ عادی / سخت‌گیر.
        val modeChipNormal = UiKit.chipView(this, "عادی (Normal)", true)
        val modeChipStrict = UiKit.chipView(this, "سخت‌گیر (Strict)", false)
        modeChipNormal.setOnClickListener { mode = SessionMode.NORMAL; refreshModeChips(); updateSummary() }
        modeChipStrict.setOnClickListener { mode = SessionMode.STRICT; refreshModeChips(); updateSummary() }
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(modeChipNormal); addView(modeChipStrict)
        }

        // آرام‌سازی خروج: سه چیپ با برچسب مدت (فقط در Strict اثر دارد).
        val coolingChips = CoolingOff.values().map { co ->
            UiKit.chipView(this, co.labelFa, co == cooling).apply {
                tag = co
                setOnClickListener { cooling = co; refreshCoolingChips(); updateSummary() }
            }
        }
        val coolingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            coolingChips.forEach { addView(it) }
        }

        refreshDurationChips = { durationChips.forEach { UiKit.setChipSelected(this, it, (it.tag as Long) == durationMillis) } }
        refreshModeChips = {
            UiKit.setChipSelected(this, modeChipNormal, mode == SessionMode.NORMAL)
            UiKit.setChipSelected(this, modeChipStrict, mode == SessionMode.STRICT)
        }
        refreshCoolingChips = { coolingChips.forEach { UiKit.setChipSelected(this, it, (it.tag as CoolingOff) == cooling) } }

        profileSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                selectedProfile = if (pos == 0) null else profiles[pos - 1]
                selectedProfile?.let { p ->
                    durationMillis = p.durationMillis
                    mode = p.mode
                    cooling = p.coolingOff
                    refreshDurationChips()
                    refreshModeChips()
                    refreshCoolingChips()
                    val matched = presets.any { it.second == durationMillis }
                    if (!matched) {
                        val (v, u) = splitDuration(durationMillis)
                        customValue.setText(v.toString())
                        unitSpinner.setSelection(DurationRange.Unit.values().indexOf(u))
                    } else {
                        customValue.setText("")
                    }
                }
                updateSummary()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        customValue.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!s.isNullOrBlank()) durationChips.forEach { UiKit.setChipSelected(this@SessionActivity, it, false) }
                updateSummary()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        unitSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (customValue.text.isNotBlank()) durationChips.forEach { UiKit.setChipSelected(this@SessionActivity, it, false) }
                updateSummary()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.body(this, "پروفایل"))
            c.addView(profileSpinner)
            c.addView(UiKit.body(this, "مدت سشن (از ۳۰ ثانیه تا ۹۰ روز)"))
            c.addView(durationRow1)
            c.addView(durationRow2)
            c.addView(customRow)
            c.addView(UiKit.body(this, "حالت"))
            c.addView(modeRow)
            c.addView(UiKit.body(this, "آرام‌سازی خروج (فقط در حالت سخت‌گیر)"))
            c.addView(coolingRow)
            c.addView(summary)
        })
        updateSummary()

        column.addView(UiKit.primaryButton(this, "شروع سشن") {
            val d = currentDurationMillis()
            if (!DurationRange.isValid(d)) { Toast.makeText(this, "مدت سشن باید بین ۳۰ ثانیه تا ۹۰ روز باشد", Toast.LENGTH_SHORT).show(); return@primaryButton }
            val delay = selectedProfile?.delaySeconds ?: PrefsManager.getDelaySeconds(this)
            if (!ProtectionHealth.isAccessibilityEnabled(this)) {
                Toast.makeText(this, "اول سرویس دسترسی را از صفحه‌ی تشخیص فعال کنید؛ بدون آن بلاک اجرا نمی‌شود.", Toast.LENGTH_LONG).show()
            }
            if (mode == SessionMode.STRICT) {
                val level = ProtectionHealth.protectionLevel(this)
                AlertDialog.Builder(this)
                    .setTitle("فعال‌سازی Strict — تأیید نهایی")
                    .setMessage(
                        "مدت: ${UiKit.formatDuration(d)}\n" +
                        "سطح محافظت واقعی فعلی: ${level.labelFa}\n" +
                        "در طول سشن: ویرایش قوانین/اپ‌های هدف و تغییر تنظیمات قفل است و کانفیگ همین لحظه snapshot می‌شود.\n" +
                        "خروج عادی: فقط با درخواست خروج و گذشت ${cooling.labelFa} آرام‌سازی.\n" +
                        "اگر سرویس دسترسی خاموش شود یا اپ Force Stop شود، محافظت متوقف می‌شود و اپ معمولی جلوی آن را نمی‌گیرد؛ بعد از باز شدن دوباره، وضعیت صادقانه گزارش می‌شود.\n" +
                        "خروج اضطراری جدا از این مسیر در صفحه‌ی ریکاوری وجود دارد.\n" +
                        "Device Owner: ${if (com.example.blocker.admin.DeviceOwnerHelper.isDeviceOwner(this)) "فعال است" else "فعال نیست (حذف/Force Stop قابل جلوگیری نیست)"}"
                    )
                    .setPositiveButton("فعال کن") { _, _ -> doStart(mode, d, selectedProfile, delay, cooling) }
                    .setNegativeButton("انصراف", null)
                    .show()
            } else {
                doStart(mode, d, selectedProfile, delay, cooling)
            }
        })
        column.addView(UiKit.outlinedButton(this, "بازگشت") { finish() })
    }

    private fun doStart(mode: SessionMode, durationMillis: Long, profile: FocusProfile?, delay: Int, cooling: CoolingOff) {
        SessionManager.startSession(this, mode, durationMillis, profile, delay, cooling)
        SessionForegroundService.start(this)
        Toast.makeText(this, "سشن شروع شد", Toast.LENGTH_SHORT).show()
        render()
    }
}
