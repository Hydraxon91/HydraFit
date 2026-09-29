package com.hydrafit.app.core.domain.engine

/**
 * Resolves a [SplitType] preference into a concrete per-day focus sequence. Shared by the
 * deterministic engine (to pick movement-pattern templates) and the model-backed engines (to steer
 * each day toward one focus instead of letting the model repeat a single focus all week).
 */
object SplitResolver {

    /** The split a preference resolves to for a given training frequency. */
    fun resolveSplitType(preference: SplitType, daysPerWeek: Int): SplitType = when (preference) {
        SplitType.AUTO -> when (daysPerWeek) {
            2, 3 -> SplitType.FULL_BODY
            4 -> SplitType.UPPER_LOWER
            else -> SplitType.PUSH_PULL_LEGS
        }
        else -> preference
    }

    /** The repeating focus cycle for a split, e.g. PUSH, PULL, LEGS. */
    fun focusCycle(splitType: SplitType): List<SplitFocus> = when (splitType) {
        SplitType.FULL_BODY -> listOf(SplitFocus.FULL_BODY)
        SplitType.UPPER_LOWER -> listOf(SplitFocus.UPPER, SplitFocus.LOWER)
        SplitType.PUSH_PULL_LEGS -> listOf(SplitFocus.PUSH, SplitFocus.PULL, SplitFocus.LEGS)
        SplitType.AUTO -> listOf(SplitFocus.FULL_BODY)
    }

    /** The focus for every day, cycling the split's foci up to [daysPerWeek]. */
    fun focusSequence(preference: SplitType, daysPerWeek: Int): List<SplitFocus> {
        val cycle = focusCycle(resolveSplitType(preference, daysPerWeek))
        return List(daysPerWeek) { index -> cycle[index % cycle.size] }
    }
}
