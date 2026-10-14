package com.hydrafit.app.core.exercisecontent

/**
 * Public, presentation-facing entry point for the bundled offline exercise technique
 * considerations.
 *
 * The owning features use this only to decide whether an instruction action shows the
 * "Technique considerations available" indication. The content itself is presented by
 * [ExerciseInstructionDialog]; nothing here is persisted or written.
 */
object ExerciseCautions {

    /**
     * True when [exerciseId] has bundled technique considerations. The check is by exact stable ID,
     * never by display name, so a `user-*` custom identity, a blank or unknown id and a renamed
     * seeded row's display text all return false — while a renamed seeded row itself keeps its id
     * and therefore keeps its considerations. Absence is not a statement that an exercise is safe.
     */
    fun hasTechniqueConsiderations(exerciseId: String?): Boolean =
        ExerciseCautionSource.cautionFor(exerciseId) != null
}
