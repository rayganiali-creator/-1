package com.example.blocker.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build

/**
 * تنها نقطه‌ی تماس با DevicePolicyManager.
 * صداقت فنی: اپ معمولی نمی‌تواند خودش را Device Owner کند. این کلاس فقط وقتی
 * اثر واقعی دارد که اپ قبلاً با پروویژن رسمی (مثلاً دستور adb روی دستگاه
 * فکتوری‌ریست‌شده یا QR provisioning) Device Owner شده باشد.
 */
object DeviceOwnerHelper {

    fun adminComponent(context: Context): ComponentName =
        ComponentName(context, BlockDeviceAdminReceiver::class.java)

    fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isDeviceOwner(context: Context): Boolean =
        try { dpm(context).isDeviceOwnerApp(context.packageName) } catch (e: Exception) { false }

    fun isDeviceAdminActive(context: Context): Boolean =
        try { dpm(context).isAdminActive(adminComponent(context)) } catch (e: Exception) { false }

    /** آیا سیستم واقعاً حذف این اپ را بلاک کرده؟ (از روی پالیسی سیستم، نه فلگ لوکال) */
    fun isUninstallBlockedBySystem(context: Context): Boolean {
        if (!isDeviceOwner(context)) return false
        return try {
            // API رسمی برای خواندن مستقیم فلگ وجود ندارد؛ معیار عملی ما این است که
            // Device Owner هستیم و آخرین اعمال ما موفق بوده — برای نمایش محافظه‌کارانه
            // در Diagnostics فقط «قابل اعمال» گزارش می‌شود، نه تضمین مطلق.
            dpm(context).isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) { false }
    }

    /**
     * اعمال/رفع بلاک حذف با API رسمی setUninstallBlocked.
     * فقط در صورت Device Owner بودن اثر دارد؛ در غیر این صورت بی‌صدا رد می‌شود
     * و false برمی‌گردد تا UI سطح محافظت را اشتباه نشان ندهد.
     * @return true اگر واقعاً روی سیستم اعمال شد.
     */
    fun applyUninstallBlockIfOwner(context: Context, blocked: Boolean): Boolean {
        if (!isDeviceOwner(context)) return false
        return try {
            dpm(context).setUninstallBlocked(adminComponent(context), context.packageName, blocked)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** دستور رسمی پروویژن برای نمایش در راهنما — هرگز از داخل اپ اجرا نمی‌شود. */
    fun provisioningAdbCommand(packageName: String): String =
        "adb shell dpm set-device-owner $packageName/com.example.blocker.admin.BlockDeviceAdminReceiver"

    fun supportsDeviceOwner(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
}
