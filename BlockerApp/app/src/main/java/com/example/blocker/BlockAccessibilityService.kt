package com.example.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * این سرویس قلب برنامه بلاکر است.
 * هر بار که صفحه یا محتوای صفحه روی گوشی تغییر می‌کند، اندروید این سرویس را
 * با یک AccessibilityEvent خبر می‌کند. ما بررسی می‌کنیم:
 *   ۱) آیا برنامه‌ی در حال اجرا در لیست برنامه‌های مسدود است؟
 *   ۲) اگر مرورگر است، آیا آدرس سایت با لیست سایت‌های مسدود مطابقت دارد؟
 *   ۳) آیا متن روی صفحه شامل یکی از کلمات کلیدی مسدود شده است؟
 * در صورت مطابقت، کاربر را به صفحه‌ی BlockedScreenActivity می‌فرستیم.
 */
class BlockAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "BlockerService"

        // شناسه‌ی نوار آدرس (url bar) در معروف‌ترین مرورگرها
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

    private var lastBlockedSignature: String = ""
    private var lastBlockedTime: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        // خود برنامه‌ی بلاکر و صفحه‌ی مسدودی را هرگز مسدود نکن
        if (packageName == this.packageName) return

        try {
            // ۱) بررسی لیست برنامه‌های مسدود شده
            val blockedApps = PrefsManager.getBlockedApps(this)
            if (blockedApps.contains(packageName)) {
                triggerBlock("app:$packageName", "این برنامه توسط شما مسدود شده است.")
                return
            }

            val root = rootInActiveWindow ?: return

            // ۲) بررسی آدرس سایت در صورتی که برنامه یک مرورگر شناخته شده باشد
            val blockedWebsites = PrefsManager.getBlockedWebsites(this)
            if (blockedWebsites.isNotEmpty()) {
                val url = findUrlText(root)
                if (url != null) {
                    val lowerUrl = url.lowercase()
                    for (domain in blockedWebsites) {
                        if (domain.isNotBlank() && lowerUrl.contains(domain)) {
                            triggerBlock("site:$domain", "دسترسی به این سایت مسدود شده است.\n($domain)")
                            return
                        }
                    }
                }
            }

            // ۳) بررسی کلمات کلیدی در کل متن صفحه (مستقل از نوع برنامه)
            val blockedKeywords = PrefsManager.getBlockedKeywords(this)
            if (blockedKeywords.isNotEmpty()) {
                val screenText = StringBuilder()
                collectText(root, screenText, maxNodes = 400)
                val lowerScreen = screenText.toString().lowercase()
                for (keyword in blockedKeywords) {
                    if (keyword.isNotBlank() && lowerScreen.contains(keyword)) {
                        triggerBlock("keyword:$keyword", "این صفحه به دلیل شامل بودن یک کلمه‌ی\nمسدود شده، بسته شد.")
                        return
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "error while processing event", e)
        }
    }

    /** به صورت بازگشتی و محدود، متن نودهای accessibility را جمع می‌کند. */
    private fun collectText(node: AccessibilityNodeInfo?, sb: StringBuilder, maxNodes: Int, visited: IntArray = intArrayOf(0)) {
        if (node == null || visited[0] >= maxNodes) return
        visited[0] = visited[0] + 1

        node.text?.let { sb.append(it).append(' ') }
        node.contentDescription?.let { sb.append(it).append(' ') }

        for (i in 0 until node.childCount) {
            if (visited[0] >= maxNodes) break
            collectText(node.getChild(i), sb, maxNodes, visited)
        }
    }

    /** تلاش می‌کند متن نوار آدرس مرورگر را با شناسه‌های شناخته‌شده پیدا کند. */
    private fun findUrlText(root: AccessibilityNodeInfo): String? {
        for (id in URL_BAR_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes != null && nodes.isNotEmpty()) {
                val text = nodes[0].text?.toString()
                if (!text.isNullOrBlank()) return text
            }
        }
        return null
    }

    /** جلوگیری از باز کردن مکرر صفحه‌ی مسدودی برای همان رخداد در بازه‌ی زمانی کوتاه. */
    private fun triggerBlock(signature: String, message: String) {
        val now = System.currentTimeMillis()
        if (signature == lastBlockedSignature && now - lastBlockedTime < 1500) return
        lastBlockedSignature = signature
        lastBlockedTime = now

        // ابتدا کاربر را به صفحه‌ی اصلی می‌بریم تا برنامه/سایت مسدود از پس‌زمینه هم خارج شود
        performGlobalAction(GLOBAL_ACTION_HOME)

        val intent = Intent(this, BlockedScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockedScreenActivity.EXTRA_MESSAGE, message)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        Log.w(TAG, "accessibility service interrupted")
    }
}
