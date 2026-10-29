package com.example.blocker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIntentPolicyTest {
    @Test fun ordinaryEditableFieldIsNotSearchWithoutEvidence() {
        assertFalse(SearchIntentPolicy.shouldInspectInput(
            hasEditableText = true,
            isSearchField = false,
            temporarySearchContext = false
        ))
    }

    @Test fun explicitSearchFieldCanBeInspected() {
        assertTrue(SearchIntentPolicy.shouldInspectInput(
            hasEditableText = true,
            isSearchField = true,
            temporarySearchContext = false
        ))
    }

    @Test fun temporaryContextOnlyAppliesToEditableText() {
        assertTrue(SearchIntentPolicy.shouldInspectInput(true, false, true))
        assertFalse(SearchIntentPolicy.shouldInspectInput(false, false, true))
    }

    @Test fun queryRevalidationUsesPersianNormalization() {
        assertTrue(SearchIntentPolicy.sameQuery("كلمه‌ ممنوع", "کلمه ممنوع"))
        assertFalse(SearchIntentPolicy.sameQuery("جستجوی قبلی", "جستجوی جدید"))
        assertFalse(SearchIntentPolicy.sameQuery(null, "query"))
        assertFalse(SearchIntentPolicy.sameQuery("", "query"))
    }
}
