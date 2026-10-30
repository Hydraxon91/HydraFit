package com.hydrafit.app.core.domain.engine

/**
 * The measure a newly generated plan prescribes for an exercise.
 *
 * This is how the app shapes its own prescription; it is not a stored measurement, a backup field
 * or a user setting. Everything without an explicit preference keeps [Repetitions].
 */
sealed interface ExerciseMeasure {
    /** The planner's ordinary counted prescription. */
    data object Repetitions : ExerciseMeasure

    /** A held or elapsed effort prescribed in whole seconds, never reps. */
    data class Timed(val durationSeconds: Int) : ExerciseMeasure
}

/**
 * App-owned preferred measures for built-in exercise identities.
 *
 * A preference decides only what a *generated* slot looks like: it never rewrites a stored rep
 * value, an accepted plan, a routine or recorded history. A custom or renamed exercise id has no
 * entry here and stays rep-based. Plank's 30 seconds is an approved product default, not a
 * physiological claim.
 */
object ExerciseMeasureDefaults {

    /** The default duration for a preferred-timed exercise. */
    const val DEFAULT_TIMED_SECONDS: Int = 30

    private val preferredMeasures: Map<String, ExerciseMeasure> = mapOf(
        "plank" to ExerciseMeasure.Timed(DEFAULT_TIMED_SECONDS)
    )

    fun preferredMeasure(exerciseId: String): ExerciseMeasure =
        preferredMeasures[exerciseId] ?: ExerciseMeasure.Repetitions
}
