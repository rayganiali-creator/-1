package com.example.blocker

import com.example.blocker.core.TextNormalizer

/**
 * Policy for deciding whether editable text is eligible for keyword evaluation.
 * UI metadata is evidence, not proof; temporary context is deliberately short-lived
 * and is consumed by the service after one editable text-change event.
 */
object SearchIntentPolicy {
    fun shouldInspectInput(
        hasEditableText: Boolean,
        isSearchField: Boolean,
        temporarySearchContext: Boolean
    ): Boolean = hasEditableText && (isSearchField || temporarySearchContext)

    /** Compare delayed candidates with the currently focused query without retaining raw text elsewhere. */
    fun sameQuery(current: String?, expected: String?): Boolean {
        if (current.isNullOrBlank() || expected.isNullOrBlank()) return false
        return TextNormalizer.normalize(current.take(2000)) ==
            TextNormalizer.normalize(expected.take(2000))
    }
}
