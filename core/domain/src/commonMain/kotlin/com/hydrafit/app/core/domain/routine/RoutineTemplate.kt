package com.hydrafit.app.core.domain.routine

import com.hydrafit.app.core.domain.engine.SplitFocus

/**
 * A named, editable routine the user builds and reuses.
 *
 * Workouts and their exercise slots are ordered and keep their persisted id across edits and
 * reordering; duplication assigns fresh ids so the copy is independent. A template never carries
 * performed history — activating one snapshots its prescriptions, so a later catalog or template
 * edit cannot rewrite an activation.
 */
data class RoutineTemplate(
    val id: Long = 0,
    val name: String,
    val revision: Int = 0,
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
    val archivedAtMillis: Long? = null,
    val sourcePlanId: Long? = null,
    val workouts: List<RoutineWorkout> = emptyList()
) {
    val isArchived: Boolean get() = archivedAtMillis != null
}

data class RoutineWorkout(
    val id: Long = 0,
    val position: Int = 0,
    val name: String,
    val focus: SplitFocus? = null,
    val entries: List<RoutineEntry> = emptyList()
)

data class RoutineEntry(
    val id: Long = 0,
    val position: Int = 0,
    val exerciseId: String,
    val sets: Int,
    val reps: Int,
    /** null = unspecified load; 0.0 = an explicit zero external load; positive = stored kilograms. */
    val weightKg: Double? = null
)
