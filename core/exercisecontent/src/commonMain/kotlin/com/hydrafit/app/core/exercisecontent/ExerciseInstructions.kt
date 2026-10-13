package com.hydrafit.app.core.exercisecontent

import org.jetbrains.compose.resources.StringResource

/**
 * The resource keys for one authored exercise instruction. Kept apart from the resolved text so the
 * catalog can be enumerated and validated without a resource environment.
 */
internal data class ExerciseInstructionKeys(
    val setup: StringResource,
    val execution: StringResource
)

/**
 * Bundled offline instruction text for seeded exercises, keyed by the catalog's stable exercise ID.
 *
 * This is app-owned catalog content, not user data: it ships with the app, is never written to the
 * database and is not part of a backup, a shared plan or a frozen prescription. Exercises with no
 * bundled entry — custom exercises and seeded exercises outside the authored batches — resolve to
 * null so a caller shows the explicit missing-content state instead of invented guidance. Lookup is
 * by ID only; a name never re-derives an identity.
 */
internal object ExerciseInstructionSource {

    /**
     * The approved 0.7.4 first batch. Membership means the exercise is in scope for authoring, not
     * that text exists yet: only a [keysByExerciseId] entry resolves to an instruction.
     */
    val approvedBatch: Set<String> = setOf(
        "barbell-bench-press",
        "dumbbell-bench-press",
        "push-up",
        "overhead-press",
        "dumbbell-row",
        "lat-pulldown",
        "seated-cable-row",
        "lateral-raise",
        "back-squat",
        "goblet-squat",
        "romanian-deadlift",
        "leg-curl",
        "dumbbell-curl",
        "triceps-pushdown",
        "standing-calf-raise",
        "hanging-leg-raise"
    )

    /**
     * Authored instructions, added batch by batch. Every entry must belong to [approvedBatch]; an
     * exercise outside the approved batch must not gain bundled text.
     */
    val keysByExerciseId: Map<String, ExerciseInstructionKeys> = emptyMap()

    /** Keys for [exerciseId], or null when there is no authored instruction (including blank IDs). */
    fun keysFor(exerciseId: String?): ExerciseInstructionKeys? = exerciseId
        ?.takeIf { it.isNotBlank() }
        ?.let(keysByExerciseId::get)
}
