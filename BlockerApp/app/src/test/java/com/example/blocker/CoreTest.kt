package com.example.blocker

import com.example.blocker.core.BackupValidator
import com.example.blocker.core.CoolingOff
import com.example.blocker.core.KeywordMatcher
import com.example.blocker.core.KeywordRule
import com.example.blocker.core.PendingBlockManager
import com.example.blocker.core.ScheduleCalculator
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

    @Test fun substringMatch() {
        assertNotNull(KeywordMatcher.match(rule("قمار"), "سایت قماربازی باز شد"))
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
