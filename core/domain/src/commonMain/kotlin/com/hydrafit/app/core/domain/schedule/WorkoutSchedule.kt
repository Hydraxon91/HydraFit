package com.hydrafit.app.core.domain.schedule

import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.workout.LoadKind

/** How a block assigns its workouts to a calendar. */
enum class ScheduleMode { WEEKDAY, SEQUENCE }

enum class ActivationStatus { ACTIVE, FINISHED, CANCELLED }

enum class OccurrenceStatus {
    PENDING,
    IN_PROGRESS,
    FINISHED,
    FINISHED_PARTIAL,
    SKIPPED;

    /** True once the occurrence can no longer advance or receive new work. */
    val isResolved: Boolean
        get() = this == FINISHED || this == FINISHED_PARTIAL || this == SKIPPED
}

/** What happened to sets a resolved occurrence never performed. */
enum class RemainingDisposition { OMITTED, SKIPPED }

/**
 * A frozen training block: prescriptions snapshotted from a routine template or an accepted plan,
 * plus the scheduling choices. Editing the source template or catalog later never rewrites it.
 * [templateId] is provenance only; the activation owns its own copy of every prescription.
 */
data class TrainingActivation(
    val id: Long = 0,
    val templateId: Long? = null,
    val templateRevision: Int? = null,
    val sourcePlanId: Long? = null,
    val name: String,
    val createdAtMillis: Long,
    val startEpochDay: Long,
    val mode: ScheduleMode,
    val weekdays: Set<DayOfWeek>,
    val status: ActivationStatus,
    val weekNumber: Int? = null,
    val cycleNumber: Int? = null,
    val endedAtMillis: Long? = null,
    val revision: Int = 1,
    val workouts: List<ActivationWorkout> = emptyList()
)

data class ActivationWorkout(
    val id: Long = 0,
    val position: Int = 0,
    val name: String,
    val focus: SplitFocus? = null,
    val entries: List<ActivationEntry> = emptyList()
)

data class ActivationEntry(
    val id: Long = 0,
    val position: Int = 0,
    val exerciseId: String,
    val exerciseName: String,
    val movementPattern: MovementPattern,
    val requiredEquipment: Set<EquipmentTag> = emptySet(),
    val involvements: Map<MuscleGroup, Double>? = null,
    val isUnilateral: Boolean = false,
    val sets: Int,
    val reps: Int,
    val weightKg: Double? = null,
    /** The exercise's load capability frozen at activation. */
    val loadCapability: ExerciseLoadCapability = ExerciseLoadCapability.EXTERNAL,
    /** What [weightKg] means on this frozen slot. */
    val loadKind: LoadKind = LoadKind.EXTERNAL
)

data class WorkoutOccurrence(
    val id: Long = 0,
    val activationId: Long,
    val activationWorkoutId: Long,
    val queuePosition: Int,
    val scheduledEpochDay: Long? = null,
    val notBeforeEpochDay: Long? = null,
    val startedAtMillis: Long? = null,
    val resolvedAtMillis: Long? = null,
    val status: OccurrenceStatus = OccurrenceStatus.PENDING,
    val revision: Int = 1,
    val entries: List<OccurrenceEntry> = emptyList()
) {
    val isResolved: Boolean get() = status.isResolved
}

/**
 * One occurrence's working copy of a prescription. [sourceActivationEntryId] links back to the
 * frozen master; [remainingDisposition]/[terminalRemainingSets] record what a resolved occurrence
 * left unperformed.
 */
data class OccurrenceEntry(
    val id: Long = 0,
    val sourceActivationEntryId: Long? = null,
    val position: Int = 0,
    val exerciseId: String,
    val exerciseName: String,
    val movementPattern: MovementPattern,
    val requiredEquipment: Set<EquipmentTag> = emptySet(),
    val involvements: Map<MuscleGroup, Double>? = null,
    val isUnilateral: Boolean = false,
    val sets: Int,
    val reps: Int,
    val weightKg: Double? = null,
    /** The exercise's load capability frozen into this occurrence entry. */
    val loadCapability: ExerciseLoadCapability = ExerciseLoadCapability.EXTERNAL,
    /** What [weightKg] means on this occurrence entry. */
    val loadKind: LoadKind = LoadKind.EXTERNAL,
    val remainingDisposition: RemainingDisposition? = null,
    val terminalRemainingSets: Int? = null
)

/** The single-row scheduling cursor: which block is active and which workout is selected next. */
data class WorkoutScheduleState(
    val activeActivationId: Long? = null,
    val selectedOccurrenceId: Long? = null,
    val legacyFallbackEnabled: Boolean = true
)

fun Set<DayOfWeek>.toWeekdayMask(): Int = fold(0) { mask, day -> mask or dayBit(day) }

fun weekdaysOf(mask: Int): Set<DayOfWeek> =
    DayOfWeek.entries.filterTo(mutableSetOf()) { mask and dayBit(it) != 0 }

private fun dayBit(day: DayOfWeek): Int = 1 shl day.ordinal
