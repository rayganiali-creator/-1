package com.example.blocker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import com.example.blocker.PrefsManager
import com.example.blocker.R
import com.example.blocker.health.ProtectionHealth
import com.example.blocker.session.SessionManager
import com.example.blocker.ui.SessionActivity
import com.example.blocker.widget.SessionWidgetUpdater

/**
 * سرویس پیش‌زمینه‌ی سشن: نوتیفیکیشن پایدار با زمان باقی‌مانده.
 * صادقانه: این سرویس جلوی Force Stop را نمی‌گیرد؛ فقط تا وقتی سیستم اجازه می‌دهد
 * سشن را قابل مشاهده و پردازش را زنده نگه می‌دارد.
 */
class SessionForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "blocker_session"
        const val NOTIF_ID = 42
        const val ALERT_CHANNEL_ID = "blocker_alert"
        const val ALERT_NOTIF_ID = 43
        @Volatile var running: Boolean = false

        fun start(context: Context) {
            // شروع از پس‌زمینه (مثلاً از گیرنده‌ی زمان‌بندی) در اندروید ۱۲+ گاهی
            // ForegroundServiceStartNotAllowedException می‌دهد؛ استثنا نباید
            // فراخواننده (گیرنده/اکتیویتی) را بکشد — ثبت و عبور، نه سقوط.
            try {
                val intent = Intent(context, SessionForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            } catch (e: Exception) {
                try {
                    PrefsManager.recordProtectionFailure(context, "fgs_start_failed")
                } catch (ignored: Exception) {
                }
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SessionForegroundService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var alertShown = false
    private var foregroundStarted = false

    /** تیک زنده‌ی ویجت (هر ثانیه)؛ فقط وقتی ویجتی نصب شده است.
     * بدنه زیر چتر است: استثنای نادرِ تیکر روی نخ اصلی، کل فرایند را نمی‌کشد؛
     * تیک بعدی طبق زمان‌بندی ادامه می‌دهد (نه حلقه‌ی بی‌پایانِ راه‌اندازی مجدد). */
    private val widgetTicker = object : Runnable {
        override fun run() {
            try {
                SessionWidgetUpdater.updateAll(this@SessionForegroundService)
            } catch (e: Exception) {
                // ثبت نمی‌کنیم تا لاگ را پر نکند؛ تیک بعدی ادامه می‌دهد.
            }
            handler.postDelayed(this, 1_000L)
        }
    }

    private fun startWidgetTicker() {
        handler.removeCallbacks(widgetTicker)
        // ویجت قابلیت جانبی است؛ خرابی provider یا داده‌ی ویجت نباید
        // سرویس اصلی سشن را متوقف کند.
        try {
            if (SessionWidgetUpdater.hasWidget(this)) handler.post(widgetTicker)
        } catch (e: Exception) {
            handler.removeCallbacks(widgetTicker)
        }
    }
    private val ticker = object : Runnable {
        override fun run() {
            try {
                val s = SessionManager.activeSession(this@SessionForegroundService)
                if (s == null) { stopSelf(); return }
                notify(buildNotification(s))
                SessionWidgetUpdater.updateAll(this@SessionForegroundService)
                // اگر ویجت وسط سشن نصب شده باشد، تیک زنده‌اش را حداکثر تا ۳۰ ثانیه‌ی
                // دیگر راه بینداز (شروع سشن، ویجتی نداشت که تیکش را روشن کند).
                startWidgetTicker()
                // نگهبان خاموشی سرویس: اگر وسط سشن، سرویس دسترسی خاموش شد، به‌جای
                // سکوت (که یعنی «محافظت ادامه دارد» در حالی که ندارد) یک بار هشدار بده.
                // با برگشتن سرویس، پرچم ریست می‌شود تا خاموشی بعدی دوباره هشدار بدهد.
                if (!ProtectionHealth.isAccessibilityEnabled(this@SessionForegroundService)) {
                    if (!alertShown) {
                        alertShown = true
                        PrefsManager.recordProtectionFailure(this@SessionForegroundService, "accessibility-disabled-during-session")
                        notifyAlert()
                    }
                } else {
                    alertShown = false
                }
            } catch (e: Exception) {
                // تیکر ۳۰ ثانیه‌ای: استثنا نباید فرایند را بکشد؛ تیک بعدی ادامه می‌دهد.
                try {
                    PrefsManager.recordProtectionFailure(this@SessionForegroundService, "fgs_ticker_error")
                } catch (ignored: Exception) {
                }
            }
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY ممکن است سرویس را با intent=null بازسازی کند؛ وضعیت سشن
        // از ذخیره‌سازی خوانده می‌شود و intent منبع حقیقت نیست.
        try {
            val s = SessionManager.activeSession(this)
            if (s == null) {
                handler.removeCallbacks(ticker)
                handler.removeCallbacks(widgetTicker)
                running = false
                stopSelfResult(startId)
                return START_NOT_STICKY
            }

            // سرویس باید فوراً foreground شود، پیش از کارهای جانبی.
            val notif = buildNotification(s)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, notif)
            }
            foregroundStarted = true
            running = true

            try {
                PrefsManager.recordServiceStart(this)
            } catch (ignored: Exception) {
                // خرابی آمار تشخیصی نباید سرویس را از کار بیندازد.
            }

            handler.removeCallbacks(ticker)
            handler.postDelayed(ticker, 30_000L)
            try { SessionWidgetUpdater.updateAll(this) } catch (ignored: Exception) {}
            startWidgetTicker()
            return START_STICKY
        } catch (e: Exception) {
            // اگر اندروید ارتقای سرویس را رد کند، استثنا را به crash تبدیل نکن.
            running = false
            handler.removeCallbacks(ticker)
            handler.removeCallbacks(widgetTicker)
            try { PrefsManager.recordProtectionFailure(this, "fgs_start_or_promote_failed") } catch (ignored: Exception) {}
            if (foregroundStarted) {
                try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (ignored: Exception) {}
                foregroundStarted = false
            }
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // بستن کارت برنامه از صفحه‌ی اخیرها را پایان سشن تلقی نمی‌کنیم.
        // استارت خودکار از پس‌زمینه عمداً انجام نمی‌شود؛ اندروید آن را محدود می‌کند.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(widgetTicker)
        if (foregroundStarted) {
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (ignored: Exception) {}
            foregroundStarted = false
        }
        try { SessionWidgetUpdater.updateAll(this) } catch (ignored: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notify(n: Notification) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, n)
    }

    private fun notifyAlert() {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alert = Notification.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_blocker)
            .setContentTitle("هشدار: سرویس سد خاموش شد")
            .setContentText("محافظت متوقف شده است؛ برای ادامه‌ی سشن، سرویس را دوباره فعال کنید.")
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(ALERT_NOTIF_ID, alert)
    }

    private fun buildNotification(s: com.example.blocker.core.SessionRecord): Notification {
        val remaining = SessionManager.remainingMillis(this, s)
        val mins = remaining / 60_000L
        val secs = (remaining % 60_000L) / 1000L
        val modeFa = if (s.mode == com.example.blocker.core.SessionMode.STRICT) "Strict" else "عادی"
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, SessionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_blocker)
            .setContentTitle("سشن فوکوس فعال است — حالت $modeFa")
            .setContentText(String.format("زمان باقی‌مانده: %02d:%02d — %s", mins, secs, s.profileName))
            .setOngoing(true)
            .setContentIntent(openIntent)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, "سشن فوکوس", NotificationManager.IMPORTANCE_LOW).apply {
                description = "نوتیفیکیشن پایدار سشن فعال سد"
            }
            nm.createNotificationChannel(channel)
            val alertChannel = NotificationChannel(ALERT_CHANNEL_ID, "هشدار محافظت", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "هشدار خاموش شدن سرویس سد وسط سشن"
            }
            nm.createNotificationChannel(alertChannel)
        }
    }
}
