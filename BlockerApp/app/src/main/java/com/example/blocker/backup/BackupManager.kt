package com.example.blocker.backup

import android.content.Context
import com.example.blocker.PrefsManager
import com.example.blocker.core.BackupValidator
import org.json.JSONArray
import org.json.JSONObject

/**
 * خروجی/ورودی بکاپ کانفیگ با schema نسخه‌دار.
 * بکاپ هرگز شامل سشن فعال، مجوزها یا وضعیت Device Owner نیست.
 * اعمال بکاپ اتمیک است: اول کل فایل validate می‌شود، بعد — و فقط بعد از تأیید کاربر — جایگزین می‌شود.
 */
object BackupManager {

    fun exportJson(context: Context): String {
        val o = JSONObject()
        o.put("schemaVersion", BackupValidator.CURRENT_SCHEMA)
        o.put("exportedAt", System.currentTimeMillis())
        val rules = JSONArray(); PrefsManager.getRules(context).forEach { rules.put(PrefsManager.ruleToJson(it)) }
        o.put("keywords", rules)
        o.put("websites", JSONArray(PrefsManager.getBlockedWebsites(context).toList()))
        o.put("apps", JSONArray(PrefsManager.getBlockedApps(context).toList()))
        o.put("delaySeconds", PrefsManager.getDelaySeconds(context))
        val profiles = JSONArray()
        PrefsManager.getProfiles(context).filter { !it.builtin }.forEach { profiles.put(PrefsManager.profileToJson(it)) }
        o.put("profiles", profiles)
        val counters = PrefsManager.getReportCounters(context)
        o.put("reports", counters)
        return o.toString(2)
    }

    private fun jsonToMap(o: JSONObject): Map<String, Any?> {
        val m = mutableMapOf<String, Any?>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            m[k] = when (val v = o.get(k)) {
                is JSONArray -> List(v.length()) { v.opt(it) }
                is JSONObject -> jsonToMap(v)
                else -> v
            }
        }
        return m
    }

    /** @return null اگر معتبر است، وگرنه متن خطا. */
    fun validateBackup(jsonText: String): String? {
        val parsed = try { JSONObject(jsonText) } catch (e: Exception) { return "فایل JSON معتبر نیست." }
        val errors = BackupValidator.validate(jsonToMap(parsed))
        return if (errors.isEmpty()) null else errors.joinToString("\n")
    }

    /** اعمال بکاپ؛ فقط بعد از validate موفق و تأیید کاربر صدا زده شود. */
    fun applyBackup(context: Context, jsonText: String): Boolean {
        if (validateBackup(jsonText) != null) return false
        return try {
            val o = JSONObject(jsonText)
            o.optJSONArray("keywords")?.let { arr ->
                PrefsManager.setRules(context, List(arr.length()) { PrefsManager.ruleFromJson(arr.getJSONObject(it)) })
            }
            o.optJSONArray("websites")?.let { arr ->
                val current = PrefsManager.getBlockedWebsites(context)
                current.forEach { PrefsManager.removeBlockedWebsite(context, it) }
                for (i in 0 until arr.length()) PrefsManager.addBlockedWebsite(context, arr.optString(i))
            }
            o.optJSONArray("apps")?.let { arr ->
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) set += arr.optString(i)
                PrefsManager.setBlockedApps(context, set)
            }
            if (o.has("delaySeconds")) PrefsManager.setDelaySeconds(context, o.optInt("delaySeconds", 5))
            o.optJSONArray("profiles")?.let { arr ->
                for (i in 0 until arr.length()) {
                    PrefsManager.saveCustomProfile(context, PrefsManager.profileFromJson(arr.getJSONObject(i)))
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
