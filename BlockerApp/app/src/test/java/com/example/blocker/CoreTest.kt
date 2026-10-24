package com.example.blocker

import com.example.blocker.core.BackupValidator
import com.example.blocker.core.CoolingOff
import com.example.blocker.core.DurationRange
import com.example.blocker.core.KeywordMatcher
import com.example.blocker.core.KeywordRule
import com.example.blocker.core.OwnAppGuard
import com.example.blocker.core.PendingBlockManager
import com.example.blocker.core.RuleDelays
import com.example.blocker.core.ScheduleCalculator
import com.example.blocker.core.SelfProtection
import com.example.blocker.core.SessionMode
import com.example.blocker.core.SessionRecord
import com.example.blocker.core.SessionSchedule
import com.example.blocker.core.SessionStatus
import com.example.blocker.core.SessionTimeCalculator
import com.example.blocker.core.TextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {
    @Test fun arabicLettersBecomePersian() {
        assertEquals("یکی کلمه", TextNormalizer.normalize("يكي كلمة"))
    }
    @Test fun digitsUnified() {
        assertEquals("123 456", TextNormalizer.normalize("١٢٣ ۴۵۶"))
    }
    @Test fun diacriticsAndTatweelRemoved() {
        assertEquals("سلام", TextNormalizer.normalize("سَلامـ"))
    }
    @Test fun zwnjBecomesSpaceAndSpacesCollapse() {
        assertEquals("می روم خانه", TextNormalizer.normalize("می‌روم   خانه"))
    }
    @Test fun latinLowercased() {
        assertEquals("instagram", TextNormalizer.normalize("InStAgRaM"))
    }
}

class KeywordMatcherTest {
    private fun rule(text: String, whole: Boolean = false, allow: Boolean = false, enabled: Boolean = true) =
        KeywordRule(id = "r-$text", text = text, wholeWord = whole, isAllow = allow, enabled = enabled)

    @Test fun wholeWordAlwaysApplies() {
        // «فوت» نباید «فوتبال» را بگیرد — حتی وقتی پرچم wholeWord خاموش است؛
        // تطبیق همیشه مرزی است. ولی کلمه‌ی مستقل و کلمه‌ی کنار نشانه‌گذاری می‌گیرد.
        assertNull(KeywordMatcher.match(rule("فوت"), "فوتبال"))
        assertNotNull(KeywordMatcher.match(rule("فوت"), "من فوت کردم"))
        assertNotNull(KeywordMatcher.match(rule("فوت"), "فوت، بازی"))
    }
    @Test fun wholeWordRejectsInsideWord() {
        assertNull(KeywordMatcher.match(rule("قمار", whole = true), "قماربازی"))
    }
    @Test fun wholeWordAcceptsStandalone() {
        assertNotNull(KeywordMatcher.match(rule("قمار", whole = true), "این قمار است"))
    }
    @Test fun persianVariantsMatch() {
        assertNotNull(KeywordMatcher.match(rule("کلمه یکی"), "اين كلمه يكي است"))
    }
    @Test fun disabledRuleNeverMatches() {
        assertNull(KeywordMatcher.match(rule("x", enabled = false), "x"))
    }
    @Test fun allowlistOverridesBlock() {
        val rules = listOf(rule("example"), rule("example.com/help", allow = true))
        assertNull(KeywordMatcher.firstBlockMatch(rules, "visit example.com/help now"))
        assertNotNull(KeywordMatcher.firstBlockMatch(rules, "visit example.com now"))
    }
    @Test fun targetPackageScoping() {
        val scoped = KeywordRule(id = "s", text = "secret", targetPackages = setOf("com.chat"))
        assertNull(KeywordMatcher.firstBlockMatch(listOf(scoped), "a secret here", packageName = "com.other"))
        assertNotNull(KeywordMatcher.firstBlockMatch(listOf(scoped), "a secret here", packageName = "com.chat"))
    }
}

