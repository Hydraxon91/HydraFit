package com.hydrafit.app.feature.equipment

import com.hydrafit.app.core.domain.fatigue.MuscleGroup

/**
 * The in-progress numeric involvement edit for one muscle. The draft is ephemeral: it exists only
 * while the per-muscle dialog is open, never persists on its own, and Cancel discards it without
 * touching the exercise's involvement map.
 */
data class InvolvementEditorState(
    val muscle: MuscleGroup,
    /** The exact text the editor opened with, so an untouched field keeps its stored double. */
    val initialInput: String,
    /** The stored weight the editor opened with, or null when the muscle had no contribution. */
    val origin: Double?,
    val input: String = initialInput
) {
    /** The parsed field value, or null when the text is not a number (including blank). */
    val enteredWeight: Double?
        get() = input.trim().toDoubleOrNull()

    /**
     * Blank text is invalid: removal is the explicit `0`/None choice, never an empty field. Any
     * other input must parse to a finite number within [MIN_WEIGHT, MAX_WEIGHT]; malformed,
     * non-finite and out-of-range text is rejected.
     */
    val isValid: Boolean
        get() {
            val value = enteredWeight ?: return false
            return value.isFinite() && value in MIN_WEIGHT..MAX_WEIGHT
        }

    /**
     * The weight Apply writes, or null when the input removes the muscle (`0`). An untouched field
     * returns [origin] exactly, so the stored double is not rounded through a text round-trip.
     * Callers must check [isValid] first; an invalid draft also yields null.
     */
    fun weightToApply(): Double? {
        if (!isValid) return null
        if (input == initialInput) return origin.withoutZero()
        return enteredWeight.withoutZero()
    }

    companion object {
        const val MIN_WEIGHT: Double = 0.0
        const val MAX_WEIGHT: Double = 1.0

        /** Shown for an unset muscle so None/zero is an explicit, valid choice rather than blank. */
        const val NONE_INPUT: String = "0"

        /** Seeds a draft from the muscle's current weight, showing `0` when it has no contribution. */
        fun forMuscle(muscle: MuscleGroup, weight: Double?): InvolvementEditorState {
            val text = weight?.toString() ?: NONE_INPUT
            return InvolvementEditorState(
                muscle = muscle,
                initialInput = text,
                origin = weight,
                input = text
            )
        }
    }
}

/** The named tier shortcuts offered alongside the numeric field, ascending. None means "remove". */
val INVOLVEMENT_TIERS: List<Double> = listOf(0.3, 0.5, 0.7, 1.0)

/** The canonical named tier for a stored weight; null means a custom, non-tier value. */
enum class InvolvementTier(val weight: Double) {
    LOW(0.3),
    MID(0.5),
    HIGH(0.7),
    PRIMARY(1.0)
}

internal fun involvementTierOf(weight: Double): InvolvementTier? =
    InvolvementTier.entries.firstOrNull { it.weight == weight }

/**
 * The value a muscle chip cycles to: unset→Low, then upward through the tiers, then None; an
 * already non-tier value cycles to None rather than being rounded onto the scale.
 */
internal fun nextInvolvementTier(current: Double?): Double? {
    if (current == null) return INVOLVEMENT_TIERS.first()
    val index = INVOLVEMENT_TIERS.indexOfFirst { it == current }
    return if (index == -1 || index == INVOLVEMENT_TIERS.lastIndex) {
        null
    } else {
        INVOLVEMENT_TIERS[index + 1]
    }
}

private fun Double?.withoutZero(): Double? = this?.takeUnless { it == 0.0 }
