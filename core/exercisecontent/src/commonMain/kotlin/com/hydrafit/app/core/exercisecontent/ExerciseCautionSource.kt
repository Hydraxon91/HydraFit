package com.hydrafit.app.core.exercisecontent

import hydrafit.core.exercisecontent.generated.resources.Res
import hydrafit.core.exercisecontent.generated.resources.exercise_caution_conventional_deadlift
import hydrafit.core.exercisecontent.generated.resources.exercise_caution_dumbbell_rdl
import hydrafit.core.exercisecontent.generated.resources.exercise_caution_romanian_deadlift
import org.jetbrains.compose.resources.StringResource

/**
 * Bundled offline technique considerations for a small, sourced set of seeded exercises, keyed by
 * the catalog's stable exercise ID.
 *
 * This is app-owned informational content, not user data and not a risk score: it ships with the
 * app, is never written to the database, and is not part of a backup, a shared plan or a frozen
 * prescription. Lookup is by exact stable ID and is deliberately independent of
 * [ExerciseInstructionSource], so an exercise can carry a caution without an authored instruction
 * and the reverse. An exercise outside the approved set — including custom exercises, renamed rows
 * and CAT-02 profile suggestions — resolves to null, and a caller must never read that absence as a
 * statement that the exercise is safe.
 *
 * The text gives controllable technique for the built-in movement. It is not medical advice, an
 * injury prediction, a rehabilitation prescription or a substitute for coaching.
 */
internal object ExerciseCautionSource {

    /**
     * The approved 0.7.5 (EX-03) batch. Membership means the movement is in scope for a sourced
     * caution; it does not classify any exercise outside the set as unsafe.
     */
    val approvedCautionBatch: Set<String> = setOf(
        "conventional-deadlift",
        "romanian-deadlift",
        "dumbbell-rdl"
    )

    /** Authored cautions. Every entry must belong to [approvedCautionBatch]. */
    val cautionByExerciseId: Map<String, StringResource> = mapOf(
        "conventional-deadlift" to Res.string.exercise_caution_conventional_deadlift,
        "romanian-deadlift" to Res.string.exercise_caution_romanian_deadlift,
        "dumbbell-rdl" to Res.string.exercise_caution_dumbbell_rdl
    )

    /** Caution text for [exerciseId], or null when none is authored (including blank IDs). */
    fun cautionFor(exerciseId: String?): StringResource? = exerciseId
        ?.takeIf { it.isNotBlank() }
        ?.let(cautionByExerciseId::get)
}
