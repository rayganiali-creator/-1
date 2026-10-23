package com.example.blocker.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** گیرنده‌ی Device Admin؛ پیش‌نیاز Device Owner شدن با پروویژن رسمی اندروید. */
class BlockDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Log.i("BlockerAdmin", "device admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.w("BlockerAdmin", "device admin disabled")
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Log.i("BlockerAdmin", "provisioning complete")
    }
}
