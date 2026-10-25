package com.example.blocker

import com.example.blocker.core.BlockingDelays
import com.example.blocker.core.CoolingOff
import com.example.blocker.core.KeywordMatcher
import com.example.blocker.core.KeywordRule
import com.example.blocker.core.PendingBlockManager
import com.example.blocker.core.SemanticEngine
import com.example.blocker.core.SessionMode
import com.example.blocker.core.SessionRecord
import com.example.blocker.core.SessionStatus
import com.example.blocker.core.SessionTimeCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست‌های خلأ ممیزی نهایی: مقادیر دیلی، جایگزینی تایمر، تغییر ساعت دیواری،
 * تمدید سشن، قانون خالی، قوانین تکراری، و مهلت خروج Strict — همه خالص.
 */
class AuditGapTest {

    private fun session(
        duration: Long = 60_000L,
        startWall: Long = 1_000_000L,
        startElapsed: Long = 500_000L,
        status: SessionStatus = SessionStatus.ACTIVE,
        exitDeadline: Long = 0L
    ) = SessionRecord(
        id = "s1",
        mode = SessionMode.STRICT,
        status = status,
        profileName = "",
        durationMillis = duration,
        startWallMillis = startWall,
        startElapsedMillis = startElapsed,
        startBootWallMillis = -1L,
        endWallMillis = startWall + duration,
        delaySeconds = 5,
        coolingOff = CoolingOff.HOURS_2,
        configSnapshotJson = "{}",
        exitDeadlineWallMillis = exitDeadline
    )

    @Test fun allSupportedGlobalDelays() {
        // دیلی‌های سراسری پشتیبانی‌شده دقیقاً همان‌هایی‌اند که ممیزی خواسته است.
        assertEquals(listOf(0, 3, 5, 10, 15), BlockingDelays.ALLOWED_SECONDS)
        val mgr = PendingBlockManager()
        for (delay in BlockingDelays.ALLOWED_SECONDS) {
            val now = 10_000L
            val p = mgr.schedule("sig-$delay", "r1", now, delay)
            assertEquals(now + delay * 1000L, p.fireAtMillis)
            assertFalse(mgr.consumeIfDue(p.fireAtMillis - 1, "sig-$delay"))
            assertTrue(mgr.consumeIfDue(p.fireAtMillis, "sig-$delay"))
            assertFalse("second consume must not refire", mgr.consumeIfDue(p.fireAtMillis, "sig-$delay"))
        }
    }

    @Test fun differentSignatureReplacesPending() {
        val mgr = PendingBlockManager()
        mgr.schedule("sig-A", "r1", 10_000L, 5)
        mgr.schedule("sig-B", "r2", 11_000L, 3)
        assertEquals("sig-B", mgr.current()?.signature)
        assertFalse(mgr.consumeIfDue(20_000L, "sig-A"))
        assertNull(mgr.current())
    }

    @Test fun wallClockChangeDoesNotMoveRemainingWhenSameBoot() {
        val s = session()
        // ۱۰ ثانیه گذشته (elapsed)؛ ساعت دیواری یک ساعت عقب یا جلو رفته.
        val base = SessionTimeCalculator.remainingMillis(s, 1_000_000L, 510_000L, true)
        assertEquals(50_000L, base)
        assertEquals(base, SessionTimeCalculator.remainingMillis(s, 1_000_000L - 3_600_000L, 510_000L, true))
        assertEquals(base, SessionTimeCalculator.remainingMillis(s, 1_000_000L + 3_600_000L, 510_000L, true))
    }

    @Test fun extensionArithmetic() {
        val s = session(duration = 60_000L)
        val extended = s.copy(durationMillis = 75_000L, endWallMillis = s.endWallMillis + 15_000L)
        assertEquals(65_000L, SessionTimeCalculator.remainingMillis(extended, 1_000_000L, 510_000L, true))
    }

    @Test fun blankRuleNeverMatches() {
        val blank = KeywordRule(id = "b", text = "   ")
        assertNull(KeywordMatcher.match(blank, "قمار و هر چیز دیگر"))
        assertNull(KeywordMatcher.firstBlockMatch(listOf(blank), "قمار"))
        assertNull(SemanticEngine.firstSemanticMatch(listOf(blank), "قمار"))
    }

    @Test fun duplicateRulesAreDeterministic() {
        val a = KeywordRule(id = "first", text = "قمار")
        val b = KeywordRule(id = "second", text = "قمار")
        val m = KeywordMatcher.firstBlockMatch(listOf(a, b), "قمار کردم")
        assertEquals("first", m?.rule?.id)
        // ترتیب ورودی عوض شد؛ نتیجه باید قانون اولِ همان فهرست باشد.
        assertEquals("second", KeywordMatcher.firstBlockMatch(listOf(b, a), "قمار کردم")?.rule?.id)
    }

    @Test fun strictExitDeadline() {
        val s = session(status = SessionStatus.EXIT_PENDING, exitDeadline = 2_000_000L)
        assertFalse(SessionTimeCalculator.exitReady(s, 1_999_999L))
        assertTrue(SessionTimeCalculator.exitReady(s, 2_000_000L))
        assertTrue(SessionTimeCalculator.exitReady(s, 3_000_000L))
        val active = session(status = SessionStatus.ACTIVE, exitDeadline = 1_000_000L)
        assertFalse("non-pending session is never exit-ready", SessionTimeCalculator.exitReady(active, 9_000_000L))
        assertEquals(
            1_000_000L + CoolingOff.HOURS_2.millis,
            SessionTimeCalculator.exitDeadline(session(), 1_000_000L)
        )
    }
}
