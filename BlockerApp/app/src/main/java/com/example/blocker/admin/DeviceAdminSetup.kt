package com.example.blocker.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent

/**
 * فعال‌سازی Device Admin با رضایت صریح کاربر.
 * صداقت فنی: Device Admin به‌تنهایی جلوی حذف را نمی‌گیرد — فقط یک مرحله‌ی
 * اصطکاکی اضافه می‌کند (اول باید در تنظیمات غیرفعال شود و سپس حذف انجام شود).
 * قفل واقعیِ حذف فقط با Device Owner و setUninstallBlocked ممکن است.
 * این تابع هرگز بدون لمس دکمه توسط کاربر صدا زده نمی‌شود.
 */
object DeviceAdminSetup {

    fun requestDeviceAdmin(activity: Activity) {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, DeviceOwnerHelper.adminComponent(activity))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "سد برای افزودن یک مرحله‌ی حفاظتی قبل از حذف شدن، درخواست Device Admin می‌کند. " +
                        "این مجوز به‌تنهایی جلوی حذف را نمی‌گیرد؛ فقط حذف را چند مرحله‌ای می‌کند. " +
                        "هر وقت بخواهی می‌توانی از تنظیمات > امنیت > Device admin apps آن را غیرفعال کنی."
            )
        }
        activity.startActivity(intent)
    }

    fun statusLabelFa(context: Context): String = when {
        DeviceOwnerHelper.isDeviceOwner(context) -> "Device Owner فعال است — بلاک واقعی حذف در سشن Strict اعمال می‌شود."
        DeviceOwnerHelper.isDeviceAdminActive(context) -> "Device Admin فعال است — حذف، یک مرحله‌ی اضافی در تنظیمات لازم دارد (اصطکاک، نه قفل)."
        else -> "فعال نیست — برای حذف سد هیچ مرحله‌ی اضافه‌ای لازم نیست."
    }
}
