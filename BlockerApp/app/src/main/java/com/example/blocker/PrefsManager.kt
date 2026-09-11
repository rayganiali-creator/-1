package com.example.blocker

import android.content.Context
import android.content.SharedPreferences

/**
 * کلاس مدیریت ذخیره‌سازی لیست برنامه‌ها، سایت‌ها و کلمات مسدود شده.
 * از SharedPreferences استفاده می‌کند تا هم توسط اکتیویتی‌ها و هم توسط
 * AccessibilityService قابل خواندن باشد.
 */
object PrefsManager {

    private const val PREFS_NAME = "blocker_prefs"
    private const val KEY_BLOCKED_APPS = "blocked_apps"
    private const val KEY_BLOCKED_WEBSITES = "blocked_websites"
    private const val KEY_BLOCKED_KEYWORDS = "blocked_keywords"
    private const val KEY_SERVICE_ENABLED = "service_enabled"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---------- برنامه‌های مسدود شده (package name) ----------
    fun getBlockedApps(context: Context): MutableSet<String> =
        HashSet(prefs(context).getStringSet(KEY_BLOCKED_APPS, emptySet()) ?: emptySet())

    fun setBlockedApps(context: Context, apps: Set<String>) {
        prefs(context).edit().putStringSet(KEY_BLOCKED_APPS, apps).apply()
    }

    fun toggleBlockedApp(context: Context, packageName: String, blocked: Boolean) {
        val set = getBlockedApps(context)
        if (blocked) set.add(packageName) else set.remove(packageName)
        setBlockedApps(context, set)
    }

    // ---------- سایت‌های مسدود شده (دامنه، مثل instagram.com) ----------
    fun getBlockedWebsites(context: Context): MutableSet<String> =
        LinkedHashSet(prefs(context).getStringSet(KEY_BLOCKED_WEBSITES, emptySet()) ?: emptySet())

    fun addBlockedWebsite(context: Context, domain: String) {
        val set = getBlockedWebsites(context)
        set.add(domain.trim().lowercase())
        prefs(context).edit().putStringSet(KEY_BLOCKED_WEBSITES, set).apply()
    }

    fun removeBlockedWebsite(context: Context, domain: String) {
        val set = getBlockedWebsites(context)
        set.remove(domain)
        prefs(context).edit().putStringSet(KEY_BLOCKED_WEBSITES, set).apply()
    }

    // ---------- کلمات کلیدی مسدود شده ----------
    fun getBlockedKeywords(context: Context): MutableSet<String> =
        LinkedHashSet(prefs(context).getStringSet(KEY_BLOCKED_KEYWORDS, emptySet()) ?: emptySet())

    fun addBlockedKeyword(context: Context, keyword: String) {
        val set = getBlockedKeywords(context)
        set.add(keyword.trim().lowercase())
        prefs(context).edit().putStringSet(KEY_BLOCKED_KEYWORDS, set).apply()
    }

    fun removeBlockedKeyword(context: Context, keyword: String) {
        val set = getBlockedKeywords(context)
        set.remove(keyword)
        prefs(context).edit().putStringSet(KEY_BLOCKED_KEYWORDS, set).apply()
    }
}
