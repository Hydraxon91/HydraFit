package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence

/**
 * One prescribed exercise of an active guided workout with its recorded progress. The prescription
 * is frozen from the occurrence entry, so a later edit to the accepted plan does not move the target.
 */
data class GuidedExerciseProgress(
    val occurrenceEntryId: Long,
    val exerciseId: String,
    val exerciseName: String,
    val prescribedSets: Int,
    val performedWorkingSets: Int,
    val reps: Int,
    val weightKg: Double?,
    val loadKind: LoadKind,
    val loadCapability: ExerciseLoadCapability
) {
    /** Prescribed working sets still unrecorded for this entry. */
    val remainingSets: Int get() = (prescribedSets - performedWorkingSets).coerceAtLeast(0)

    val isComplete: Boolean get() = remainingSets == 0
}

/**
 * The ordered guided view of an active workout occurrence. Working sets are counted only through
 * their occurrence entry, so warm-ups and unattributed extras never satisfy a prescription.
 */
data class GuidedWorkoutProgress(
    val occurrenceId: Long,
    val exercises: List<GuidedExerciseProgress>
) {
    val prescribedSets: Int get() = exercises.sumOf { it.prescribedSets }

    /** Recorded working sets, capped per entry so an over-logged entry cannot overstate progress. */
    val performedSets: Int
        get() = exercises.sumOf { it.performedWorkingSets.coerceAtMost(it.prescribedSets) }

    val allComplete: Boolean get() = exercises.all { it.isComplete }
}

/**
 * Builds the guided progress for [occurrence] from recorded working sets keyed by occurrence entry.
 * Pure: it reads no repository or clock and mutates nothing.
 */
fun buildGuidedWorkoutProgress(
    occurrence: WorkoutOccurrence,
    performedWorkingSetsByEntry: Map<Long, Int>
): GuidedWorkoutProgress = GuidedWorkoutProgress(
    occurrenceId = occurrence.id,
    exercises = occurrence.entries
        .sortedBy { it.position }
        .map { entry ->
            GuidedExerciseProgress(
                occurrenceEntryId = entry.id,
                exerciseId = entry.exerciseId,
                exerciseName = entry.exerciseName,
                prescribedSets = entry.sets,
                performedWorkingSets = performedWorkingSetsByEntry[entry.id] ?: 0,
                reps = entry.reps,
                weightKg = entry.weightKg,
                loadKind = entry.loadKind,
                loadCapability = entry.loadCapability
            )
        }
)
