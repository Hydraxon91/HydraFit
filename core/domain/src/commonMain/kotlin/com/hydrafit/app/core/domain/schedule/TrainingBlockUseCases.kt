package com.hydrafit.app.core.domain.schedule

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.routine.PrescriptionBounds
import com.hydrafit.app.core.domain.routine.RoutineTemplateException
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.WorkoutLoadPolicy
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository

/** Closes an active block once every occurrence is resolved, and clears the cursor. */
class FinishTrainingBlockUseCase(
    private val scheduleRepository: WorkoutScheduleRepository,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(activationId: Long) {
        val activation = scheduleRepository.getActivation(activationId)
            ?: throw ScheduleException("That training block no longer exists")
        if (activation.status != ActivationStatus.ACTIVE) {
            throw ScheduleException("Only the active block can be finished")
        }
        if (scheduleRepository.occurrences(activationId).any { !it.isResolved }) {
            throw ScheduleException("Some workouts are still pending")
        }
        val now = timeProvider.nowMillis()
        scheduleRepository.updateActivationHeader(
            activation.copy(
                status = ActivationStatus.FINISHED,
                endedAtMillis = now,
                revision = activation.revision + 1
            )
        )
        clearCursorIfActive(activationId)
    }

    private suspend fun clearCursorIfActive(activationId: Long) {
        val state = scheduleRepository.scheduleState()
        if (state.activeActivationId == activationId) {
            scheduleRepository.setScheduleState(
                state.copy(activeActivationId = null, selectedOccurrenceId = null)
            )
        }
    }
}

/** Cancels the active block, keeping all recorded work and occurrence history. */
class CancelTrainingActivationUseCase(
    private val scheduleRepository: WorkoutScheduleRepository,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(activationId: Long) {
        val activation = scheduleRepository.getActivation(activationId)
            ?: throw ScheduleException("That training block no longer exists")
        if (activation.status != ActivationStatus.ACTIVE) return
        scheduleRepository.updateActivationHeader(
            activation.copy(
                status = ActivationStatus.CANCELLED,
                endedAtMillis = timeProvider.nowMillis(),
                revision = activation.revision + 1
            )
        )
        val state = scheduleRepository.scheduleState()
        if (state.activeActivationId == activationId) {
            scheduleRepository.setScheduleState(
                state.copy(activeActivationId = null, selectedOccurrenceId = null)
            )
        }
    }
}

/** One occurrence's resulting schedule after a move or mode switch (read-only preview). */
data class OccurrenceDateChange(
    val occurrenceId: Long,
    val scheduledEpochDay: Long?,
    val notBeforeEpochDay: Long? = null
)

/**
 * Moves one pending occurrence. In chosen-weekday mode its scheduled day and every later **pending**
 * occurrence are reflowed from the new date so the block keeps its chosen weekdays without
 * compressing; started (`IN_PROGRESS`) and resolved occurrences are never touched. In sequence mode
 * it only sets a "not before" hint and queue order is untouched.
 */
class MoveWorkoutOccurrenceUseCase(
    private val scheduleRepository: WorkoutScheduleRepository,
    private val previewSchedule: PreviewWorkoutScheduleUseCase
) {
    suspend operator fun invoke(occurrenceId: Long, newEpochDay: Long) {
        val occurrence = requireMoved(occurrenceId)
        val activation = scheduleRepository.getActivation(occurrence.activationId)
            ?: throw ScheduleException("That training block no longer exists")

        if (activation.mode == ScheduleMode.SEQUENCE) {
            scheduleRepository.updateOccurrence(
                occurrence.copy(notBeforeEpochDay = newEpochDay, revision = occurrence.revision + 1)
            )
            return
        }
        pendingSuffix(occurrence, activation, newEpochDay).forEach { (pending, date) ->
            scheduleRepository.updateOccurrence(
                pending.copy(scheduledEpochDay = date, revision = pending.revision + 1)
            )
        }
    }

    /** The resulting dates for the moved occurrence and its pending suffix, without writing. */
    suspend fun preview(occurrenceId: Long, newEpochDay: Long): List<OccurrenceDateChange> {
        val occurrence = requireMoved(occurrenceId)
        val activation = scheduleRepository.getActivation(occurrence.activationId)
            ?: throw ScheduleException("That training block no longer exists")
        if (activation.mode == ScheduleMode.SEQUENCE) {
            return listOf(
                OccurrenceDateChange(occurrence.id, null, notBeforeEpochDay = newEpochDay)
            )
        }
        return pendingSuffix(occurrence, activation, newEpochDay).map { (pending, date) ->
            OccurrenceDateChange(pending.id, scheduledEpochDay = date)
        }
    }

    private suspend fun requireMoved(occurrenceId: Long): WorkoutOccurrence {
        val occurrence = scheduleRepository.getOccurrence(occurrenceId)
            ?: throw ScheduleException("That workout no longer exists")
        if (occurrence.status != OccurrenceStatus.PENDING) {
            throw ScheduleException("Only a pending workout can be moved")
        }
        return occurrence
    }

    private suspend fun pendingSuffix(
        occurrence: WorkoutOccurrence,
        activation: TrainingActivation,
        newEpochDay: Long
    ): List<Pair<WorkoutOccurrence, Long?>> {
        val occurrences = scheduleRepository.occurrences(activation.id)
            .sortedBy { it.queuePosition }
        val index = occurrences.indexOfFirst { it.id == occurrence.id }
        val suffix = occurrences.drop(index).filter { it.status == OccurrenceStatus.PENDING }
        val dates = previewSchedule(
            count = suffix.size,
            startEpochDay = newEpochDay,
            mode = ScheduleMode.WEEKDAY,
            weekdays = activation.weekdays,
            startToday = true
        )
        return suffix.mapIndexed { position, pending -> pending to dates.getOrNull(position) }
    }
}

