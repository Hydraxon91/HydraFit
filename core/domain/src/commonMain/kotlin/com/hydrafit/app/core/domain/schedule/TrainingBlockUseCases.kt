package com.hydrafit.app.core.domain.schedule

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.routine.PrescriptionBounds
import com.hydrafit.app.core.domain.routine.RoutineTemplateException
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.time.TimeProvider
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

/**
 * Moves one pending occurrence. In chosen-weekday mode its scheduled day and every later unresolved
 * occurrence are reflowed from the new date so the block keeps its chosen weekdays without
 * compressing. In sequence mode it only sets a "not before" hint and queue order is untouched.
 */
class MoveWorkoutOccurrenceUseCase(
    private val scheduleRepository: WorkoutScheduleRepository,
    private val previewSchedule: PreviewWorkoutScheduleUseCase
) {
    suspend operator fun invoke(occurrenceId: Long, newEpochDay: Long) {
        val occurrence = scheduleRepository.getOccurrence(occurrenceId)
            ?: throw ScheduleException("That workout no longer exists")
        if (occurrence.isResolved) throw ScheduleException("That workout is already finished")
        val activation = scheduleRepository.getActivation(occurrence.activationId)
            ?: throw ScheduleException("That training block no longer exists")

        if (activation.mode == ScheduleMode.SEQUENCE) {
            scheduleRepository.updateOccurrence(
                occurrence.copy(notBeforeEpochDay = newEpochDay, revision = occurrence.revision + 1)
            )
            return
        }

        val occurrences = scheduleRepository.occurrences(occurrence.activationId)
            .sortedBy { it.queuePosition }
        val index = occurrences.indexOfFirst { it.id == occurrenceId }
        val suffix = occurrences.drop(index).filterNot { it.isResolved }
        val dates = previewSchedule(
            count = suffix.size,
            startEpochDay = newEpochDay,
            mode = ScheduleMode.WEEKDAY,
            weekdays = activation.weekdays,
            startToday = true
        )
        suffix.forEachIndexed { position, pending ->
            scheduleRepository.updateOccurrence(
                pending.copy(
                    scheduledEpochDay = dates.getOrNull(position),
                    revision = pending.revision + 1
                )
            )
        }
    }
}

/**
 * Switches the active block between chosen-weekday and next-workout-sequence scheduling. Queued
 * workout ids and recorded work are preserved; only the unresolved occurrences' dates change.
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
        val activation = scheduleRepository.getActivation(activationId)
            ?: throw ScheduleException("That training block no longer exists")
        if (activation.status != ActivationStatus.ACTIVE) {
            throw ScheduleException("Only the active block can be rescheduled")
        }
        scheduleRepository.updateActivationHeader(
            activation.copy(mode = mode, weekdays = weekdays, revision = activation.revision + 1)
        )
        val unresolved = scheduleRepository.occurrences(activationId)
            .sortedBy { it.queuePosition }
            .filterNot { it.isResolved }
        if (mode == ScheduleMode.SEQUENCE) {
            unresolved.forEach { pending ->
                scheduleRepository.updateOccurrence(
                    pending.copy(scheduledEpochDay = null, revision = pending.revision + 1)
                )
            }
            return
        }
        val dates = previewSchedule(unresolved.size, startEpochDay, ScheduleMode.WEEKDAY, weekdays)
        unresolved.forEachIndexed { position, pending ->
            scheduleRepository.updateOccurrence(
                pending.copy(
                    scheduledEpochDay = dates.getOrNull(position),
                    notBeforeEpochDay = null,
                    revision = pending.revision + 1
                )
            )
        }
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
            OccurrenceEntry(
                sourceActivationEntryId = null,
                position = index,
                exerciseId = draft.exerciseId,
                exerciseName = exercise?.name ?: draft.exerciseId,
                movementPattern = exercise?.movementPattern ?: MovementPattern.CORE,
                requiredEquipment = exercise?.requiredEquipment ?: emptySet(),
                involvements = exercise?.effectiveInvolvements,
                isUnilateral = exercise?.isUnilateral ?: false,
                sets = draft.sets,
                reps = draft.reps,
                weightKg = draft.weightKg
            )
        }
        scheduleRepository.replaceOccurrenceEntries(occurrenceId, entries)
        scheduleRepository.updateOccurrence(occurrence.copy(revision = occurrence.revision + 1))
    }
}
