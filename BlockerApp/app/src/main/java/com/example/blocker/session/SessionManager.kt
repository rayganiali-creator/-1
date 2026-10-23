package com.example.blocker.session

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.example.blocker.PrefsManager
import com.example.blocker.admin.DeviceOwnerHelper
import com.example.blocker.core.CoolingOff
import com.example.blocker.core.FocusProfile
import com.example.blocker.core.SessionMode
import com.example.blocker.core.SessionRecord
import com.example.blocker.core.SessionStatus
import com.example.blocker.core.SessionTimeCalculator
import org.json.JSONArray
import org.json.JSONObject

/**
 * تنها مرجع حقیقت برای وضعیت سشن (Single source of truth).
 * ذخیره‌سازی با commit() همگام انجام می‌شود تا سشن بعد از process death گم نشود.
 * یک آینه‌ی حداقلی جدا (RecoveryStore) هم نوشته می‌شود تا ریکاوری اضطراری
 * حتی با خرابی این فایل ممکن باشد.
 */
object SessionManager {

    private const val PREFS = "blocker_session"
    private const val KEY_SESSION_JSON = "session_json"
    private const val KEY_CORRUPT = "session_corrupt"

    fun bootCount(context: Context): Int =
        try { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) }
        catch (e: Exception) { -1 }

    // ---------- خواندن ----------
    fun currentRaw(context: Context): SessionRecord? {
        val json = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SESSION_JSON, null) ?: return null
        return try { fromJson(JSONObject(json)) } catch (e: Exception) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_CORRUPT, true).commit()
            null
        }
    }

    fun isCorrupt(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_CORRUPT, false)

    /** سشن جاری بعد از اعمال گذارهای زمانی (انقضا / نهایی‌شدن خروج) — idempotent. */
    fun current(context: Context): SessionRecord? {
        val s = currentRaw(context) ?: return null
        if (s.status == SessionStatus.COMPLETED || s.status == SessionStatus.INTERRUPTED) return s
        val nowWall = System.currentTimeMillis()
        if (s.status == SessionStatus.EXIT_PENDING && SessionTimeCalculator.exitReady(s, nowWall)) {
            return finish(context, s, completed = false)
        }
        if (isExpiredNow(context, s)) {
            return finish(context, s, completed = true)
        }
        return s
    }

    fun activeSession(context: Context): SessionRecord? {
        val s = current(context) ?: return null
        return if (s.status == SessionStatus.ACTIVE || s.status == SessionStatus.EXIT_PENDING) s else null
    }

    fun isStrictActive(context: Context): Boolean =
        activeSession(context)?.mode == SessionMode.STRICT

    fun sameBoot(context: Context, s: SessionRecord): Boolean {
        val bc = bootCount(context)
        return if (bc >= 0 && s.startBootWallMillis > 0) {
            // startBootWallMillis اینجا bootCount شروع را نگه می‌دارد (نام تاریخی فیلد در مدل core)
            bc.toLong() == s.startBootWallMillis
        } else {
            SessionTimeCalculator.likelySameBoot(s, SystemClock.elapsedRealtime())
        }
    }

    fun isExpiredNow(context: Context, s: SessionRecord): Boolean =
        SessionTimeCalculator.isExpired(s, System.currentTimeMillis(), SystemClock.elapsedRealtime(), sameBoot(context, s))

    fun remainingMillis(context: Context, s: SessionRecord): Long =
        SessionTimeCalculator.remainingMillis(s, System.currentTimeMillis(), SystemClock.elapsedRealtime(), sameBoot(context, s))

    // ---------- ساخت snapshot کانفیگ ----------
    fun buildSnapshotJson(context: Context, profile: FocusProfile?): String {
        val apps = (profile?.blockedApps?.takeIf { it.isNotEmpty() } ?: PrefsManager.getBlockedApps(context))
        val websites = PrefsManager.getBlockedWebsites(context)
        var rules = PrefsManager.getRules(context).filter { it.enabled }
        if (profile != null && profile.keywordCategories.isNotEmpty()) {
            rules = rules.filter { it.isAllow || it.category in profile.keywordCategories }
        }
        val rulesArr = JSONArray(); rules.forEach { rulesArr.put(PrefsManager.ruleToJson(it)) }
        return JSONObject().apply {
            put("apps", JSONArray(apps.toList()))
            put("websites", JSONArray(websites.toList()))
            put("rules", rulesArr)
        }.toString()
    }

    fun snapshotApps(snapshotJson: String): Set<String> = try {
        val a = JSONObject(snapshotJson).optJSONArray("apps")
        val out = mutableSetOf<String>()
        if (a != null) for (i in 0 until a.length()) out += a.optString(i)
        out
    } catch (e: Exception) { emptySet() }

    fun snapshotWebsites(snapshotJson: String): Set<String> = try {
        val a = JSONObject(snapshotJson).optJSONArray("websites")
        val out = mutableSetOf<String>()
        if (a != null) for (i in 0 until a.length()) out += a.optString(i)
        out
    } catch (e: Exception) { emptySet() }

    fun snapshotRules(snapshotJson: String): List<com.example.blocker.core.KeywordRule> = try {
        val a = JSONObject(snapshotJson).optJSONArray("rules")
        val out = mutableListOf<com.example.blocker.core.KeywordRule>()
        if (a != null) for (i in 0 until a.length()) out += PrefsManager.ruleFromJson(a.getJSONObject(i))
        out
    } catch (e: Exception) { emptyList() }

    // ---------- چرخه‌ی حیات ----------
    fun startSession(
        context: Context,
        mode: SessionMode,
        durationMillis: Long,
        profile: FocusProfile?,
        delaySeconds: Int,
        coolingOff: CoolingOff
    ): SessionRecord {
        val nowWall = System.currentTimeMillis()
        val record = SessionRecord(
            id = PrefsManager.newId(),
            mode = mode,
            status = SessionStatus.ACTIVE,
            profileName = profile?.name ?: if (mode == SessionMode.STRICT) "Strict دستی" else "سشن دستی",
            durationMillis = durationMillis,
            startWallMillis = nowWall,
            startElapsedMillis = SystemClock.elapsedRealtime(),
            startBootWallMillis = bootCount(context).toLong(),
            endWallMillis = nowWall + durationMillis,
            delaySeconds = delaySeconds,
            coolingOff = coolingOff,
            configSnapshotJson = buildSnapshotJson(context, profile)
        )
        persist(context, record)
        PrefsManager.recordSessionStarted(context)
        if (mode == SessionMode.STRICT) {
            DeviceOwnerHelper.applyUninstallBlockIfOwner(context, true)
        }
        return record
    }

    /** درخواست خروج از Strict: سشن فعال می‌ماند، فقط وضعیت EXIT_PENDING می‌شود. */
    fun requestStrictExit(context: Context): SessionRecord? {
        val s = activeSession(context) ?: return null
        if (s.mode != SessionMode.STRICT || s.status != SessionStatus.ACTIVE) return s
        val now = System.currentTimeMillis()
        val updated = s.copy(
            status = SessionStatus.EXIT_PENDING,
            exitRequestedAtWallMillis = now,
            exitDeadlineWallMillis = SessionTimeCalculator.exitDeadline(s, now)
        )
        persist(context, updated)
        return updated
    }

    fun cancelExitRequest(context: Context): SessionRecord? {
        val s = activeSession(context) ?: return null
        if (s.status != SessionStatus.EXIT_PENDING) return s
        val updated = s.copy(status = SessionStatus.ACTIVE, exitRequestedAtWallMillis = 0L, exitDeadlineWallMillis = 0L)
        persist(context, updated)
        return updated
    }

    /** پایان سشن (طبیعی، دستی در Normal، یا نهایی‌شدن خروج). فقط یک بار اثر می‌کند. */
    fun finish(context: Context, s: SessionRecord, completed: Boolean): SessionRecord {
        if (s.status == SessionStatus.COMPLETED || s.status == SessionStatus.INTERRUPTED) return s
        val done = s.copy(
            status = if (completed) SessionStatus.COMPLETED else SessionStatus.INTERRUPTED,
            completedAtWallMillis = System.currentTimeMillis()
        )
        persist(context, done)
        val focus = (done.completedAtWallMillis - s.startWallMillis).coerceIn(0L, s.durationMillis)
        PrefsManager.recordSessionFinished(context, completed, if (completed) s.durationMillis else focus)
        if (s.mode == SessionMode.STRICT) {
            DeviceOwnerHelper.applyUninstallBlockIfOwner(context, false)
        }
        RecoveryStore.clear(context)
        return done
    }

    fun endNormalSessionNow(context: Context): SessionRecord? {
        val s = activeSession(context) ?: return null
        if (s.mode != SessionMode.NORMAL) return null
        return finish(context, s, completed = false)
    }

    /** پاک‌کردن کامل وضعیت سشن؛ مسیر ریکاوری اضطراری هم از همین اثر استفاده می‌کند ولی مستقل پیاده شده. */
    fun clearAll(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
        DeviceOwnerHelper.applyUninstallBlockIfOwner(context, false)
        RecoveryStore.clear(context)
    }

    private fun persist(context: Context, record: SessionRecord) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SESSION_JSON, toJson(record).toString()).putBoolean(KEY_CORRUPT, false).commit()
        RecoveryStore.writeMirror(context, record)
    }

    // ---------- JSON ----------
    fun toJson(s: SessionRecord): JSONObject = JSONObject().apply {
        put("id", s.id); put("mode", s.mode.name); put("status", s.status.name)
        put("profileName", s.profileName); put("duration", s.durationMillis)
        put("startWall", s.startWallMillis); put("startElapsed", s.startElapsedMillis)
        put("bootCount", s.startBootWallMillis); put("endWall", s.endWallMillis)
        put("delay", s.delaySeconds); put("cooling", s.coolingOff.name)
        put("snapshot", s.configSnapshotJson)
        put("exitReq", s.exitRequestedAtWallMillis); put("exitDeadline", s.exitDeadlineWallMillis)
        put("completedAt", s.completedAtWallMillis); put("schema", s.schemaVersion)
    }

    fun fromJson(o: JSONObject): SessionRecord = SessionRecord(
        id = o.getString("id"),
        mode = SessionMode.valueOf(o.getString("mode")),
        status = SessionStatus.valueOf(o.getString("status")),
        profileName = o.optString("profileName", ""),
        durationMillis = o.getLong("duration"),
        startWallMillis = o.getLong("startWall"),
        startElapsedMillis = o.getLong("startElapsed"),
        startBootWallMillis = o.optLong("bootCount", -1L),
        endWallMillis = o.getLong("endWall"),
        delaySeconds = o.optInt("delay", 5),
        coolingOff = try { CoolingOff.valueOf(o.optString("cooling", "HOURS_2")) } catch (e: Exception) { CoolingOff.HOURS_2 },
        configSnapshotJson = o.optString("snapshot", "{}"),
        exitRequestedAtWallMillis = o.optLong("exitReq", 0L),
        exitDeadlineWallMillis = o.optLong("exitDeadline", 0L),
        completedAtWallMillis = o.optLong("completedAt", 0L),
        schemaVersion = o.optInt("schema", 1)
    )
}
