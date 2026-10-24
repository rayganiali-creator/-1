package com.example.blocker.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.blocker.PrefsManager
import com.example.blocker.R
import com.example.blocker.core.CoolingOff
import com.example.blocker.core.DurationRange
import com.example.blocker.core.FocusProfile
import com.example.blocker.core.SessionMode
import com.example.blocker.core.SessionRecord
import com.example.blocker.core.SessionStatus
import com.example.blocker.health.ProtectionHealth
import com.example.blocker.service.SessionForegroundService
import com.example.blocker.session.SessionManager
import com.example.blocker.widget.SessionWidgetProvider
import java.text.SimpleDateFormat
import java.util.Locale
import com.example.blocker.widget.SessionWidgetUpdater

/** صفحه‌ی ساخت و مدیریت سشن؛ Quick Start داشبورد هم به همین‌جا می‌آید. */
class SessionActivity : AppCompatActivity() {

    private lateinit var column: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private var ticking = false
    private var liveRing: UiKit.LiveRingView? = null
    private var exitInfoView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "سشن فوکوس"
        val (scroll, col) = UiKit.screen(this)
        column = col
        setContentView(scroll)
        render()
    }

    override fun onResume() { super.onResume(); startTicker(); SessionWidgetUpdater.updateAll(this) }
    override fun onPause() {
        super.onPause()
        ticking = false
        tickerRunnable?.let { handler.removeCallbacks(it) }
        tickerRunnable = null
    }

    private var tickerRunnable: Runnable? = null

    private fun startTicker() {
        if (ticking) return
        ticking = true
        val r = object : Runnable {
            override fun run() {
                if (!ticking) return
                val s = SessionManager.activeSession(this@SessionActivity)
                if (s == null) { render(); return }
                val rem = SessionManager.remainingMillis(this@SessionActivity, s)
                val frac = if (s.durationMillis > 0) rem.toFloat() / s.durationMillis.toFloat() else 0f
                liveRing?.update(frac, faDigits(UiKit.formatDuration(rem)))
                if (s.status == SessionStatus.EXIT_PENDING) {
                    val left = s.exitDeadlineWallMillis - System.currentTimeMillis()
                    exitInfoView?.text = if (left > 0)
                        "خروج در انتظار است؛ محافظت همچنان فعال است. زمان باقی‌مانده تا امکان خروج: " + UiKit.formatDuration(left)
                    else "مهلت خروج رسیده است؛ در حال نهایی‌سازی…"
                    if (left <= 0) { SessionManager.current(this@SessionActivity); render(); return }
                }
                handler.postDelayed(this, 1000L)
            }
        }
        tickerRunnable = r
        handler.post(r)
    }

    private fun timeFa(wallMillis: Long): String =
        SimpleDateFormat("HH:mm", Locale.US).format(java.util.Date(wallMillis))

    private fun faDigits(s: String): String {
        val fa = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        return s.map { if (it in '0'..'9') fa[it - '0'] else it }.joinToString("")
    }

    private fun render() {
        column.removeAllViews()
        liveRing = null; exitInfoView = null
        val active = SessionManager.activeSession(this)
        if (active != null) renderActive(active) else renderCreate()
    }

    // ---------- سشن فعال (موکاپ: حلقه‌ی بزرگ + قرص Strict + کارت شروع/پایان) ----------
    private fun renderActive(s: SessionRecord) {
        column.addView(UiKit.title(this, "سشن فعال", 22f))
        val remaining = SessionManager.remainingMillis(this, s)
        val fraction = if (s.durationMillis > 0) remaining.toFloat() / s.durationMillis.toFloat() else 0f
        val ring = UiKit.sessionRing(this, faDigits(UiKit.formatDuration(remaining)), fraction)
        liveRing = ring
        column.addView(ring)
        if (s.mode == SessionMode.STRICT) {
            // قرص Strict موکاپ: خط نعنایی + نقطه + متن
            val palette = ThemePalette.resolve(this)
            val pill = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = UiKit.dp(this@SessionActivity, 24).toFloat()
                    setColor(palette.tile)
                    setStroke(UiKit.dp(this@SessionActivity, 1), palette.accent)
                }
                val p = UiKit.dp(this@SessionActivity, 12)
                setPadding(p, UiKit.dp(this@SessionActivity, 6), p, UiKit.dp(this@SessionActivity, 6))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                    bottomMargin = UiKit.dp(this@SessionActivity, 12)
                }
            }
            pill.addView(View(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(palette.accent)
                }
                layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@SessionActivity, 8), UiKit.dp(this@SessionActivity, 8)).apply {
                    marginEnd = UiKit.dp(this@SessionActivity, 8)
                }
            })
            pill.addView(TextView(this).apply {
                text = "Strict"
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.accent)
            })
            column.addView(pill)
        }
        column.addView(UiKit.card(this) { c ->
            c.addView(timeRow("شروع سشن", faDigits(timeFa(s.startWallMillis))))
            c.addView(UiKit.divider(this))
            c.addView(timeRow("پایان سشن", faDigits(timeFa(s.endWallMillis))))
        })

        if (s.status == SessionStatus.ACTIVE) {
            column.addView(UiKit.outlinedButton(this, "تمدید") {
                SessionManager.extendSession(this, 15 * 60_000L)
                Toast.makeText(this, "سشن ۱۵ دقیقه تمدید شد", Toast.LENGTH_SHORT).show()
                render()
            })
        }
        if (s.mode == SessionMode.NORMAL) {
            column.addView(UiKit.primaryButton(this, "پایان سشن") {
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

    /** ردیف ساعت کارت شروع/پایان: آیکون ساعت + برچسب + ساعت پررنگ. */
    private fun timeRow(label: String, time: String): LinearLayout {
        val palette = ThemePalette.resolve(this)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(ImageView(this@SessionActivity).apply {
                setImageResource(R.drawable.ic_tile_clock)
                imageTintList = android.content.res.ColorStateList.valueOf(palette.accent)
                layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@SessionActivity, 20), UiKit.dp(this@SessionActivity, 20))
            })
            addView(TextView(this@SessionActivity).apply {
                text = label
                textSize = 15f
                setTextColor(palette.onSurface)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = UiKit.dp(this@SessionActivity, 8)
                }
            })
            addView(View(this@SessionActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            })
            addView(TextView(this@SessionActivity).apply {
                text = time
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.onSurface)
            })
        }
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
                        (if (PrefsManager.getSelfProtectionEnabled(this)) "" else "خودمحافظتی خاموش است، ولی در این سشن Strict به‌صورت خودکار فعال می‌شود؛ تا پایان تایمر نه می‌توانی اپ را حذف کنی نه متوقف.\n") +
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
        SessionWidgetUpdater.updateAll(this)
        maybePromptWidget()
    }

    /**
     * پرسش گذاشتن ویجت — اول از کاربر می‌پرسد، خودش چیزی اضافه نمی‌کند:
     * فقط موقع شروع سشن، فقط اگر هنوز ویجتی نیست و کاربر «دیگر نپرس» نزده باشد.
     * «بذار» دیالوگ سیستمی pin را باز می‌کند؛ «بعداً» دفعه‌ی بعد دوباره می‌پرسد.
     */
    private fun maybePromptWidget() {
        if (PrefsManager.isWidgetPromptNever(this) || SessionWidgetUpdater.hasWidget(this)) return
        AlertDialog.Builder(this)
            .setTitle("ویجت زمان باقی‌مانده")
            .setMessage("می‌خوای ویجت «سد» را روی صفحه‌ی اصلی بگذاری تا زمان باقی‌مانده‌ی سشن را همان‌جا ببینی؟")
            .setPositiveButton("بذار") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    AppWidgetManager.getInstance(this).requestPinAppWidget(
                        ComponentName(this, SessionWidgetProvider::class.java), null, null)
                } else {
                    Toast.makeText(this, "از لیست ویجت‌های صفحه‌ی اصلی، ویجت «سد» را اضافه کن.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("بعداً", null)
            .setNeutralButton("دیگر نپرس") { _, _ -> PrefsManager.setWidgetPromptNever(this, true) }
            .show()
    }
}