class SessionTimeCalculatorTest {
    private fun session(duration: Long = 60_000L, startWall: Long = 1_000_000L, startElapsed: Long = 500_000L) =
        SessionRecord(
            id = "s1", mode = SessionMode.STRICT, status = SessionStatus.ACTIVE,
            profileName = "test", durationMillis = duration,
            startWallMillis = startWall, startElapsedMillis = startElapsed,
            startBootWallMillis = startWall, endWallMillis = startWall + duration,
            delaySeconds = 5, coolingOff = CoolingOff.HOURS_2, configSnapshotJson = "{}"
        )

    @Test fun sameBootUsesMonotonic() {
        val s = session()
        // ساعت دیواری ۱۰ دقیقه جلو پریده ولی monotonic فقط ۲۰ ثانیه گذشته → نباید سشن تمام شده باشد
        val remaining = SessionTimeCalculator.remainingMillis(s, nowWallMillis = 1_600_000L, nowElapsedMillis = 520_000L, sameBoot = true)
        assertEquals(40_000L, remaining)
    }
    @Test fun afterRebootUsesWallClock() {
        val s = session()
        val remaining = SessionTimeCalculator.remainingMillis(s, nowWallMillis = 1_050_000L, nowElapsedMillis = 10_000L, sameBoot = false)
        assertEquals(10_000L, remaining)
    }
    @Test fun expiredAfterRebootBeyondEnd() {
        val s = session()
        assertTrue(SessionTimeCalculator.isExpired(s, nowWallMillis = 2_000_000L, nowElapsedMillis = 10_000L, sameBoot = false))
    }
    @Test fun exitReadyOnlyAfterDeadline() {
        val s = session().copy(status = SessionStatus.EXIT_PENDING, exitRequestedAtWallMillis = 2_000_000L,
            exitDeadlineWallMillis = SessionTimeCalculator.exitDeadline(session(), 2_000_000L))
        assertFalse(SessionTimeCalculator.exitReady(s, 2_000_000L + CoolingOff.HOURS_2.millis - 1))
        assertTrue(SessionTimeCalculator.exitReady(s, 2_000_000L + CoolingOff.HOURS_2.millis))
    }
    @Test fun duplicateCompletionGuardData() {
        val s = session()
        assertEquals(s.durationMillis, s.endWallMillis - s.startWallMillis)
    }
}

class PendingBlockManagerTest {
    @Test fun oldTaskCannotFireOnNewContext() {
        val m = PendingBlockManager()
        m.schedule("sig-a", "r1", nowMillis = 0L, delaySeconds = 5)
        assertFalse(m.consumeIfDue(nowMillis = 6_000L, currentSignature = "sig-b"))
        assertNull(m.current())
    }
    @Test fun firesOnceWhenDue() {
        val m = PendingBlockManager()
        m.schedule("sig-a", "r1", nowMillis = 0L, delaySeconds = 5)
        assertFalse(m.consumeIfDue(nowMillis = 4_999L, currentSignature = "sig-a"))
        assertTrue(m.consumeIfDue(nowMillis = 5_000L, currentSignature = "sig-a"))
        assertFalse(m.consumeIfDue(nowMillis = 6_000L, currentSignature = "sig-a"))
    }
    @Test fun scheduleSameSignatureIsIdempotent() {
        val m = PendingBlockManager()
        val p1 = m.schedule("sig-a", "r1", nowMillis = 0L, delaySeconds = 5)
        val p2 = m.schedule("sig-a", "r1", nowMillis = 2_000L, delaySeconds = 5)
        assertEquals(p1.fireAtMillis, p2.fireAtMillis)
    }
    @Test fun cancelOnContextChange() {
        val m = PendingBlockManager()
        m.schedule("sig-a", "r1", nowMillis = 0L, delaySeconds = 5)
        m.cancelIfDifferent("sig-b")
        assertNull(m.current())
    }
}

