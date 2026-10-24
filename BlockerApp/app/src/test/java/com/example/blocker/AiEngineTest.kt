package com.example.blocker

import com.example.blocker.core.AiEngine
import com.example.blocker.core.AiIntention
import com.example.blocker.core.AiTestDataset
import com.example.blocker.core.AiVerdict
import com.example.blocker.core.DecisionCache
import com.example.blocker.core.DecisionPolicy
import com.example.blocker.core.KeywordRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiEngineTest {

    private val gambling = KeywordRule(id = "r1", text = "قمار")

    private fun classify(
        text: String,
        policy: DecisionPolicy = DecisionPolicy.BALANCED,
        aiEnabled: Boolean = true,
        cache: DecisionCache? = null
    ) = AiEngine.classify(
        listOf(gambling), text, null, emptySet(), policy, aiEnabled, cache, System.currentTimeMillis()
    )

    @Test fun exactBlocksInAllPolicies() {
        for (policy in DecisionPolicy.values()) {
            assertEquals(policy.name, AiVerdict.BLOCK, classify("قمار", policy).verdict)
        }
    }

    @Test fun conservativeRelatedPairIsUncertain() {
        val d = classify("کازینو و پوکر", DecisionPolicy.CONSERVATIVE)
        assertEquals(AiVerdict.UNCERTAIN, d.verdict)
    }

    @Test fun balancedRelatedPairBlocks() {
        assertEquals(AiVerdict.BLOCK, classify("کازینو و پوکر", DecisionPolicy.BALANCED).verdict)
    }

    @Test fun strictSingleRelatedIsUncertain() {
        assertEquals(AiVerdict.UNCERTAIN, classify("کازینو", DecisionPolicy.STRICT).verdict)
    }

    @Test fun strictFuzzyD2IsUncertain() {
        assertEquals(AiVerdict.UNCERTAIN, classify("قمازا", DecisionPolicy.STRICT).verdict)
    }

    @Test fun balancedFuzzyD2IsAllow() {
        assertEquals(AiVerdict.ALLOW, classify("قمازا", DecisionPolicy.BALANCED).verdict)
    }

    @Test fun aiDisabledExactOnly() {
        assertEquals(AiVerdict.ALLOW, classify("کازینو و پوکر", aiEnabled = false).verdict)
        assertEquals(AiVerdict.BLOCK, classify("قمار", aiEnabled = false).verdict)
    }

    @Test fun cacheSecondHitIsFromCache() {
        val cache = DecisionCache()
        val first = classify("قمار", cache = cache)
        val second = classify("قمار", cache = cache)
        assertFalse(first.fromCache)
        assertTrue(second.fromCache)
        assertEquals(first.verdict, second.verdict)
    }

    @Test fun cacheInvalidatedByPolicyChange() {
        val cache = DecisionCache()
        val balanced = classify("کازینو و پوکر", DecisionPolicy.BALANCED, cache = cache)
        assertEquals(AiVerdict.BLOCK, balanced.verdict)
        val conservative = classify("کازینو و پوکر", DecisionPolicy.CONSERVATIVE, cache = cache)
        assertFalse(conservative.fromCache)
        assertEquals(AiVerdict.UNCERTAIN, conservative.verdict)
    }

    @Test fun emptyAndMalformedNeverThrow() {
        assertEquals(AiVerdict.ALLOW, classify("").verdict)
        assertEquals(AiVerdict.ALLOW, classify("   ").verdict)
        assertEquals(AiVerdict.BLOCK, classify("قمار ".repeat(1000)).verdict)
    }

    @Test fun recoveryVeto() {
        assertEquals(AiVerdict.ALLOW, classify("ترک قمار").verdict)
    }

    @Test fun cacheExpires() {
        val cache = DecisionCache(ttlMillis = 1000L)
        cache.put("k", classify("قمار"), 1000L)
        assertTrue(cache.get("k", 1500L) != null)
        assertTrue(cache.get("k", 3000L) == null)
    }
}

class AiTestDatasetTest {

    private fun classifyBalanced(text: String) = AiEngine.classify(
        AiTestDataset.rules, text, null, emptySet(),
        DecisionPolicy.BALANCED, true, null, System.currentTimeMillis()
    )

    @Test fun datasetMetricsAreReal() {
        val m = AiTestDataset.evaluate(::classifyBalanced)
        assertEquals(AiTestDataset.cases.size, m.total)
        assertEquals(25, m.correct)
        assertEquals(2, m.falsePositives)
        assertEquals(0, m.falseNegatives)
        assertEquals(1, m.uncertainPredicted)
        assertTrue("precision=${m.precision}", m.precision >= 0.80 && m.precision <= 0.83)
        assertEquals(1.0, m.recall, 0.001)
    }

    @Test fun intentionJsonRoundTrip() {
        val original = AiIntention(
            name = "دوری از قمار",
            description = "توضیح",
            blockExamples = listOf("قمار", "کازینو و پوکر"),
            allowExamples = listOf("ترک قمار")
        )
        val restored = AiIntention.fromJsonString(original.toJsonString())
        assertEquals(original, restored)
        // ورودی ناقص/تهی/خراب → نیت خالی، نه سقوط.
        assertEquals(AiIntention(), AiIntention.fromJsonString(null))
        assertEquals(AiIntention(), AiIntention.fromJsonString("{bad"))
        assertEquals("فقط نام", AiIntention.fromJsonString("{\"name\":\"فقط نام\"}").name)
    }
}
