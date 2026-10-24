package com.example.blocker

import android.content.Context
import android.content.SharedPreferences
import com.example.blocker.core.BlockingDelays
import com.example.blocker.core.CoolingOff
import com.example.blocker.core.FocusProfile
import com.example.blocker.core.KeywordRule
import com.example.blocker.core.RuleDelays
import com.example.blocker.core.SessionMode
import com.example.blocker.core.SessionSchedule
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * مدیریت ذخیره‌سازی کانفیگ بلاکر.
 * سازگاری به عقب حفظ شده: API قدیمی (StringSet ها) سر جایش است و قوانین جدید
 * (KeywordRule) روی همان داده‌ی قدیمی مهاجرت می‌شوند — اولین خواندن قوانین،
 * StringSet قدیمی را به قانون‌های نسخه‌دار تبدیل می‌کند.
 */
object PrefsManager {

    private const val PREFS_NAME = "blocker_prefs"
    private const val KEY_BLOCKED_APPS = "blocked_apps"
    private const val KEY_BLOCKED_WEBSITES = "blocked_websites"
    private const val KEY_BLOCKED_KEYWORDS = "blocked_keywords"
    private const val KEY_RULES_JSON = "rules_json_v2"
    private const val KEY_PROFILES_JSON = "profiles_json_v2"
    private const val KEY_SCHEDULES_JSON = "schedules_json_v2"
    private const val KEY_REPORTS_JSON = "reports_json_v2"
    private const val KEY_DELAY_SECONDS = "delay_seconds"
    private const val KEY_DEFAULT_COOLING = "default_cooling_off"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"
    private const val KEY_WIDGET_PROMPT_NEVER = "widget_prompt_never"
    private const val KEY_ONBOARDING_STEP = "onboarding_step"
    private const val KEY_LAST_DIAGNOSTIC = "last_diagnostic_at"
    private const val KEY_SELF_PROTECTION = "self_protection_enabled"
    private const val KEY_THEME = "theme"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---------- برنامه‌های مسدود شده ----------
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

    // ---------- سایت‌های مسدود شده ----------
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

    // ---------- کلمات کلیدی: API قدیمی (سازگاری) ----------
    fun getBlockedKeywords(context: Context): MutableSet<String> =
        LinkedHashSet(prefs(context).getStringSet(KEY_BLOCKED_KEYWORDS, emptySet()) ?: emptySet())

    fun addBlockedKeyword(context: Context, keyword: String) {
        addRule(context, KeywordRule(id = newId(), text = keyword.trim()))
    }

    fun removeBlockedKeyword(context: Context, keyword: String) {
        val rules = getRules(context).filterNot { it.text.equals(keyword, ignoreCase = true) }
        setRules(context, rules)
    }

    // ---------- قوانین نسخه‌دار (v2) ----------
    fun newId(): String = UUID.randomUUID().toString()