class BackupValidatorTest {
    @Test fun validBackupPasses() {
        val data = mapOf<String, Any?>(
            "schemaVersion" to 1,
            "keywords" to listOf(mapOf("text" to "قمار")),
            "websites" to listOf("example.com"),
            "apps" to listOf("com.example.app"),
            "delaySeconds" to 5
        )
        assertTrue(BackupValidator.validate(data).isEmpty())
    }
    @Test fun badSchemaAndDomainRejected() {
        val data = mapOf<String, Any?>(
            "schemaVersion" to 99,
            "websites" to listOf("not a domain")
        )
        assertTrue(BackupValidator.validate(data).size >= 2)
    }
}

class ScheduleCalculatorTest {
    @Test fun oneTimeLaterToday() {
        val s = SessionSchedule(id = "1", profileId = "p", hour = 20, minute = 0)
        val startOfToday = 1_000_000L
        val now = startOfToday + 10L * 3600_000L
        assertEquals(startOfToday + 20L * 3600_000L, ScheduleCalculator.nextTriggerMillis(s, now, startOfToday, 2))
    }
    @Test fun oneTimePassedGoesTomorrow() {
        val s = SessionSchedule(id = "1", profileId = "p", hour = 8, minute = 0)
        val startOfToday = 1_000_000L
        val now = startOfToday + 10L * 3600_000L
        assertEquals(startOfToday + 24L * 3600_000L + 8L * 3600_000L, ScheduleCalculator.nextTriggerMillis(s, now, startOfToday, 2))
    }
    @Test fun overlapDetection() {
        assertTrue(ScheduleCalculator.overlaps(0, 100, 50, 100))
        assertFalse(ScheduleCalculator.overlaps(0, 100, 100, 100))
    }
}

/**
 * تست‌های ایمنی گارد: گارد باید فقط صفحه‌ی جزئیاتِ سرویسِ خودمان را بگیرد و
 * هر صفحه‌ی دیگری را رها کند — مثبت کاذب یعنی قفل کردن کاربر بیرون از تنظیمات.
 */
class SelfProtectionGuardTest {
    private val pkg = "com.example.blocker"
    private val label = "سد"
    private val marker = SelfProtection.DESCRIPTION_MARKER

    private fun settingsPage(text: String, hasSwitch: Boolean) =
        SelfProtection.shouldGuardSettingsPage(true, true, "com.android.settings", pkg, label, marker, text, hasSwitch)

    @Test fun detailPageGuarded() {
        // صفحه‌ی جزئیات سرویس ما: لیبل + توضیح + Switch
        val text = "سد این سرویس برای مسدود کردن برنامه‌ها، سایت‌ها و کلمات کلیدی مورد نظر شما استفاده می‌شود. نسخه ۲٫۰"
        assertTrue(settingsPage(text, hasSwitch = true))
    }

    @Test fun guardEffectiveInStrictEvenIfToggleOff() {
        // Strict یعنی حداکثر محافظت برای همان سشن، حتی اگر کلید خاموش بوده باشد.
        assertTrue(SelfProtection.isGuardEffective(persistedOn = false, strictActive = true))
        assertTrue(SelfProtection.isGuardEffective(persistedOn = true, strictActive = false))
        assertFalse(SelfProtection.isGuardEffective(persistedOn = false, strictActive = false))
    }

    @Test fun keywordScopeListAndInput() {
        // ظرف‌های لیستی (تاریخچه، پیشنهادها، فید) متن‌شان برای تطبیق حساب نمی‌شود.
        assertTrue(com.example.blocker.core.KeywordScope.isListContainerClass("androidx.recyclerview.widget.RecyclerView"))
        assertTrue(com.example.blocker.core.KeywordScope.isListContainerClass("android.widget.ListView"))
        assertTrue(com.example.blocker.core.KeywordScope.isListContainerClass("android.widget.GridView"))
        assertFalse(com.example.blocker.core.KeywordScope.isListContainerClass("android.webkit.WebView"))
        assertFalse(com.example.blocker.core.KeywordScope.isListContainerClass("android.widget.TextView"))
        // فیلدهای ورودی (جستجو/آدرس) حساب می‌شوند.
        assertTrue(com.example.blocker.core.KeywordScope.isInputNode("android.widget.EditText", false))
        assertTrue(com.example.blocker.core.KeywordScope.isInputNode("android.view.View", true))
        assertFalse(com.example.blocker.core.KeywordScope.isInputNode("android.widget.TextView", false))
    }

