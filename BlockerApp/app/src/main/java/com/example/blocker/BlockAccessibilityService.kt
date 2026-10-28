package com.example.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.example.blocker.core.SemanticEngine
import com.example.blocker.core.AiEngine
import com.example.blocker.core.AiVerdict
import com.example.blocker.core.DecisionCache
import com.example.blocker.core.KeywordRule
import com.example.blocker.core.KeywordScope
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
        private val LIVE_SEARCH_PACKAGES = setOf("org.telegram.messenger", "com.instagram.android")
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
        val delaySeconds: Int,
        /** برای بلاک کلمه‌کلیدی: شناسه‌ی قانون و عبارتِ شاهد، برای بازخورد «اشتباه بود». */
        val ruleId: String? = null,
        val evidence: String? = null
    )

    private val handler = Handler(Looper.getMainLooper())
    private val pendingBlocks = PendingBlockManager()
    /** کش تصمیم‌های معنایی برای این سرویس (محدود، با انقضا؛ متن خام نگه نمی‌دارد). */
    private val aiDecisionCache = DecisionCache()
    private var pendingRunnable: Runnable? = null
    private var lastBlockedSignature: String = ""
    private var lastBlockedTime: Long = 0L
    private var lastSelfProtectTime: Long = 0L
    /** آخرین باری که صفحه‌ی اطلاعاتِ خودِ اپ در تنظیمات دیده شد (برای شناسایی دیالوگ توقف AOSP که نام اپ را ندارد). */
    private var lastOwnAppInfoPageTime: Long = 0L
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
        return when {
            // بدون سشن، هیچ بلاک محتوایی نیست: صرفِ اضافه کردن قانون بلاک نمی‌کند.
            session == null -> EffectiveConfig(
                apps = emptySet(),
                websites = emptySet(),
                rules = emptyList(),
                delaySeconds = 0,
                strict = false
            )
            session.mode == SessionMode.STRICT -> EffectiveConfig(
                apps = SessionManager.snapshotApps(session.configSnapshotJson),
                websites = SessionManager.snapshotWebsites(session.configSnapshotJson),
                rules = SessionManager.snapshotRules(session.configSnapshotJson),
                delaySeconds = session.delaySeconds,
                strict = true
            )
            else -> EffectiveConfig(
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
        // heartbeat و بررسی سشن بیرون از try اصلی بودند؛ استثنای نادر آن‌ها
        // سرویس را می‌کشت. حالا همه‌ی پردازش رخداد زیر یک چتر است.
        try {
            heartbeat()
            maybeCheckSession()
            // نگهبان پنجره‌ی خودمان: رخداد با پکیج دیگر (مثل پنجره‌ی صفحه‌کلید هنگام
            // تایپ داخل اپ) در حالی که پنجره‌ی فعال خودِ اپ است، نباید باعث خواندن
            // متن خودِ اپ و بلاک آن شود.
            val activeWindowPackage = try { rootInActiveWindow?.packageName?.toString() } catch (e: Exception) { null }
            if (OwnAppGuard.shouldSkip(packageName, activeWindowPackage, this.packageName)) return

            val targetPackage = activeWindowPackage ?: packageName
            if (checkSelfProtection(targetPackage)) return
            val config = effectiveConfig()
            // حالت پیش‌فرض: فقط با کلیک واقعی روی جستجو/ارسال بررسی کن؛
            // نوشتن متن یا دیده‌شدن اتفاقی کلمه در صفحه، به‌تنهایی تریگر نیست.
            val liveSearchInputChanged =
                event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
                    targetPackage in LIVE_SEARCH_PACKAGES
            val searchSubmitted =
                event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && isSearchActionEvent(event)
            val pageContentTrigger =
                PrefsManager.isPageContentBlockingEnabled(this) &&
                    (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                     event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            val keywordTriggerAllowed = liveSearchInputChanged || searchSubmitted || pageContentTrigger
            val candidate = findCandidate(targetPackage, config, keywordTriggerAllowed)

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
            // بدنه‌ی runnable هم زیر چتر است؛ استثنای نادرِ لحظه‌ی اجرا نباید
            // نخ اصلی و با آن کل فرایند را بکشد.
            val runnable = Runnable {
                try {
                    revalidateAndFire(candidate, config)
                } catch (e: Exception) {
                    Log.e(TAG, "error while firing pending block", e)
                }
            }
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
     * خودمحافظتی (راه بدون فکتوری‌ریست)، در سه سطح:
     *  - دیالوگ‌های حذف و توقف اجباریِ «سد»: هر وقت کلید خودمحافظتی روشن است
     *    (حتی بدون سشن) با back بسته می‌شوند؛ در سشن Strict برای همان سشن
     *    اجباری‌اند حتی اگر کلید خاموش بوده باشد (مقدار ذخیره‌شده دست نمی‌خورد).
     *  - صفحه‌ی اطلاعات اپ: فقط در Strict بسته می‌شود تا دکمه‌های حذف و توقف
     *    اصلاً در دسترس نباشند؛ بیرون از Strict این صفحه همیشه باز است.
     *  - صفحه‌ی جزئیات سرویسِ خودمان در تنظیمات: فقط وسط سشن بسته می‌شود تا
     *    بیرون از سشن، مدیریت سرویس همیشه در دسترس کاربر باشد.
     * ترتیب مهم: اول تصمیم گارد با پرچمِ فعلیِ صفحه‌ی اطلاعات اپ، بعد
     * به‌روزرسانی پرچم از صفحه‌ی فعلی — تا رخدادِ خودِ دیالوگ پرچم را پاک نکند.
     * ریل‌های ایمنی: فقط back (هرگز HOME یا قفل)؛ ضربه‌گیر ۱.۵ ثانیه؛ fail-open
     * در شک؛ وقتی سرویس خاموش است (مسیر روشن کردن سرویس) هرگز اجرا نمی‌شود —
     * سرویس خاموش اصلاً رخداد نمی‌گیرد.
     * @return true اگر اقدام خودمحافظتی انجام شد (رخداد عادی دیگر پردازش نشود).
     */
    private fun checkSelfProtection(packageName: String): Boolean {
        val persistedOn = PrefsManager.getSelfProtectionEnabled(this)
        val session = SessionManager.activeSession(this)
        val strictActive = session?.mode == SessionMode.STRICT
        val guardOn = SelfProtection.isGuardEffective(persistedOn, strictActive)
        if (!guardOn) return false
        if (!SelfProtection.isRelevantForeground(packageName)) return false
        val root = rootInActiveWindow ?: return false
        val sb = StringBuilder()
        val switchFound = booleanArrayOf(false)
        try {
            collectTextAndSwitch(root, sb, 200, switchFound)
        } catch (e: Exception) {
            return false
        }
        val text = sb.toString()
        val label = try { applicationInfo.loadLabel(packageManager)?.toString() ?: "" } catch (e: Exception) { "" }
        val now = System.currentTimeMillis()
        val sessionActive = session != null
        val flagFresh = SelfProtection.isFlagFresh(lastOwnAppInfoPageTime, now)

        val message: String? = when (packageName) {
            in SelfProtection.SETTINGS_PACKAGES -> when {
                // در Strict، خودِ صفحه‌ی اطلاعات اپ بسته می‌شود تا دکمه‌های حذف و
                // توقف اصلاً در دسترس نباشند — نه فقط دیالوگِ بعد از زدن دکمه.
                strictActive && SelfProtection.isOwnAppInfoPage(text, this.packageName, label) ->
                    "صفحه‌ی اطلاعات «سد» در طول سشن Strict بسته می‌شود.\nتا پایان تایمر نمی‌توانی اپ را حذف یا متوقف کنی."
                SelfProtection.shouldGuardSettingsPage(
                    sessionActive, guardOn, packageName, this.packageName, label,
                    SelfProtection.DESCRIPTION_MARKER, text, switchFound[0]
                ) -> "این صفحه در طول سشن محافظت می‌شود.\n(حذف یا غیرفعال‌سازی سد وسط سشن مسدود است.)"
                SelfProtection.shouldGuardForceStopDialog(
                    guardOn, packageName, this.packageName, label, text, flagFresh
                ) -> if (strictActive) "توقف اجباری «سد» در طول سشن Strict مسدود است.\nتا پایان تایمر باز نمی‌شود."
                    else "توقف اجباری «سد» مسدود است.\nبرای توقف اجباری، اول خودمحافظتی را در تنظیماتِ اپ خاموش کن."
                SelfProtection.shouldGuardSettingsUninstallDialog(
                    guardOn, packageName, this.packageName, label, text, flagFresh
                ) -> if (strictActive) "حذف «سد» در طول سشن Strict مسدود است.\nتا پایان تایمر باز نمی‌شود."
                    else "حذف «سد» مسدود است.\nبرای حذف، اول خودمحافظتی را در تنظیماتِ اپ خاموش کن."
                else -> null
            }
            in SelfProtection.PACKAGE_INSTALLER_PACKAGES -> when {
                SelfProtection.shouldGuardInstallerDialog(
                    guardOn, packageName, this.packageName, label, text
                ) -> if (strictActive) "حذف «سد» در طول سشن Strict مسدود است.\nتا پایان تایمر باز نمی‌شود."
                    else "حذف «سد» مسدود است.\nبرای حذف، اول خودمحافظتی را در تنظیماتِ اپ خاموش کن."
                else -> null
            }
            else -> null
        }
        if (message == null) {
            // گاردی عمل نکرد: پرچم را از صفحه‌ی فعلی به‌روز کن — صفحه‌ی اطلاعاتِ
            // خودمان پرچم را تازه می‌کند، هر صفحه‌ی دیگری در تنظیمات آن را پاک می‌کند.
            if (packageName in SelfProtection.SETTINGS_PACKAGES) {
                lastOwnAppInfoPageTime =
                    if (SelfProtection.isOwnAppInfoPage(text, this.packageName, label)) now else 0L
            }
            return false
        }
        lastOwnAppInfoPageTime = 0L
        if (now - lastSelfProtectTime < 1500) return true
        lastSelfProtectTime = now
        PrefsManager.recordBlock(this, "self-protection")
        performGlobalAction(GLOBAL_ACTION_BACK)
        val intent = Intent(this, BlockedScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockedScreenActivity.EXTRA_MESSAGE, message)
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

    private fun findCandidate(
        packageName: String,
        config: EffectiveConfig,
        keywordTriggerAllowed: Boolean = true
    ): Candidate? {
        // دفاع دوم: حتی اگر رخداد از جای دیگری آمده باشد (مثلاً بازبینی بلاک معلق
        // زمان‌دار)، متن پنجره‌ی خودمان هرگز ارزیابی نمی‌شود.
        val activeWindowPackage = try { rootInActiveWindow?.packageName?.toString() } catch (e: Exception) { null }
        if (OwnAppGuard.shouldSkip(null, activeWindowPackage, this.packageName)) return null
        // بلاک محتوا فقط وسط سشن: بدون سشنِ فعال، هیچ اپ/سایت/کلمه‌ای بلاک نمی‌شود.
        // (این گارد، بازبینیِ بلاکِ معلقِ زمان‌دار را هم پوشش می‌دهد: اگر سشن وسط
        // دیلی تمام شود، بلاک شلیک نمی‌شود.)
        if (SessionManager.activeSession(this) == null) return null
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
            // ۳) بلاک کلمه‌کلیدی با موتور تصمیم روی دستگاه.
            // پیش‌فرض فقط پس از کلیک واقعی جستجو/ارسال، متن فیلد ورودی بررسی می‌شود.
            // بررسی متن محتوای صفحه انتخابی است و از لیست‌ها/پیشنهادها صرف‌نظر می‌کند.
            if (config.rules.isNotEmpty() && keywordTriggerAllowed) {
                val inputText = StringBuilder()
                val contentText = StringBuilder()
                collectScopedText(root, inputText, contentText, inList = false, maxNodes = 400)
                // جستجو/آدرس همیشه از فیلد ورودی می‌آید؛ محتوای صفحه فقط
                // وقتی کاربر حالت اختیاری آن را روشن کرده باشد به تصمیم اضافه می‌شود.
                val scopedText = buildString {
                    append(inputText)
                    if (PrefsManager.isPageContentBlockingEnabled(this@BlockAccessibilityService)) {
                        append(' ')
                        append(contentText)
                    }
                }
                val decision = AiEngine.classify(
                    config.rules, scopedText, packageName,
                    PrefsManager.getLearnedAllow(this),
                    PrefsManager.getDecisionPolicy(this),
                    PrefsManager.isAiEnabled(this),
                    aiDecisionCache,
                    System.currentTimeMillis()
                )
                // شمارنده‌ها: فقط طبقه‌بندی‌های تازه (نه از کش)؛ متن هرگز ذخیره نمی‌شود.
                PrefsManager.recordAiDecision(this, decision)
                if (decision.verdict == AiVerdict.BLOCK && decision.rule != null) {
                    return Candidate(
                        "keyword:${decision.rule.id}",
                        "keyword:${decision.rule.text}",
                        "این صفحه به دلیل شامل بودن یک کلمه‌ی مسدود شده، بسته شد.",
                        packageName,
                        decision.rule.effectiveDelaySeconds(config.delaySeconds),
                        decision.rule.id,
                        decision.evidence
                    )
                }
            }
        } finally {
            // root از rootInActiveWindow گرفته شده و recycle آن با خود سیستم است؛ فرزندان داخل collectText مدیریت می‌شوند.
        }
        return null
    }

    /**
     * جمع‌آوری متن پنجره با قاعده‌ی KeywordScope: متن ورودی (جعبه‌ی جستجو/آدرس)
     * و متن محتوا (غیرلیستی) جدا جمع می‌شوند؛ متن داخل لیست‌ها (تاریخچه،
     * پیشنهادها، فید) در هیچ‌کدام نمی‌آید تا صرفِ دیده شدن کلمه بلاک نکند.
     */
    private fun collectScopedText(
        node: AccessibilityNodeInfo?,
        inputSb: StringBuilder,
        contentSb: StringBuilder,
        inList: Boolean,
        maxNodes: Int,
        visited: IntArray = intArrayOf(0)
    ) {
        if (node == null || visited[0] >= maxNodes) return
        visited[0] = visited[0] + 1
        val cls = node.className?.toString()
        val nowInList = inList || KeywordScope.isListContainerClass(cls)
        val isInput = KeywordScope.isInputNode(cls, node.isEditable)
        val target = when {
            isInput -> inputSb
            !nowInList && PrefsManager.isPageContentBlockingEnabled(this) -> contentSb
            else -> null
        }
        if (target != null) {
            node.text?.let { target.append(it).append(' ') }
            node.contentDescription?.let { target.append(it).append(' ') }
        }
        for (i in 0 until node.childCount) {
            if (visited[0] >= maxNodes) break
            val child = node.getChild(i) ?: continue
            try { collectScopedText(child, inputSb, contentSb, nowInList, maxNodes, visited) } finally { child.recycle() }
        }
    }


    /** تشخیص محافظه‌کارانه‌ی عمل جستجو/ارسال در فیلد ورودی قابل‌دسترسی. */
    private fun isSearchActionEvent(event: AccessibilityEvent): Boolean {
        val parts = mutableListOf<String>()
        event.text?.forEach { parts += it.toString() }
        event.contentDescription?.toString()?.let { parts += it }
        val source = try { event.source } catch (_: Exception) { null }
        if (source != null) {
            try {
                // کلیک روی خودِ فیلد جستجو، انتخاب پیشنهاد یا فوکوس ورودی
                // «ارسال جستجو» محسوب نمی‌شود.
                if (source.isEditable) return false
                source.text?.toString()?.let { parts += it }
                source.contentDescription?.toString()?.let { parts += it }
                source.viewIdResourceName?.let { parts += it }
            } finally {
                source.recycle()
            }
        }
        val label = parts.joinToString(" ").lowercase()
        return listOf("search", "query", "submit", "ime", "enter", "جستجو", "جست‌وجو", "بگرد", "انتقال")
            .any { label.contains(it) }
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
            // برای بازخورد یادگیری روی صفحه‌ی بلاک (فقط بلاک کلمه‌کلیدی).
            candidate.ruleId?.let { putExtra(BlockedScreenActivity.EXTRA_RULE_ID, it) }
            candidate.evidence?.let { putExtra(BlockedScreenActivity.EXTRA_EVIDENCE, it) }
            if (candidate.ruleId != null) putExtra(BlockedScreenActivity.EXTRA_PACKAGE_NAME, candidate.packageName)
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
