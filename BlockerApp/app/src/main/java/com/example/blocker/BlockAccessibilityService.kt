package com.example.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.example.blocker.core.KeywordMatcher
import com.example.blocker.core.KeywordRule
import com.example.blocker.core.OwnAppGuard
import com.example.blocker.core.PendingBlockManager
import com.example.blocker.core.SelfProtection
import com.example.blocker.core.SessionMode
import com.example.blocker.core.TextNormalizer
import com.example.blocker.session.SessionManager

/**
 * قلب اجرای بلاکر.
 *
 * کانفیگ مؤثر:
 * - اگر سشن Strict فعال باشد، فقط snapshot قفل‌شده‌ی همان سشن اعمال می‌شود
 *   (تغییر لیست‌ها در میانه‌ی سشن اثری ندارد).
 * - در بقیه‌ی حالت‌ها (از جمله بدون سشن، مثل نسخه‌ی قبلی) کانفیگ زنده اعمال می‌شود.
 *
 * دیلی: هر تریگر واجد شرایط اول وارد PendingBlockManager می‌شود؛ اگر کانتکست
 * عوض شود اقدام معلق کنسل می‌شود و لحظه‌ی اجرا تطبیق دوباره بررسی می‌شود.
 * دیلی برای برنامه/دامنه از تنظیمات سراسری یا سشن می‌آید؛ برای کلمه از تأخیر
 * خودِ قانون (فوری یا ۱ تا ۵ ثانیه) وگرنه از دیلی سراسری/سشن.
 *
 * متن پنجره‌ی خودِ اپ هرگز ارزیابی نمی‌شود (OwnAppGuard: هم پکیج رخداد و هم
 * پکیج پنجره‌ی فعال چک می‌شود) تا کلمه‌ای که در خودِ اپ دیده می‌شود باعث
 * بسته شدن اپ نشود.
 *
 * محدودیت صادقانه: فقط متن قابل مشاهده در نودهای accessibility دیده می‌شود؛
 * متن داخل عکس/ویدیو، WebView های خاص و صفحه‌های امن پوشش تضمینی ندارند.
 */
class BlockAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "BlockerService"
        @Volatile var instanceAlive: Boolean = false

        private val URL_BAR_IDS = listOf(
            "com.android.chrome:id/url_bar",
            "com.chrome.beta:id/url_bar",
            "com.chrome.dev:id/url_bar",
            "org.mozilla.firefox:id/url_bar_title",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.microsoft.emmx:id/url_bar",
            "com.opera.browser:id/url_field",
            "com.brave.browser:id/url_bar",
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.android.browser:id/url"
        )
    }

    private data class EffectiveConfig(
        val apps: Set<String>,
        val websites: Set<String>,
        val rules: List<KeywordRule>,
        val delaySeconds: Int,
        val strict: Boolean
    )

    private data class Candidate(
        val signature: String,
        val ruleKey: String,
        val message: String,
        val packageName: String,
        /** تأخیر بلاک این کاندید بعد از شناسایی؛ برای کلمه از قانون، برای بقیه از دیلی سراسری/سشن. */
        val delaySeconds: Int
    )

    private val handler = Handler(Looper.getMainLooper())
    private val pendingBlocks = PendingBlockManager()
    private var pendingRunnable: Runnable? = null
    private var lastBlockedSignature: String = ""
    private var lastBlockedTime: Long = 0L
    private var lastSelfProtectTime: Long = 0L
    private var lastHeartbeat: Long = 0L
    private var lastSessionCheck: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceAlive = true
    }

    override fun onDestroy() {
        instanceAlive = false
        cancelPendingRunnable()
        super.onDestroy()
    }

    private fun effectiveConfig(): EffectiveConfig {
        val session = SessionManager.activeSession(this)
        return if (session != null && session.mode == SessionMode.STRICT) {
            EffectiveConfig(
                apps = SessionManager.snapshotApps(session.configSnapshotJson),
                websites = SessionManager.snapshotWebsites(session.configSnapshotJson),
                rules = SessionManager.snapshotRules(session.configSnapshotJson),
                delaySeconds = session.delaySeconds,
                strict = true
            )
        } else {
            EffectiveConfig(
                apps = PrefsManager.getBlockedApps(this),
                websites = PrefsManager.getBlockedWebsites(this),
                rules = PrefsManager.getRules(this).filter { it.enabled },
                delaySeconds = PrefsManager.getDelaySeconds(this),
                strict = false
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return
        heartbeat()
        maybeCheckSession()
        // نگهبان پنجره‌ی خودمان: رخداد با پکیج دیگر (مثل پنجره‌ی صفحه‌کلید هنگام
        // تایپ داخل اپ) در حالی که پنجره‌ی فعال خودِ اپ است، نباید باعث خواندن
        // متن خودِ اپ و بلاک آن شود.
        val activeWindowPackage = try { rootInActiveWindow?.packageName?.toString() } catch (e: Exception) { null }
        if (OwnAppGuard.shouldSkip(packageName, activeWindowPackage, this.packageName)) return

        try {
            if (checkSelfProtection(packageName)) return
            val config = effectiveConfig()
            val candidate = findCandidate(packageName, config)

            if (candidate == null) {
                // کانتکست دیگر واجد شرایط نیست → اقدام معلق کنسل شود
                pendingBlocks.cancelIfDifferent(null)
                cancelPendingRunnable()
                return
            }

            if (candidate.delaySeconds <= 0) {
                fireBlock(candidate)
                return
            }

            // دیلی واقعی: زمان‌بندی + شمارش معکوس حداقلی
            val now = System.currentTimeMillis()
            val isNew = pendingBlocks.current()?.signature != candidate.signature
            val pending = pendingBlocks.schedule(candidate.signature, candidate.ruleKey, now, candidate.delaySeconds)
            if (isNew) {
                Toast.makeText(this, "محتوای مسدود شناسایی شد؛ ${candidate.delaySeconds} ثانیه تا بستن…", Toast.LENGTH_SHORT).show()
            }
            cancelPendingRunnable()
            val delayMs = (pending.fireAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            val runnable = Runnable { revalidateAndFire(candidate, config) }
            pendingRunnable = runnable
            handler.postDelayed(runnable, delayMs)
        } catch (e: Exception) {
            Log.e(TAG, "error while processing event", e)
        }
    }

    /** لحظه‌ی اجرا: دوباره بررسی می‌کنیم که تریگر هنوز سر جایش هست یا نه. */
    private fun revalidateAndFire(original: Candidate, config: EffectiveConfig) {
        if (!pendingBlocks.consumeIfDue(System.currentTimeMillis(), original.signature)) return
        val fresh = try { findCandidate(original.packageName, config) } catch (e: Exception) { null }
        if (fresh != null && fresh.signature == original.signature) {
            fireBlock(fresh)
        }
    }

    /**
     * خودمحافظتی (راه بدون فکتوری‌ریست): فقط وقتی سشنی فعال و گارد روشن است، و فقط
     * صفحه‌ی جزئیاتِ سرویسِ خودِ «سد» در تنظیمات (لیبل + توضیح سرویس + Switch) یا
     * دیالوگ حذف در نصاب، با back بسته و صفحه‌ی «مسدود شد» نمایش داده می‌شود.
     * ریل‌های ایمنی: فقط back (هرگز HOME یا قفل)؛ ضربه‌گیر ۱.۵ ثانیه؛ fail-open در
     * شک؛ وقتی سشنی فعال نیست یا سرویس خاموش است (مسیر روشن کردن سرویس) هرگز
     * اجرا نمی‌شود — سرویس خاموش اصلاً رخداد نمی‌گیرد.
     * @return true اگر اقدام خودمحافظتی انجام شد (رخداد عادی دیگر پردازش نشود).
     */
    private fun checkSelfProtection(packageName: String): Boolean {
        val guardOn = PrefsManager.getSelfProtectionEnabled(this)
        if (!guardOn) return false
        val sessionActive = SessionManager.activeSession(this) != null
        if (!sessionActive) return false
        if (!SelfProtection.isRelevantForeground(packageName)) return false
        val root = rootInActiveWindow ?: return false
        val sb = StringBuilder()
        val switchFound = booleanArrayOf(false)
        try {
            collectTextAndSwitch(root, sb, 200, switchFound)
        } catch (e: Exception) {
            return false
        }
        val label = try { applicationInfo.loadLabel(packageManager)?.toString() ?: "" } catch (e: Exception) { "" }
        val guarded = when (packageName) {
            in SelfProtection.SETTINGS_PACKAGES -> SelfProtection.shouldGuardSettingsPage(
                sessionActive, guardOn, packageName, this.packageName, label,
                SelfProtection.DESCRIPTION_MARKER, sb.toString(), switchFound[0]
            )
            in SelfProtection.PACKAGE_INSTALLER_PACKAGES -> SelfProtection.shouldGuardInstallerDialog(
                sessionActive, guardOn, packageName, this.packageName, label, sb.toString()
            )
            else -> false
        }
        if (!guarded) return false
        val now = System.currentTimeMillis()
        if (now - lastSelfProtectTime < 1500) return true
        lastSelfProtectTime = now
        PrefsManager.recordBlock(this, "self-protection")
        performGlobalAction(GLOBAL_ACTION_BACK)
        val intent = Intent(this, BlockedScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(
                BlockedScreenActivity.EXTRA_MESSAGE,
                "این صفحه در طول سشن محافظت می‌شود.\n(حذف یا غیرفعال‌سازی سد وسط سشن مسدود است.)"
            )
        }
        startActivity(intent)
        return true
    }

    /** مثل collectText ولی هم‌زمان وجود یک Switch در درخت را هم گزارش می‌کند. */
    private fun collectTextAndSwitch(
        node: AccessibilityNodeInfo?,
        sb: StringBuilder,
        maxNodes: Int,
        switchFound: BooleanArray,
        visited: IntArray = intArrayOf(0)
    ) {
        if (node == null || visited[0] >= maxNodes) return
        visited[0] = visited[0] + 1
        if ((node.className?.toString() ?: "").endsWith("Switch")) switchFound[0] = true
        node.text?.let { sb.append(it).append(' ') }
        node.contentDescription?.let { sb.append(it).append(' ') }
        for (i in 0 until node.childCount) {
            if (visited[0] >= maxNodes) break
            val child = node.getChild(i) ?: continue
            try { collectTextAndSwitch(child, sb, maxNodes, switchFound, visited) } finally { child.recycle() }
        }
    }

    private fun findCandidate(packageName: String, config: EffectiveConfig): Candidate? {
        // دفاع دوم: حتی اگر رخداد از جای دیگری آمده باشد (مثلاً بازبینی بلاک معلق
        // زمان‌دار)، متن پنجره‌ی خودمان هرگز ارزیابی نمی‌شود.
        val activeWindowPackage = try { rootInActiveWindow?.packageName?.toString() } catch (e: Exception) { null }
        if (OwnAppGuard.shouldSkip(null, activeWindowPackage, this.packageName)) return null
        // ۱) بلاک برنامه
        if (config.apps.contains(packageName)) {
            return Candidate("app:$packageName", "app:$packageName", "این برنامه توسط شما مسدود شده است.", packageName, config.delaySeconds)
        }
        val root = rootInActiveWindow ?: return null
        try {
            // ۲) بلاک دامنه از نوار آدرس مرورگر
            if (config.websites.isNotEmpty()) {
                val url = findUrlText(root)
                if (url != null) {
                    val normalizedUrl = TextNormalizer.normalize(url)
                    for (domain in config.websites) {
                        val d = TextNormalizer.normalize(domain)
                        if (d.isNotBlank() && normalizedUrl.contains(d)) {
                            return Candidate("site:$domain", "site:$domain", "دسترسی به این سایت مسدود شده است.\n($domain)", packageName, config.delaySeconds)
                        }
                    }
                }
            }
            // ۳) بلاک کلمه‌کلیدی با موتور نرمال‌سازی + allowlist
            if (config.rules.isNotEmpty()) {
                val screenText = StringBuilder()
                collectText(root, screenText, maxNodes = 400)
                val match = KeywordMatcher.firstBlockMatch(config.rules, screenText.toString(), packageName)
                if (match != null) {
                    return Candidate(
                        "keyword:${match.rule.id}",
                        "keyword:${match.rule.text}",
                        "این صفحه به دلیل شامل بودن یک کلمه‌ی مسدود شده، بسته شد.",
                        packageName,
                        match.rule.effectiveDelaySeconds(config.delaySeconds)
                    )
                }
            }
        } finally {
            // root از rootInActiveWindow گرفته شده و recycle آن با خود سیستم است؛ فرزندان داخل collectText مدیریت می‌شوند.
        }
        return null
    }

    private fun collectText(node: AccessibilityNodeInfo?, sb: StringBuilder, maxNodes: Int, visited: IntArray = intArrayOf(0)) {
        if (node == null || visited[0] >= maxNodes) return
        visited[0] = visited[0] + 1
        node.text?.let { sb.append(it).append(' ') }
        node.contentDescription?.let { sb.append(it).append(' ') }
        for (i in 0 until node.childCount) {
            if (visited[0] >= maxNodes) break
            val child = node.getChild(i) ?: continue
            try { collectText(child, sb, maxNodes, visited) } finally { child.recycle() }
        }
    }

    private fun findUrlText(root: AccessibilityNodeInfo): String? {
        for (id in URL_BAR_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes != null && nodes.isNotEmpty()) {
                val text = nodes[0].text?.toString()
                nodes.forEach { it.recycle() }
                if (!text.isNullOrBlank()) return text
            }
        }
        return null
    }

    private fun fireBlock(candidate: Candidate) {
        val now = System.currentTimeMillis()
        if (candidate.signature == lastBlockedSignature && now - lastBlockedTime < 1500) return
        lastBlockedSignature = candidate.signature
        lastBlockedTime = now
        PrefsManager.recordBlock(this, candidate.ruleKey)
        performGlobalAction(GLOBAL_ACTION_HOME)
        val intent = Intent(this, BlockedScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockedScreenActivity.EXTRA_MESSAGE, candidate.message)
        }
        startActivity(intent)
    }

    private fun cancelPendingRunnable() {
        pendingRunnable?.let { handler.removeCallbacks(it) }
        pendingRunnable = null
    }

    private fun heartbeat() {
        val now = System.currentTimeMillis()
        if (now - lastHeartbeat < 5_000L) return
        lastHeartbeat = now
        getSharedPreferences("blocker_diag", MODE_PRIVATE).edit()
            .putLong("a11y_last_seen", now).apply()
    }

    private fun maybeCheckSession() {
        val now = System.currentTimeMillis()
        if (now - lastSessionCheck < 5_000L) return
        lastSessionCheck = now
        // current() گذارهای زمانی (انقضا/خروج) را idempotent اعمال می‌کند
        SessionManager.current(this)
    }

    override fun onInterrupt() {
        Log.w(TAG, "accessibility service interrupted")
    }
}