    @Test fun accessibilityListPageNotGuarded() {
        // لیست سرویس‌ها: لیبل ما هست ولی توضیح کامل و Switch سرویس ما نیست
        val text = "دسترسی‌پذیری TalkBack سد منوی دسترسی"
        assertFalse(settingsPage(text, hasSwitch = false))
    }

    @Test fun listPageWithOtherSwitchesNotGuarded() {
        // لیست سامسونگ برای هر سرویس یک Switch دارد؛ بدون توضیح ما نباید بگیرد
        val text = "دسترسی‌پذیری TalkBack سد"
        assertFalse(settingsPage(text, hasSwitch = true))
    }

    @Test fun detailPageWithoutSwitchNotGuarded() {
        val text = "سد این سرویس برای مسدود کردن برنامه‌ها استفاده می‌شود."
        assertFalse(settingsPage(text, hasSwitch = false))
    }

    @Test fun settingsHomeNotGuarded() {
        assertFalse(settingsPage("تنظیمات شبکه و اینترنت نمایش صدا", hasSwitch = false))
    }

    @Test fun otherServiceDetailPageNotGuarded() {
        val text = "TalkBack این سرویس برای خواندن صفحه استفاده می‌شود."
        assertFalse(settingsPage(text, hasSwitch = true))
    }

    @Test fun labelSubstringNotGuarded() {
        // «سدها» نباید با مرز کلمه با «سد» اشتباه شود
        val text = "سدها این سرویس برای مسدود کردن برنامه‌ها استفاده می‌شود."
        assertFalse(settingsPage(text, hasSwitch = true))
    }

    @Test fun noSessionNotGuarded() {
        val text = "سد این سرویس برای مسدود کردن برنامه‌ها استفاده می‌شود."
        assertFalse(SelfProtection.shouldGuardSettingsPage(false, true, "com.android.settings", pkg, label, marker, text, true))
    }

    @Test fun prefOffNotGuarded() {
        val text = "سد این سرویس برای مسدود کردن برنامه‌ها استفاده می‌شود."
        assertFalse(SelfProtection.shouldGuardSettingsPage(true, false, "com.android.settings", pkg, label, marker, text, true))
    }

    @Test fun wrongPackageNotGuarded() {
        val text = "سد این سرویس برای مسدود کردن برنامه‌ها استفاده می‌شود."
        assertFalse(SelfProtection.shouldGuardSettingsPage(true, true, "com.android.systemui", pkg, label, marker, text, true))
    }

    @Test fun ownPackageForegroundNotGuarded() {
        val text = "سد این سرویس برای مسدود کردن برنامه‌ها استفاده می‌شود."
        assertFalse(SelfProtection.shouldGuardSettingsPage(true, true, pkg, pkg, label, marker, text, true))
    }

    @Test fun installerUninstallDialogGuarded() {
        assertTrue(SelfProtection.shouldGuardInstallerDialog(true, "com.android.packageinstaller", pkg, label, "آیا می‌خواهید سد را حذف کنید؟"))
    }

    @Test fun installerDialogOtherAppNotGuarded() {
        assertFalse(SelfProtection.shouldGuardInstallerDialog(true, "com.android.packageinstaller", pkg, label, "آیا می‌خواهید تلگرام را حذف کنید؟"))
    }

    @Test fun installerDialogNoSessionGuarded() {
        // گارد دیالوگ سشن نمی‌خواهد: کلید روشن کافی است، حتی بدون سشن.
        assertTrue(SelfProtection.shouldGuardInstallerDialog(true, "com.android.packageinstaller", pkg, label, "آیا می‌خواهید سد را حذف کنید؟"))
    }

