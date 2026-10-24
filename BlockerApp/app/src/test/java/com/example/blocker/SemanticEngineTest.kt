package com.example.blocker

import com.example.blocker.core.KeywordRule
import com.example.blocker.core.SemanticEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SemanticEngineTest {

    private fun rule(text: String, id: String = "r1") = KeywordRule(id = id, text = text)
    private fun match(text: String, rules: List<KeywordRule>, learned: Set<String> = emptySet()) =
        SemanticEngine.firstSemanticMatch(rules, text, null, learned)

    @Test fun stemStripsPersianSuffixes() {
        assertEquals("قمار", SemanticEngine.stem("قمارها"))
        assertEquals("قمار", SemanticEngine.stem("قمارباز"))
        assertEquals("قمار", SemanticEngine.stem("قماربازی"))
        assertEquals("کتاب", SemanticEngine.stem("کتابهایم"))
    }

    @Test fun stemKeepsShortWords() {
        assertEquals("بت", SemanticEngine.stem("بت"))
        assertEquals("گل", SemanticEngine.stem("گل"))
    }

    @Test fun stemCollapsesRepeatedLetters() {
        assertEquals("قمار", SemanticEngine.stem("قمممااار"))
    }

    @Test fun exactStemBlocks() {
        assertNotNull(match("من قمارها را دوست ندارم", listOf(rule("قمار"))))
    }

    @Test fun wholeWordStillHolds() {
        // «فوت» نباید «فوتبال» را بگیرد — حتی با موتور معنایی.
        assertNull(match("بازی فوتبال دیدم", listOf(rule("فوت"))))
    }

    @Test fun fuzzyTypoBlocks() {
        assertNotNull(match("سایت قماز", listOf(rule("قمار"))))
    }

    @Test fun fuzzyDoesNotApplyToShortWords() {
        // «بث» (فاصله‌ی ۱ از «بت») چون کوتاه است، با فازی بلاک نمی‌کند.
        assertNull(match("بث", listOf(rule("بت"))))
    }

    @Test fun singleRelatedWordNeverBlocks() {
        // «کازینو» تکی، بدون کلمه‌ی دقیق، زیر آستانه است.
        assertNull(match("رفتم کازینو", listOf(rule("قمار"))))
    }

    @Test fun relatedPairBlocks() {
        // دو کلمه‌ی مرتبط با هم یعنی موضوع همان موضوع است.
        assertNotNull(match("کازینو و پوکر", listOf(rule("قمار"))))
    }

    @Test fun recoveryContextVetoesExact() {
        // «ترک قمار» بحث درباره‌ی ترک است، نه خود قمار.
        assertNull(match("دارم ترک قمار می‌کنم", listOf(rule("قمار"))))
    }

    @Test fun recoveryContextDoesNotSaveStrongTopic() {
        // موضوع سنگین (دقیق + دو مرتبط = ۲٫۲) منهای یک کلمه‌ی ترک (۰٫۸) هنوز بالای آستانه است.
        assertNotNull(match("قمار کازینو پوکر درمان", listOf(rule("قمار"))))
    }

    @Test fun learnedAllowlistBlocksFutureMatches() {
        val learned = setOf("r1|${SemanticEngine.evidenceKey("قمار")}")
        assertNull(match("قمار", listOf(rule("قمار")), learned))
        // ولی قانون دیگر با همان کلمه هنوز کار می‌کند.
        assertNotNull(match("قمار", listOf(rule("قمار", "r2")), learned))
    }

    @Test fun phraseRuleStillExact() {
        assertNotNull(match("شرط بندی کردم", listOf(rule("شرط بندی"))))
        assertNull(match("شرط بستم", listOf(rule("شرط بندی"))))
    }

    @Test fun allowRuleStillWins() {
        val allow = KeywordRule(id = "a1", text = "آموزش", isAllow = true)
        assertNull(match("آموزش قمار", listOf(rule("قمار"), allow)))
    }
}
