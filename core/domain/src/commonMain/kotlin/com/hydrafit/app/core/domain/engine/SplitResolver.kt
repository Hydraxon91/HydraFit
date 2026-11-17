package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.MovementPattern

/**
 * Resolves a [SplitType] preference into a concrete per-day focus sequence. Shared by the
 * deterministic engine (to pick movement-pattern templates) and the model-backed engines (to steer
 * each day toward one focus instead of letting the model repeat a single focus all week).
 */
object SplitResolver {

    private val AUTO_FIVE_DAY = listOf(
        SplitFocus.UPPER,
        SplitFocus.LOWER,
        SplitFocus.PUSH,
        SplitFocus.PULL,
        SplitFocus.LEGS
    )

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

    /**
     * The focus for every day, cycling the split's foci up to [daysPerWeek]. A five-day Auto week is
     * the one exception to plain cycling: a Push/Pull/Legs cycle would train legs once and push and
     * pull twice, so it uses [AUTO_FIVE_DAY], which trains the upper and lower body twice and
     * keeps a dedicated push, pull and legs day. An explicitly chosen split is never rewritten.
     */
    fun focusSequence(preference: SplitType, daysPerWeek: Int): List<SplitFocus> {
        if (preference == SplitType.AUTO && daysPerWeek == AUTO_FIVE_DAY.size) return AUTO_FIVE_DAY
        val cycle = focusCycle(resolveSplitType(preference, daysPerWeek))
        return List(daysPerWeek) { index -> cycle[index % cycle.size] }
    }

    /** Compound movement families for a focus, shared with candidate eligibility. */
    fun compoundGroups(focus: SplitFocus): List<List<MovementPattern>> = when (focus) {
        SplitFocus.PUSH -> listOf(
            listOf(MovementPattern.HORIZONTAL_PUSH),
            listOf(MovementPattern.VERTICAL_PUSH)
        )
        SplitFocus.PULL -> listOf(
            listOf(MovementPattern.VERTICAL_PULL),
            listOf(MovementPattern.HORIZONTAL_PULL)
        )
        SplitFocus.LEGS, SplitFocus.LOWER -> listOf(
            listOf(MovementPattern.SQUAT, MovementPattern.LUNGE),
            listOf(MovementPattern.HINGE)
        )
        SplitFocus.UPPER -> listOf(
            listOf(MovementPattern.HORIZONTAL_PUSH),
            listOf(MovementPattern.VERTICAL_PUSH),
            listOf(MovementPattern.HORIZONTAL_PULL),
            listOf(MovementPattern.VERTICAL_PULL)
        )
        SplitFocus.FULL_BODY -> listOf(
            listOf(MovementPattern.SQUAT, MovementPattern.HINGE, MovementPattern.LUNGE),
            listOf(MovementPattern.HORIZONTAL_PUSH, MovementPattern.VERTICAL_PUSH),
            listOf(MovementPattern.HORIZONTAL_PULL, MovementPattern.VERTICAL_PULL)
        )
    }
}