    @Test fun installerDialogSamsungPackageGuarded() {
        assertTrue(SelfProtection.shouldGuardInstallerDialog(true, "com.samsung.android.packageinstaller", pkg, label, "Uninstall سد؟"))
    }

    @Test fun installerDialogWithoutUninstallWordNotGuarded() {
        // فقط لیبل، بدون کلمه‌ی حذف: دیالوگ حذف نیست.
        assertFalse(SelfProtection.shouldGuardInstallerDialog(true, "com.android.packageinstaller", pkg, label, "سد نسخه ۲٫۰"))
    }

    @Test fun installerDialogPrefOffNotGuarded() {
        assertFalse(SelfProtection.shouldGuardInstallerDialog(false, "com.android.packageinstaller", pkg, label, "آیا می‌خواهید سد را حذف کنید؟"))
    }

    @Test fun forceStopDialogWithLabelGuarded() {
        // دیالوگ سامسونگی که نام اپ را دارد؛ فقط کلمه‌ی توقف، نه حذف.
        assertTrue(SelfProtection.shouldGuardForceStopDialog(true, "com.android.settings", pkg, label, "توقف اجباری سد؟ اگر برنامه را متوقف کنید، ممکن است درست کار نکند.", false))
    }

    @Test fun forceStopDialogWithFlagNoLabelGuarded() {
        // دیالوگ AOSP نام اپ را ندارد؛ پرچمِ تازه‌ی صفحه‌ی اطلاعات اپ جای لیبل را می‌گیرد.
        assertTrue(SelfProtection.shouldGuardForceStopDialog(true, "com.android.settings", pkg, label, "Force stop? If you force stop an app, it may misbehave.", true))
    }

    @Test fun forceStopDialogStaleFlagNoLabelNotGuarded() {
        assertFalse(SelfProtection.shouldGuardForceStopDialog(true, "com.android.settings", pkg, label, "Force stop? If you force stop an app, it may misbehave.", false))
    }

    @Test fun appInfoPageNotGuardedByDialogGuards() {
        // صفحه‌ی اطلاعات اپ هر دو کلمه را دارد و باید از هر دو گارد دیالوگ کنار گذاشته شود.
        val page = "سد حذف نصب توقف اجباری باز کردن غیرفعال کردن حافظه مجوزها"
        assertFalse(SelfProtection.shouldGuardForceStopDialog(true, "com.android.settings", pkg, label, page, true))
        assertFalse(SelfProtection.shouldGuardSettingsUninstallDialog(true, "com.android.settings", pkg, label, page, true))
    }

    @Test fun settingsUninstallDialogGuarded() {
        assertTrue(SelfProtection.shouldGuardSettingsUninstallDialog(true, "com.android.settings", pkg, label, "حذف سد؟ لغو حذف", false))
    }

    @Test fun ownAppInfoPageDetection() {
        assertTrue(SelfProtection.isOwnAppInfoPage("سد حذف نصب توقف اجباری", pkg, label))
        assertFalse(SelfProtection.isOwnAppInfoPage("تنظیمات شبکه و اینترنت", pkg, label))
        // صفحه‌ی اطلاعات اپِ دیگر لیبل ما را ندارد.
        assertFalse(SelfProtection.isOwnAppInfoPage("تلگرام حذف نصب توقف اجباری", pkg, label))
    }

    @Test fun flagFreshnessWindow() {
        val now = 1_000_000L
        assertTrue(SelfProtection.isFlagFresh(now - 60_000L, now))
        assertFalse(SelfProtection.isFlagFresh(now - 400_000L, now))
        assertFalse(SelfProtection.isFlagFresh(0L, now))
    }