    fun getRules(context: Context): List<KeywordRule> {
        val p = prefs(context)
        val json = p.getString(KEY_RULES_JSON, null)
        if (json == null) {
            val legacy = getBlockedKeywords(context)
            val migrated = legacy.map { KeywordRule(id = newId(), text = it) }
            setRules(context, migrated)
            return migrated
        }
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i -> ruleFromJson(arr.getJSONObject(i)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setRules(context: Context, rules: List<KeywordRule>) {
        val arr = JSONArray()
        rules.forEach { arr.put(ruleToJson(it)) }
        prefs(context).edit()
            .putString(KEY_RULES_JSON, arr.toString())
            .putStringSet(KEY_BLOCKED_KEYWORDS, rules.filter { it.enabled && !it.isAllow && it.targetPackages.isEmpty() }.map { it.text }.toSet())
            .apply()
    }

    fun addRule(context: Context, rule: KeywordRule) {
        setRules(context, getRules(context) + rule)
    }

    fun updateRule(context: Context, rule: KeywordRule) {
        setRules(context, getRules(context).map { if (it.id == rule.id) rule else it })
    }

    fun removeRule(context: Context, ruleId: String) {
        setRules(context, getRules(context).filterNot { it.id == ruleId })
    }

    fun categories(context: Context): List<String> =
        getRules(context).map { it.category }.distinct().sorted()

    fun ruleToJson(r: KeywordRule): JSONObject = JSONObject().apply {
        put("id", r.id); put("text", r.text); put("category", r.category)
        put("enabled", r.enabled); put("wholeWord", r.wholeWord)
        put("caseSensitive", r.caseSensitive); put("isAllow", r.isAllow)
        put("targets", JSONArray(r.targetPackages.toList()))
        put("delaySeconds", r.delaySeconds)
    }

    fun ruleFromJson(o: JSONObject): KeywordRule {
        val targets = mutableSetOf<String>()
        o.optJSONArray("targets")?.let { a -> for (i in 0 until a.length()) targets += a.optString(i) }
        return KeywordRule(
            id = o.optString("id", newId()),
            text = o.optString("text", ""),
            category = o.optString("category", "عمومی"),
            enabled = o.optBoolean("enabled", true),
            wholeWord = o.optBoolean("wholeWord", false),
            caseSensitive = o.optBoolean("caseSensitive", false),
            isAllow = o.optBoolean("isAllow", false),
            targetPackages = targets,
            // قوانین قدیمی که کلید ندارند، روی «دیلی سراسری» می‌مانند تا رفتارشان عوض نشود.
            delaySeconds = RuleDelays.sanitize(o.optInt("delaySeconds", RuleDelays.FOLLOW_GLOBAL))
        )
    }

    // ---------- تنظیمات ----------
    /** تم پس‌زمینه: system (خودکار)، light، dark، mint، blue، cream. */
    fun getTheme(context: Context): String =
        prefs(context).getString(KEY_THEME, "system") ?: "system"

    fun setTheme(context: Context, theme: String) {
        prefs(context).edit().putString(KEY_THEME, theme).apply()
    }

    fun getDelaySeconds(context: Context): Int =
        BlockingDelays.sanitize(prefs(context).getInt(KEY_DELAY_SECONDS, BlockingDelays.DEFAULT_SECONDS))

    fun setDelaySeconds(context: Context, seconds: Int) {
        prefs(context).edit().putInt(KEY_DELAY_SECONDS, BlockingDelays.sanitize(seconds)).apply()
    }

    fun getDefaultCoolingOff(context: Context): CoolingOff =
        try { CoolingOff.valueOf(prefs(context).getString(KEY_DEFAULT_COOLING, CoolingOff.HOURS_2.name)!!) }
        catch (e: Exception) { CoolingOff.HOURS_2 }

    fun setDefaultCoolingOff(context: Context, c: CoolingOff) {
        prefs(context).edit().putString(KEY_DEFAULT_COOLING, c.name).apply()
    }

    /** خودمحافظتی: گارد دیالوگ‌های حذف/توقف (همیشه، با کلید) + گارد صفحه‌ی سرویس (فقط وسط سشن). پیش‌فرض روشن؛ از تنظیمات قابل خاموش کردن است. */
    fun getSelfProtectionEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SELF_PROTECTION, true)

    fun setSelfProtectionEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SELF_PROTECTION, enabled).apply()
    }

    fun isOnboardingDone(context: Context): Boolean = prefs(context).getBoolean(KEY_ONBOARDING_DONE, false)
    fun setOnboardingDone(context: Context, done: Boolean) { prefs(context).edit().putBoolean(KEY_ONBOARDING_DONE, done).apply() }
    fun isWidgetPromptNever(context: Context): Boolean = prefs(context).getBoolean(KEY_WIDGET_PROMPT_NEVER, false)
    fun setWidgetPromptNever(context: Context, never: Boolean) { prefs(context).edit().putBoolean(KEY_WIDGET_PROMPT_NEVER, never).apply() }
    fun getOnboardingStep(context: Context): Int = prefs(context).getInt(KEY_ONBOARDING_STEP, 0)
    fun setOnboardingStep(context: Context, step: Int) { prefs(context).edit().putInt(KEY_ONBOARDING_STEP, step).apply() }
    fun getLastDiagnosticAt(context: Context): Long = prefs(context).getLong(KEY_LAST_DIAGNOSTIC, 0L)
    fun setLastDiagnosticAt(context: Context, at: Long) { prefs(context).edit().putLong(KEY_LAST_DIAGNOSTIC, at).apply() }

    // ---------- پروفایل‌ها ----------
    fun getProfiles(context: Context): List<FocusProfile> {
        val custom = mutableListOf<FocusProfile>()
        try {
            val arr = JSONArray(prefs(context).getString(KEY_PROFILES_JSON, "[]"))
            for (i in 0 until arr.length()) custom += profileFromJson(arr.getJSONObject(i))
        } catch (e: Exception) { /* کانفیگ خراب → فقط preset ها */ }
        return FocusProfile.presets() + custom
    }

    fun saveCustomProfile(context: Context, profile: FocusProfile) {
        val custom = getProfiles(context).filter { !it.builtin }.toMutableList()
        val idx = custom.indexOfFirst { it.id == profile.id }
        if (idx >= 0) custom[idx] = profile else custom += profile
        val arr = JSONArray(); custom.forEach { arr.put(profileToJson(it)) }
        prefs(context).edit().putString(KEY_PROFILES_JSON, arr.toString()).apply()
    }

    fun deleteCustomProfile(context: Context, profileId: String) {
        val custom = getProfiles(context).filter { !it.builtin && it.id != profileId }
        val arr = JSONArray(); custom.forEach { arr.put(profileToJson(it)) }
        prefs(context).edit().putString(KEY_PROFILES_JSON, arr.toString()).apply()
    }

    fun profileToJson(p: FocusProfile): JSONObject = JSONObject().apply {
        put("id", p.id); put("name", p.name)
        put("apps", JSONArray(p.blockedApps.toList()))
        put("cats", JSONArray(p.keywordCategories.toList()))
        put("duration", p.durationMillis); put("delay", p.delaySeconds)
        put("mode", p.mode.name); put("cooling", p.coolingOff.name); put("builtin", p.builtin)
    }

    fun profileFromJson(o: JSONObject): FocusProfile {
        fun strSet(key: String): Set<String> {
            val s = mutableSetOf<String>()
            o.optJSONArray(key)?.let { a -> for (i in 0 until a.length()) s += a.optString(i) }
            return s
        }
        return FocusProfile(
            id = o.optString("id", newId()), name = o.optString("name", "پروفایل"),
            blockedApps = strSet("apps"), keywordCategories = strSet("cats"),
            durationMillis = o.optLong("duration", 25L * 60_000L),
            delaySeconds = BlockingDelays.sanitize(o.optInt("delay", BlockingDelays.DEFAULT_SECONDS)),
            mode = try { SessionMode.valueOf(o.optString("mode", "NORMAL")) } catch (e: Exception) { SessionMode.NORMAL },
            coolingOff = try { CoolingOff.valueOf(o.optString("cooling", "HOURS_2")) } catch (e: Exception) { CoolingOff.HOURS_2 },
            builtin = o.optBoolean("builtin", false)
        )
    }

    // ---------- زمان‌بندی‌ها ----------
    fun getSchedules(context: Context): List<SessionSchedule> {
        val out = mutableListOf<SessionSchedule>()
        try {
            val arr = JSONArray(prefs(context).getString(KEY_SCHEDULES_JSON, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val days = mutableSetOf<Int>()
                o.optJSONArray("days")?.let { a -> for (j in 0 until a.length()) days += a.optInt(j) }
                out += SessionSchedule(
                    id = o.optString("id", newId()), profileId = o.optString("profileId", ""),
                    hour = o.optInt("hour", 20), minute = o.optInt("minute", 0),
                    daysOfWeek = days, enabled = o.optBoolean("enabled", true)
                )
            }
        } catch (e: Exception) { /* خالی برمی‌گردد */ }
        return out
    }

    fun setSchedules(context: Context, schedules: List<SessionSchedule>) {
        val arr = JSONArray()
        schedules.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id); put("profileId", s.profileId)
                put("hour", s.hour); put("minute", s.minute)
                put("days", JSONArray(s.daysOfWeek.toList())); put("enabled", s.enabled)
            })
        }
        prefs(context).edit().putString(KEY_SCHEDULES_JSON, arr.toString()).apply()
    }

    // ---------- گزارش‌های لوکال (فقط شمارنده‌ها؛ هرگز متن صفحات ذخیره نمی‌شود) ----------
    fun getReportCounters(context: Context): JSONObject =
        try { JSONObject(prefs(context).getString(KEY_REPORTS_JSON, "{}")) } catch (e: Exception) { JSONObject() }

    private fun mutateReports(context: Context, block: (JSONObject) -> Unit) {
        val o = getReportCounters(context)
        block(o)
        prefs(context).edit().putString(KEY_REPORTS_JSON, o.toString()).apply()
    }

    fun recordSessionStarted(context: Context) = mutateReports(context) {
        it.put("sessionsStarted", it.optInt("sessionsStarted") + 1)
    }

    fun recordSessionFinished(context: Context, completed: Boolean, focusMillis: Long) = mutateReports(context) {
        if (completed) {
            it.put("sessionsCompleted", it.optInt("sessionsCompleted") + 1)
            it.put("totalFocusMillis", it.optLong("totalFocusMillis") + focusMillis)
        } else {
            it.put("sessionsInterrupted", it.optInt("sessionsInterrupted") + 1)
        }
    }

    fun recordBlock(context: Context, ruleKey: String) = mutateReports(context) {
        it.put("blocksTotal", it.optInt("blocksTotal") + 1)
        val byRule = it.optJSONObject("blocksByRule") ?: JSONObject()
        byRule.put(ruleKey, byRule.optInt(ruleKey) + 1)
        it.put("blocksByRule", byRule)
    }

    fun recordProtectionFailure(context: Context, cause: String) = mutateReports(context) {
        it.put("protectionFailures", it.optInt("protectionFailures") + 1)
        val byCause = it.optJSONObject("failuresByCause") ?: JSONObject()
        byCause.put(cause, byCause.optInt(cause) + 1)
        it.put("failuresByCause", byCause)
    }

    fun clearReports(context: Context) {
        prefs(context).edit().remove(KEY_REPORTS_JSON).apply()
    }
}