/**
 * Switches the active block between chosen-weekday and next-workout-sequence scheduling. The new
 * schedule is validated before anything is written; queued ids and recorded work are preserved, and
 * only **pending** occurrences' dates change (started/resolved occurrences keep their dates).
 */
class SwitchScheduleModeUseCase(
    private val scheduleRepository: WorkoutScheduleRepository,
    private val previewSchedule: PreviewWorkoutScheduleUseCase
) {
    suspend operator fun invoke(
        activationId: Long,
        mode: ScheduleMode,
        weekdays: Set<DayOfWeek>,
        startEpochDay: Long
    ) {
        val activation = requireActive(activationId)
        val pending = pendingOccurrences(activationId)
        val dates = datesFor(mode, weekdays, pending.size, startEpochDay)
        scheduleRepository.updateActivationHeader(
            activation.copy(mode = mode, weekdays = weekdays, revision = activation.revision + 1)
        )
        pending.forEachIndexed { position, occurrence ->
            scheduleRepository.updateOccurrence(
                occurrence.copy(
                    scheduledEpochDay = if (mode == ScheduleMode.WEEKDAY) {
                        dates?.getOrNull(position)
                    } else {
                        null
                    },
                    notBeforeEpochDay = if (mode == ScheduleMode.WEEKDAY) {
                        null
                    } else {
                        occurrence.notBeforeEpochDay
                    },
                    revision = occurrence.revision + 1
                )
            )
        }
    }

    /** The resulting dates for each pending occurrence under the new mode, without writing. */
    suspend fun preview(
        activationId: Long,
        mode: ScheduleMode,
        weekdays: Set<DayOfWeek>,
        startEpochDay: Long
    ): List<OccurrenceDateChange> {
        requireActive(activationId)
        val pending = pendingOccurrences(activationId)
        val dates = datesFor(mode, weekdays, pending.size, startEpochDay)
        return pending.mapIndexed { position, occurrence ->
            OccurrenceDateChange(
                occurrenceId = occurrence.id,
                scheduledEpochDay = if (mode == ScheduleMode.WEEKDAY) {
                    dates?.getOrNull(position)
                } else {
                    null
                }
            )
        }
    }

    private suspend fun requireActive(activationId: Long): TrainingActivation {
        val activation = scheduleRepository.getActivation(activationId)
            ?: throw ScheduleException("That training block no longer exists")
        if (activation.status != ActivationStatus.ACTIVE) {
            throw ScheduleException("Only the active block can be rescheduled")
        }
        return activation
    }

    private suspend fun pendingOccurrences(activationId: Long): List<WorkoutOccurrence> =
        scheduleRepository.occurrences(activationId)
            .sortedBy { it.queuePosition }
            .filter { it.status == OccurrenceStatus.PENDING }

    private fun datesFor(
        mode: ScheduleMode,
        weekdays: Set<DayOfWeek>,
        count: Int,
        startEpochDay: Long
    ): List<Long?>? = if (mode == ScheduleMode.WEEKDAY) {
        previewSchedule(count, startEpochDay, ScheduleMode.WEEKDAY, weekdays)
    } else {
        null
    }
}

/**
 * Re-prescribes a workout that has not started. Once any set is logged the prescription is frozen;
 * later changes must be template edits or occurrence replacement done before starting.
 */
class EditUnstartedOccurrenceUseCase(
    private val scheduleRepository: WorkoutScheduleRepository,
    private val workoutLogRepository: WorkoutLogRepository,
    private val catalog: ExerciseCatalog
) {
    suspend operator fun invoke(occurrenceId: Long, drafts: List<OccurrenceEntryDraft>) {
        val occurrence = scheduleRepository.getOccurrence(occurrenceId)
            ?: throw ScheduleException("That workout no longer exists")
        if (occurrence.status != OccurrenceStatus.PENDING) {
            throw ScheduleException("A workout can only be edited before it starts")
        }
        if (workoutLogRepository.setsForOccurrence(occurrenceId).isNotEmpty()) {
            throw ScheduleException("A workout with logged sets can no longer be edited")
        }
        if (drafts.isEmpty()) throw ScheduleException("A workout needs at least one exercise")

        val byId = catalog.all().associateBy { it.id }
        val entries = drafts.mapIndexed { index, draft ->
            try {
                PrescriptionBounds.validate(draft.sets, draft.reps, draft.weightKg)
            } catch (error: RoutineTemplateException) {
                throw ScheduleException(error.message ?: "Invalid prescription")
            }
            val exercise = byId[draft.exerciseId]
                ?: throw ScheduleException("That exercise no longer exists")
            val (kind, weightKg) = WorkoutLoadPolicy.reconcile(
                exercise.loadCapability,
                draft.loadKind,
                draft.weightKg
            )
            OccurrenceEntry(
                sourceActivationEntryId = null,
                position = index,
                exerciseId = draft.exerciseId,
                exerciseName = exercise.name,
                movementPattern = exercise.movementPattern,
                requiredEquipment = exercise.requiredEquipment,
                involvements = exercise.effectiveInvolvements,
                isUnilateral = exercise.isUnilateral,
                sets = draft.sets,
                reps = draft.reps,
                weightKg = weightKg,
                loadCapability = exercise.loadCapability,
                loadKind = kind
            )
        }
        scheduleRepository.replaceOccurrenceEntries(occurrenceId, entries)
        scheduleRepository.updateOccurrence(occurrence.copy(revision = occurrence.revision + 1))
    }
}