    @Test fun serviceEnabledInListForms() {
        assertTrue(SelfProtection.isServiceEnabledInList("com.example.blocker/.BlockAccessibilityService", pkg, ".BlockAccessibilityService"))
        assertTrue(SelfProtection.isServiceEnabledInList("com.example.blocker/com.example.blocker.BlockAccessibilityService", pkg, ".BlockAccessibilityService"))
        assertTrue(SelfProtection.isServiceEnabledInList("com.other/.TalkBack:com.example.blocker/.BlockAccessibilityService", pkg, ".BlockAccessibilityService"))
        assertFalse(SelfProtection.isServiceEnabledInList("com.other/.TalkBack", pkg, ".BlockAccessibilityService"))
        assertFalse(SelfProtection.isServiceEnabledInList("", pkg, ".BlockAccessibilityService"))
        assertFalse(SelfProtection.isServiceEnabledInList(null, pkg, ".BlockAccessibilityService"))
    }
}

class DurationRangeTest {
    @Test fun bounds() {
        assertTrue(DurationRange.isValid(30_000L))
        assertTrue(DurationRange.isValid(90L * 24L * 60L * 60L * 1000L))
        assertTrue(DurationRange.isValid(25 * 60_000L))
        assertFalse(DurationRange.isValid(29_999L))
        assertFalse(DurationRange.isValid(90L * 24L * 60L * 60L * 1000L + 1L))
        assertFalse(DurationRange.isValid(0L))
        assertFalse(DurationRange.isValid(-1_000L))
    }
}

/**
 * نگهبان پنجره‌ی خودمان: متن خودِ اپ هرگز نباید بلاک شود، حتی وقتی رخداد
 * با پکیج دیگری (مثل صفحه‌کلید) می‌رسد.
 */
class OwnAppGuardTest {
    private val own = "com.example.blocker"

    @Test fun eventFromOwnAppSkipped() {
        assertTrue(OwnAppGuard.shouldSkip(own, "com.other.app", own))
    }

    @Test fun foreignEventWithOwnActiveWindowSkipped() {
        // سناریوی باگ گزارش‌شده: کاربر داخل خودِ اپ است و رخداد از صفحه‌کلید می‌آید.
        assertTrue(OwnAppGuard.shouldSkip("com.google.android.inputmethod.latin", own, own))
    }

    @Test fun foreignEventWithForeignWindowProcessed() {
        assertFalse(OwnAppGuard.shouldSkip("com.other.app", "com.other.app", own))
    }

    @Test fun nullWindowWithForeignEventProcessed() {
        assertFalse(OwnAppGuard.shouldSkip("com.other.app", null, own))
    }

    @Test fun nullEventWithOwnWindowSkipped() {
        assertTrue(OwnAppGuard.shouldSkip(null, own, own))
    }
}

class RuleDelaysTest {
    @Test fun sanitize() {
        assertEquals(RuleDelays.FOLLOW_GLOBAL, RuleDelays.sanitize(RuleDelays.FOLLOW_GLOBAL))
        assertEquals(0, RuleDelays.sanitize(0))
        assertEquals(5, RuleDelays.sanitize(5))
        assertEquals(RuleDelays.FOLLOW_GLOBAL, RuleDelays.sanitize(6))
        assertEquals(RuleDelays.FOLLOW_GLOBAL, RuleDelays.sanitize(10))
        assertEquals(RuleDelays.FOLLOW_GLOBAL, RuleDelays.sanitize(-2))
    }

    @Test fun labels() {
        assertEquals("دیلی سراسری", RuleDelays.labelFa(RuleDelays.FOLLOW_GLOBAL))
        assertEquals("فوری", RuleDelays.labelFa(0))
        assertEquals("3 ثانیه", RuleDelays.labelFa(3))
    }

    @Test fun effectiveDelay() {
        val followGlobal = KeywordRule(id = "1", text = "x")
        assertEquals(5, followGlobal.effectiveDelaySeconds(5))
        val immediate = KeywordRule(id = "2", text = "y", delaySeconds = 0)
        assertEquals(0, immediate.effectiveDelaySeconds(5))
        val three = KeywordRule(id = "3", text = "z", delaySeconds = 3)
        assertEquals(3, three.effectiveDelaySeconds(5))
    }
}
