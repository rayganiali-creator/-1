package com.example.blocker.ui

import android.content.Context
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.blocker.admin.DeviceOwnerHelper
import com.example.blocker.service.SessionForegroundService
import com.example.blocker.session.RecoveryStore

/**
 * ریکاوری اضطراری — عمداً مستقل از SessionManager و مدل‌های اصلی:
 * مستقیم با فایل‌های SharedPreferences خام کار می‌کند تا اگر وضعیت سشن
 * خراب شده باشد یا ناوبری اصلی بالا نیاید، باز هم بشود دستگاه را نجات داد.
 * این صفحه exported است تا با adb هم قابل باز کردن باشد:
 *   adb shell am start -n com.example.blocker/.ui.RecoveryActivity
 */
class RecoveryActivity : AppCompatActivity() {

    private lateinit var column: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "ریکاوری اضطراری"
        val (scroll, col) = UiKit.screen(this)
        column = col
        setContentView(scroll)
        render()
    }

    private fun render() {
        column.removeAllViews()
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "وضعیت ذخیره‌شده در آینه‌ی ریکاوری", 17f))
            c.addView(UiKit.body(this, RecoveryStore.describe(this)))
            val corrupt = getSharedPreferences("blocker_session", Context.MODE_PRIVATE).getBoolean("session_corrupt", false)
            if (corrupt) c.addView(UiKit.body(this, "⚠ داده‌ی سشن اصلی خراب تشخیص داده شده است؛ دکمه‌ی زیر آن را کامل پاک می‌کند."))
        })

        column.addView(UiKit.primaryButton(this, "پایان اضطراری همه‌ی سشن‌ها و پاک‌کردن وضعیت") {
            AlertDialog.Builder(this)
                .setTitle("ریکاوری اضطراری")
                .setMessage("وضعیت سشن (از جمله Strict و درخواست خروج) کاملاً پاک می‌شود، سرویس سشن متوقف می‌شود و اگر اپ Device Owner باشد بلاک حذف هم برداشته می‌شود. قوانین و کانفیگ شما پاک نمی‌شوند.")
                .setPositiveButton("انجام بده") { _, _ -> emergencyReset() }
                .setNegativeButton("انصراف", null)
                .show()
        })

        if (DeviceOwnerHelper.isDeviceOwner(this)) {
            column.addView(UiKit.tonalButton(this, "برداشتن فقط بلاک حذف (Device Owner)") {
                val ok = DeviceOwnerHelper.applyUninstallBlockIfOwner(this, false)
                Toast.makeText(this, if (ok) "بلاک حذف برداشته شد" else "ناموفق بود", Toast.LENGTH_LONG).show()
            })
        }

        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "اگر اپ اصلاً باز نمی‌شود", 17f))
            c.addView(UiKit.body(this,
                "۱) گوشی را ری‌استارت کنید؛ اگر سشن تمام شده باشد خودش پاک می‌شود.\n" +
                "۲) از حالت امن (Safe Mode) اندروید وارد شوید: در آن حالت سرویس‌های اپ‌های ثالث اجرا نمی‌شوند و می‌توانید اپ را از تنظیمات حذف یا داده‌اش را پاک کنید.\n" +
                "۳) با adb: دستور زیر این صفحه را باز می‌کند:\n" +
                "adb shell am start -n com.example.blocker/.ui.RecoveryActivity\n" +
                "۴) اگر اپ Device Owner است و می‌خواهید مدیریتش را کامل بردارید: اول از همین صفحه بلاک حذف را بردارید، بعد از تنظیمات اندروید اپ را حذف کنید. فکتوری ریست راه پیش‌فرض نیست و لازم نمی‌شود.\n" +
                "۵) این اپ هرگز Settings، لانچر یا برنامه‌های ضروری سیستم را بلاک نمی‌کند."))
        })
    }

    private fun emergencyReset() {
        // پاک‌کردن خام و مستقل — بدون تکیه بر هیچ مدل یا دیتابیسی
        try { SessionForegroundService.stop(this) } catch (e: Exception) { }
        try { DeviceOwnerHelper.applyUninstallBlockIfOwner(this, false) } catch (e: Exception) { }
        getSharedPreferences("blocker_session", Context.MODE_PRIVATE).edit().clear().commit()
        getSharedPreferences(RecoveryStore.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        Toast.makeText(this, "وضعیت سشن پاک شد؛ اپ به حالت عادی برگشت.", Toast.LENGTH_LONG).show()
        render()
    }
}
