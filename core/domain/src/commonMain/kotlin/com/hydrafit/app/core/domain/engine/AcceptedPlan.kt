package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.workout.LoadKind

/**
 * A plan the user explicitly accepted. Display data is snapshotted so a later catalog edit cannot
 * silently rewrite the meaning of an accepted plan.
 */
data class AcceptedPlan(
    val engine: PlannerEngineId,
    val acceptedAtMillis: Long,
    val days: List<AcceptedDay>,
    val weekNumber: Int = 1,
    val cycleNumber: Int = 1,
    /** The stored history row's id; 0 for a plan that has not been persisted yet. */
    val id: Long = 0,
    /**
     * The VOL-01 volume assessment frozen at acceptance, or empty for a legacy plan (accepted before
     * the explanation migration). A user-confirmed substitution clears it.
     */
    val armCoverage: List<ArmMuscleCoverage> = emptyList(),
    /** Who produced the plan; null for a legacy plan with no persisted assessment. */
    val volumeAttribution: PlanAttribution? = null,
    /** True once a user-confirmed substitution invalidates the frozen assessment. */
    val volumeExplanationInvalidated: Boolean = false
) {
    /**
     * Derived (not a constructor parameter) so it always reflects [armCoverage] and
     * [volumeExplanationInvalidated] after any `copy`, rather than freezing a stale default.
     */
    val volumeExplanationStatus: VolumeExplanationStatus
        get() = when {
            volumeExplanationInvalidated -> VolumeExplanationStatus.INVALIDATED_BY_SUBSTITUTION
            armCoverage.isNotEmpty() -> VolumeExplanationStatus.AVAILABLE
            else -> VolumeExplanationStatus.ABSENT
        }

    val volumeAssessmentVersion: Int?
        get() = if (volumeExplanationStatus == VolumeExplanationStatus.ABSENT) null else 1

    /** Spreads the plan's training days across the week, starting on Monday. */
    fun scheduledDay(dayIndex: Int): DayOfWeek? {
        if (days.isEmpty() || dayIndex !in days.indices) return null
        val offset = dayIndex * DayOfWeek.entries.size / days.size
        return DayOfWeek.entries[offset.coerceIn(0, DayOfWeek.entries.lastIndex)]
    }

    fun dayFor(dayOfWeek: DayOfWeek): AcceptedDay? =
        days.firstOrNull { scheduledDay(it.dayIndex) == dayOfWeek }
}

data class AcceptedDay(
    val dayIndex: Int,
    val focus: SplitFocus,
    val exercises: List<AcceptedExercise>
)

data class AcceptedExercise(
    val exerciseId: String,
    val sets: Int,
    val reps: Int,
    val name: String,
    val movementPattern: MovementPattern,
    val suggestedWeightKg: Double? = null,
    /** The exercise's load capability frozen at acceptance; external by default. */
    val loadCapability: ExerciseLoadCapability = ExerciseLoadCapability.EXTERNAL,
    /** What [suggestedWeightKg] means; external by default (a pre-EX-02 row is read as legacy). */
    val loadKind: LoadKind = LoadKind.EXTERNAL
)

/** Renders a stored plan with the same shape the live (draft) plan uses. */
fun AcceptedPlan.toWeeklyPlan(): WeeklyPlan = WeeklyPlan(
    engine = engine,
    weekNumber = weekNumber,
    cycleNumber = cycleNumber,
    armCoverage = armCoverage,
    days = days.map { day ->
        WorkoutDay(
            dayIndex = day.dayIndex,
            focus = day.focus,
            exercises = day.exercises.map { exercise ->
                PlannedExercise(
                    exerciseId = exercise.exerciseId,
                    sets = exercise.sets,
                    reps = exercise.reps,
                    suggestedWeightKg = exercise.suggestedWeightKg,
                    loadKind = exercise.loadKind,
                    loadCapability = exercise.loadCapability
                )
            }
        )
    }
)
