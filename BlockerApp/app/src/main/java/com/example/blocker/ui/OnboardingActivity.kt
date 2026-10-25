package com.example.blocker.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.example.blocker.PrefsManager
import com.example.blocker.core.OnboardingState
import com.example.blocker.core.OnboardingState.StepId
import com.example.blocker.core.OnboardingState.StepStatus
import com.example.blocker.health.ProtectionHealth

/**
 * ویزارد راه‌اندازی «سد» — متمرکز، قدم‌به‌قدم، و وابسته به وضعیت واقعی.
 *
 * تفاوت با نسخه‌ی قبلی (صفحه‌ی بلند با همه‌ی قدم‌ها):
 * - در هر لحظه فقط «یک» قدمِ جاری نشان داده می‌شود: شماره‌ی قدم، یک اقدام به
 *   زبان ساده، «چه چیزی باید ببینی»، دکمه‌ی باز کردن تنظیمات، و لینک عیب‌یابی.
 * - قدم «تنظیمات محدودشده» فقط وقتی ظاهر می‌شود که سیگنال‌ها بگویند لازم است
 *   (اندروید ۱۳+ و نصب غیرفروشگاهی)؛ روی گوشی‌های فروشگاهی اصلاً نشان داده نمی‌شود.
 * - پیشرفت با راستی‌آزمایی زنده محاسبه می‌شود: برگشت از تنظیمات، ری‌استارت و
 *   لغو بعدیِ دسترسی، قدمِ کامل را پاک نمی‌کند — فقط همان قدم دوباره
 *   «اقدام لازم» می‌شود (جریان تعمیر متمرکز).
 * - هیچ قدمی با زدن دکمه کامل نمی‌شود؛ فقط وقتی وضعیت واقعاً فعال باشد.
 * - قدم‌های ضروری قابل رد کردن نیستند؛ قدم‌های اختیاری دکمه‌ی «رد کردن» دارند
 *   و انتخابِ رد کردن ذخیره می‌شود (ولی با زدن روی آن قدم در فهرست، دوباره باز می‌شود).
 * - تصاویر، «شماتیک» و برچسب‌دارند — اسکرین‌شات جعلی از صفحه‌ی اندروید نشان داده نمی‌شود.
 *
 * حالت تعمیر (EXTRA_REPAIR): فقط قدم‌های ناقص نشان داده می‌شوند؛ برای وقتی که
 * دسترسی بعداً لغو شده و کاربر نمی‌خواهد کل آموزش را دوباره ببیند.
 */
class OnboardingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REPAIR = "extra_repair"
    }

    private lateinit var column: LinearLayout
    private var repairMode: Boolean = false
    /** قدمی که کاربر با «بازگشت» یا زدن روی فهرست انتخاب کرده؛ null یعنی قدم جاری خودکار. */
    private var focusId: StepId? = null
    private var showHelp: Boolean = false
    /** پس از بازگشت از اطلاعات برنامه، کاربر را مستقیم به قدم دسترسی هدایت می‌کنیم. */
    private var returningFromRestrictedSettings: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repairMode = intent.getBooleanExtra(EXTRA_REPAIR, false)
        title = if (repairMode) "تعمیر دسترسی‌ها" else "راه‌اندازی سد"
        val (scroll, col) = UiKit.screen(this)
        column = col
        setContentView(scroll)
        render()
    }

    /** بازگشت از تنظیمات = بازخوانی واقعی وضعیت؛ هرگز «خودکار جلو رفتن» نیست. */
    override fun onResume() {
        super.onResume()
        if (returningFromRestrictedSettings) {
            returningFromRestrictedSettings = false
            focusId = StepId.ACCESSIBILITY
            showHelp = false
        }
        render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }

    private fun deviceSignals(): OnboardingState.DeviceSignals {
        val installer = try {
            if (Build.VERSION.SDK_INT >= 30) {
                packageManager.getInstallSourceInfo(packageName).installingPackageName
            } else null
        } catch (e: Exception) {
            null
        }
        return OnboardingState.DeviceSignals(
            apiLevel = Build.VERSION.SDK_INT,
            installerPackage = installer,
            manufacturer = Build.MANUFACTURER ?: ""
        )
    }

    /**
     * هر وضعیت جداگانه خوانده می‌شود؛ خطا در یک خواندن، بقیه را خراب نمی‌کند
     * و صفحه را خالی نمی‌گذارد. پیش‌فرض خطا «فعال نیست» است (جهت امن: قدم
     * نشان داده می‌شود، نه اینکه پنهان شود).
     */
    private fun verifiedStates(): OnboardingState.VerifiedStates {
        val notifications = try {
            NotificationManagerCompat.from(this).areNotificationsEnabled()
        } catch (e: Exception) { false }
        val battery = try {
            (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(packageName)
        } catch (e: Exception) { false }
        val exactAlarms = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
            } else true
        } catch (e: Exception) { false }
        val hasConfig = try {
            PrefsManager.getRules(this).isNotEmpty() ||
                PrefsManager.getBlockedApps(this).isNotEmpty() ||
                PrefsManager.getBlockedWebsites(this).isNotEmpty()
        } catch (e: Exception) { false }
        val a11y = try {
            ProtectionHealth.isAccessibilityEnabled(this)
        } catch (e: Exception) { false }
        return OnboardingState.VerifiedStates(
            a11y = a11y,
            notifications = notifications,
            battery = battery,
            exactAlarms = exactAlarms,
            hasConfig = hasConfig,
            apiLevel = Build.VERSION.SDK_INT
        )
    }

    private fun whyText(stepId: StepId): String = when (stepId) {
        StepId.RESTRICTED_SETTINGS -> "اندروید ۱۳ به بعد، برای اپ‌هایی که از فروشگاه نصب نشده‌اند، فعال‌سازی سرویس دسترسی را قفل می‌کند. این قفل خود اندروید است و ربطی به سد ندارد؛ با این قدم بازش می‌کنیم."
        StepId.ACCESSIBILITY -> "بدون این، سد هیچ صفحه‌ای را نمی‌بیند و هیچ بلاکی اجرا نمی‌شود. متن صفحات روی گوشی می‌ماند؛ جایی ذخیره یا ارسال نمی‌شود."
        StepId.NOTIFICATIONS -> "برای نمایش زمان باقی‌مانده‌ی سشن و هشدار خاموش شدن سرویس وسط سشن."
        StepId.BATTERY -> "تا اندروید وسط سشن سرویس را نخواباند. معافیت ریسک را کم می‌کند، حذفش نمی‌کند؛ و فقط با تأیید خودت در دیالوگ سیستم انجام می‌شود."
        StepId.EXACT_ALARMS -> "تا زمان‌بندی‌های خودکار دقیقاً سر ساعت شروع شوند، نه با تأخیر."
        StepId.CONFIG -> "کلمات، دامنه‌ها و برنامه‌ها را انتخاب کن؛ بعداً هم از داشبورد قابل تغییر است — مگر در سشن Strict."
    }

    private fun seeText(stepId: StepId, samsung: Boolean): String = when (stepId) {
        // بدون زنجیره‌ی «→»: ترکیب فارسی + انگلیسی + پیکان در متن راست‌به‌چپ،
        // ترتیب نمایش را به‌هم می‌ریزد. قدم‌ها شماره‌دار و هر کدام در یک خط‌اند.
        StepId.RESTRICTED_SETTINGS -> "۱) صفحه‌ی اطلاعات برنامه‌ی سد باز می‌شود.\n" +
            "۲) بالای صفحه، سه‌نقطه را بزن.\n" +
            "۳) گزینه‌ی Allow restricted settings را انتخاب کن.\n" +
            "۴) Allow را بزن."
        StepId.ACCESSIBILITY -> if (samsung) {
            "۱) وارد تنظیمات شو.\n" +
            "۲) Accessibility را باز کن.\n" +
            "۳) Installed apps را باز کن.\n" +
            "۴) سد را انتخاب کن و دکمه را روشن کن.\n" +
            "۵) اگر هشدار آمد، Allow را بزن."
        } else {
            "۱) وارد تنظیمات شو.\n" +
            "۲) Accessibility را باز کن.\n" +
            "۳) Downloaded apps را باز کن.\n" +
            "۴) سد را انتخاب کن و دکمه را روشن کن.\n" +
            "۵) اگر هشدار آمد، Allow را بزن."
        }
        StepId.NOTIFICATIONS -> "دیالوگ سیستم می‌پرسد نوتیفیکیشن مجاز باشد؛ Allow را بزن."
        StepId.BATTERY -> "دیالوگ سیستم می‌پرسد بهینه‌سازی باتری نادیده گرفته شود؛ Allow را بزن."
        StepId.EXACT_ALARMS -> "صفحه‌ی آلارم‌ها باز می‌شود؛ سد را مجاز کن."
        StepId.CONFIG -> "صفحه‌ی قوانین باز می‌شود؛ یک قانون اضافه کن و با دکمه‌ی برگشت به همین‌جا برگرد."
    }

    private fun actionLabel(stepId: StepId): String = when (stepId) {
        StepId.RESTRICTED_SETTINGS -> "باز کردن اطلاعات برنامه"
        StepId.ACCESSIBILITY -> "باز کردن تنظیمات دسترسی"
        StepId.NOTIFICATIONS -> "درخواست مجوز نوتیفیکیشن"
        StepId.BATTERY -> "درخواست معافیت باتری"
        StepId.EXACT_ALARMS -> "باز کردن تنظیمات آلارم"
        StepId.CONFIG -> "مدیریت قوانین"
    }

    private fun runAction(stepId: StepId) {
        try {
            when (stepId) {
                StepId.RESTRICTED_SETTINGS -> {
                    returningFromRestrictedSettings = true
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                    )
                }
                StepId.ACCESSIBILITY -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                StepId.NOTIFICATIONS -> {
                    if (Build.VERSION.SDK_INT >= 33) {
                        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
                    }
                }
                StepId.BATTERY -> try {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                    )
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
                StepId.EXACT_ALARMS -> try {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                    )
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
                StepId.CONFIG -> startActivity(Intent(this, RulesActivity::class.java))
            }
        } catch (e: Exception) {
            Toast.makeText(this, "باز کردن تنظیمات ممکن نشد؛ از تنظیمات گوشی، مسیر نوشته‌شده را دستی دنبال کن.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * رندر هرگز نباید صفحه‌ی خالی تحویل بدهد: اگر خواندن وضعیت (مثلاً داده‌ی
     * خراب در حافظه) وسط رندر استثنا بدهد، به‌جای صفحه‌ی سفید، کارت خطا با
     * مسیر جایگزینِ مستقیم به تنظیمات نشان داده می‌شود.
     */
    private fun render() {
        try {
            renderInner()
        } catch (e: Exception) {
            column.removeAllViews()
            column.addView(UiKit.title(this, "راه‌اندازی سد — قدم‌به‌قدم", 22f))
            column.addView(UiKit.body(this,
                "نمایش راهنما به مشکل خورد (${e.javaClass.simpleName}: ${e.message ?: "خطای نامشخص"}). می‌توانی مستقیم " +
                "به تنظیمات دسترسی بروی و سد را فعال کنی؛ بعد با «بررسی دوباره» همین‌جا برگرد."))
            column.addView(UiKit.primaryButton(this, "باز کردن تنظیمات دسترسی") {
                runAction(StepId.ACCESSIBILITY)
            })
            column.addView(UiKit.outlinedButton(this, "تلاش دوباره") { render() })
        } finally {
            UiKit.versionLine(this)?.let { column.addView(it) }
        }
    }

    /**
     * بازطراحی: تمام مراحل و مجوزها در یک صفحه نمایش داده می‌شوند؛
     * حتی مواردی که در نسخه‌ی اندروید فعلی لازم نیستند، مخفی نمی‌شوند.
     */
    private fun renderInner() {
        column.removeAllViews()
        val signals = deviceSignals()
        val steps = OnboardingState.buildSteps(signals, verifiedStates())
        val samsung = OnboardingState.isSamsung(signals)

        column.addView(UiKit.title(this, if (repairMode) "بررسی و تعمیر دسترسی‌ها" else "راه‌اندازی سد", 24f))
        column.addView(UiKit.body(this,
            "همه‌ی مراحل اینجا دیده می‌شوند. وضعیت هر مورد از تنظیمات واقعی گوشی خوانده می‌شود؛ برای هر مورد دکمه‌ی مستقل خودش را بزن."))
        val doneCount = steps.count { it.status == StepStatus.DONE }
        val applicableCount = steps.count { it.status != StepStatus.NOT_APPLICABLE }
        column.addView(UiKit.body(this, "وضعیت کلی: $doneCount از $applicableCount مورد فعال یا تکمیل شده", 14f))

        steps.forEachIndexed { index, step ->
            val statusText = when (step.status) {
                StepStatus.DONE -> "فعال است ✓"
                StepStatus.ACTION_REQUIRED -> "نیاز به اقدام"
                StepStatus.NOT_APPLICABLE -> "در نسخه‌ی اندروید شما لازم نیست"
            }
            val statusColor = when (step.status) {
                StepStatus.DONE -> 0xFF7CFFD9.toInt()
                StepStatus.ACTION_REQUIRED -> 0xFFFFC66D.toInt()
                StepStatus.NOT_APPLICABLE -> ThemePalette.resolve(this).onSurfaceVariant
            }
            column.addView(UiKit.card(this) { c ->
                c.addView(UiKit.title(this, "${index + 1}. ${step.titleFa}", 18f))
                c.addView(UiKit.body(this, statusText, 14f).apply { setTextColor(statusColor) })
                c.addView(UiKit.body(this, whyText(step.id), 14f))
                if (step.status != StepStatus.NOT_APPLICABLE) {
                    c.addView(UiKit.body(this, "مراحل انجام:", 13f).apply {
                        setTextColor(ThemePalette.resolve(this@OnboardingActivity).onSurface)
                    })
                    c.addView(UiKit.body(this, seeText(step.id, samsung), 13f))
                } else {
                    val details = when (step.id) {
                        StepId.RESTRICTED_SETTINGS -> "این قفل معمولاً فقط در اندروید ۱۳ به بالا و نصب خارج از فروشگاه مطرح است. اگر دکمه‌ی سرویس دسترسی خاکستری است، اطلاعات برنامه را باز کن و منوی سه‌نقطه را بررسی کن."
                        StepId.NOTIFICATIONS -> "مجوز جداگانه‌ی نوتیفیکیشن در اندرویدهای قدیمی‌تر وجود ندارد."
                        StepId.EXACT_ALARMS -> "نسخه‌ی اندروید شما مجوز جداگانه‌ی آلارم دقیق را لازم ندارد."
                        else -> "این مورد در وضعیت فعلی به اقدام جداگانه نیاز ندارد."
                    }
                    c.addView(UiKit.body(this, details, 13f))
                }

                when (step.id) {
                    StepId.RESTRICTED_SETTINGS -> c.addView(UiKit.primaryButton(this,
                        if (step.status == StepStatus.ACTION_REQUIRED) "باز کردن اطلاعات برنامه" else "بررسی تنظیمات محدودشده") {
                        returningFromRestrictedSettings = true
                        runAction(StepId.RESTRICTED_SETTINGS)
                    })
                    StepId.ACCESSIBILITY -> c.addView(UiKit.primaryButton(this,
                        if (step.status == StepStatus.DONE) "باز کردن تنظیمات دسترسی" else "فعال‌کردن سرویس دسترسی") {
                        runAction(StepId.ACCESSIBILITY)
                    })
                    StepId.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33 && step.status != StepStatus.DONE) {
                        c.addView(UiKit.primaryButton(this, "اجازه‌ی نوتیفیکیشن") { runAction(StepId.NOTIFICATIONS) })
                    }
                    StepId.BATTERY -> if (step.status != StepStatus.DONE) {
                        c.addView(UiKit.primaryButton(this, "تنظیم دسترسی باتری") { runAction(StepId.BATTERY) })
                    }
                    StepId.EXACT_ALARMS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && step.status != StepStatus.DONE) {
                        c.addView(UiKit.primaryButton(this, "اجازه‌ی آلارم دقیق") { runAction(StepId.EXACT_ALARMS) })
                    }
                    StepId.CONFIG -> c.addView(UiKit.primaryButton(this,
                        if (step.status == StepStatus.DONE) "ویرایش قوانین" else "انتخاب برنامه‌ها و قوانین") {
                        runAction(StepId.CONFIG)
                    })
                }

                if (step.status == StepStatus.ACTION_REQUIRED) {
                    c.addView(UiKit.outlinedButton(this, "راهنمای مشکل این بخش") {
                        val details = OnboardingState.troubleshooting(step.id, samsung)
                            .joinToString("\n\n") { (issue, action) -> "• $issue\n$action" }
                        android.app.AlertDialog.Builder(this@OnboardingActivity)
                            .setTitle("عیب‌یابی: ${step.titleFa}")
                            .setMessage(details)
                            .setPositiveButton("متوجه شدم", null)
                            .show()
                    })
                }
            })
        }

        column.addView(UiKit.primaryButton(this, "بررسی دوباره‌ی همه‌ی مجوزها") { render() })
        if (steps.none { it.required && it.status == StepStatus.ACTION_REQUIRED }) {
            column.addView(UiKit.primaryButton(this, if (repairMode) "بازگشت به برنامه" else "اتمام راه‌اندازی") {
                if (!repairMode) PrefsManager.setOnboardingDone(this, true)
                finish()
            })
        }
        if (!repairMode) column.addView(UiKit.outlinedButton(this, "فعلاً بعداً") { finish() })
    }

    /** فهرست خلاصه با وضعیت دقیق هر قدم؛ زدن روی هر ردیف، آن قدم را باز می‌کند. */
    private fun renderSummary(
        visible: List<OnboardingState.SetupStep>,
        skipped: Set<String>,
        currentId: StepId?
    ) {
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "وضعیت قدم‌ها", 17f))
            visible.forEach { step ->
                val statusText = when {
                    step.id.name in skipped && step.status == StepStatus.ACTION_REQUIRED -> "رد شده (برای باز کردن، روی ردیف بزن)"
                    step.status == StepStatus.DONE -> "فعال ✓"
                    else -> "اقدام لازم"
                }
                val row = UiKit.row(this,
                    UiKit.body(this, step.titleFa + if (step.id == currentId) " (قدم جاری)" else ""),
                    UiKit.body(this, statusText, 13f))
                row.setOnClickListener {
                    if (step.id.name in skipped) PrefsManager.removeSkippedOnboardingStep(this, step.id.name)
                    focusId = step.id
                    showHelp = false
                    render()
                }
                c.addView(row)
            }
        })
    }
}
