package com.example.blocker.ui

import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.blocker.BuildConfig
import com.example.blocker.admin.DeviceOwnerHelper

/** درباره، حریم خصوصی و محدودیت‌های صادقانه. */
class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "درباره و محدودیت‌ها"
        val (scroll, column) = UiKit.screen(this)
        setContentView(scroll)

        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "سد — نسخه ${BuildConfig.VERSION_NAME}", 18f))
            c.addView(UiKit.body(this, "اپی برای کم کردن رفتار اجباری: بلاک برنامه‌ها، دامنه‌ها و کلمات کلیدی با سرویس دسترسی اندروید، سشن‌های فوکوس زمان‌دار و حالت Strict با خروج چندمرحله‌ای."))
        })
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "چه کارهایی می‌تواند", 17f))
            c.addView(UiKit.body(this, "• بلاک برنامه‌های انتخاب‌شده\n• بلاک دامنه از نوار آدرس مرورگرهای معروف (Chrome، Firefox، Samsung Internet، Edge، Brave، Opera و…)\n• بلاک کلمه/عبارت در متن قابل مشاهده‌ی صفحه، با نرمال‌سازی فارسی/عربی و تطبیق کلمه‌ی کامل\n• سشن Normal و Strict با تایمر مقاوم در برابر بستن اپ و ریبوت\n• پروفایل، زمان‌بندی، گزارش لوکال و بکاپ کانفیگ"))
        })
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "چه کارهایی نمی‌تواند (صادقانه)", 17f))
            c.addView(UiKit.body(this,
                "• اپ معمولی نمی‌تواند جلوی حذف شدن خودش یا Force Stop را بگیرد. بعد از Force Stop، اندروید تا باز شدن دستی اپ اجازه‌ی اجرا به آن نمی‌دهد.\n" +
                "• سرویس دسترسی فقط متن‌هایی را می‌بیند که برنامه‌ها در نودهای accessibility عرضه می‌کنند؛ متن داخل عکس و ویدیو، بعضی WebView ها، صفحه‌های امن و کیبوردهای سفارشی ممکن است دیده نشوند. پوشش همگانی تضمین نمی‌شود.\n" +
                "• جست‌وجوی داخل HTTPS (مثلاً عبارت جست‌وجوشده در گوگل) از بیرون قابل خواندن نیست؛ فقط دامنه دیده می‌شود.\n" +
                "• سازنده‌های مختلف گوشی سیاست‌های پس‌زمینه‌ی متفاوتی دارند؛ اپ نمی‌تواند آن‌ها را دور بزند."))
        })
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "Device Owner (اختیاری، پیشرفته)", 17f))
            c.addView(UiKit.body(this,
                "تنها راه رسمی برای بلاک حذف واقعی، Device Owner شدن اپ است. این کار از داخل اپ و خودکار انجام نمی‌شود؛ نیازمند پروویژن رسمی اندروید است — روی گوشی شخصی معمولاً یعنی فکتوری ریست و نصب از صفر، بعد اجرای دستور زیر با adb:\n\n" +
                DeviceOwnerHelper.provisioningAdbCommand(packageName) + "\n\n" +
                "وضعیت فعلی این دستگاه: " + (if (DeviceOwnerHelper.isDeviceOwner(this)) "اپ Device Owner است." else "اپ Device Owner نیست.") +
                "\nحذف مدیریت: از صفحه‌ی ریکاوری بلاک حذف را بردارید و اپ را حذف کنید؛ نیازی به فکتوری ریست نیست. این کار را روی گوشی اصلی‌تان بدون تست روی دستگاه دوم انجام ندهید."))
        })
        column.addView(UiKit.card(this) { c ->
            c.addView(UiKit.title(this, "حریم خصوصی", 17f))
            c.addView(UiKit.body(this, "همه‌چیز لوکال است. هیچ داده‌ای به سرور ارسال نمی‌شود. متن صفحاتی که بررسی می‌شوند جایی ذخیره نمی‌شوند؛ گزارش‌ها فقط شمارنده‌اند (تعداد سشن‌ها، بلاک‌ها و خرابی‌ها). بکاپ را خودتان با فایل‌ساز اندروید ذخیره و بازیابی می‌کنید."))
        })
    }
}
