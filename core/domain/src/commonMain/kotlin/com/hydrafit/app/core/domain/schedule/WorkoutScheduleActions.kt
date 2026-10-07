package com.hydrafit.app.core.domain.schedule

import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.time.DayOfWeek
import kotlinx.coroutines.flow.Flow

/**
 * Groups every scheduling operation a screen performs (observe the queue, activate, postpone, switch
 * mode, conclude workouts) so a ViewModel depends on one collaborator. It is a thin facade that adds
 * no behavior of its own; splitting it would only add indirection.
 */
class WorkoutScheduleActions(
    private val repository: WorkoutScheduleRepository,
    private val previewSchedule: PreviewWorkoutScheduleUseCase,
    private val activateRoutine: ActivateRoutineUseCase,
    private val repeatBlock: RepeatTrainingBlockUseCase,
    private val cancelActivation: CancelTrainingActivationUseCase,
    private val finishTrainingBlock: FinishTrainingBlockUseCase,
    private val moveOccurrence: MoveWorkoutOccurrenceUseCase,
    private val switchScheduleMode: SwitchScheduleModeUseCase,
    private val selectOccurrence: SelectWorkoutOccurrenceUseCase,
    private val startOccurrence: StartWorkoutOccurrenceUseCase,
    private val editOccurrence: EditUnstartedOccurrenceUseCase
) {
    fun observeActiveActivation(): Flow<TrainingActivation?> = repository.observeActiveActivation()

    fun observeScheduleState(): Flow<WorkoutScheduleState> = repository.observeScheduleState()

    fun observeOccurrences(activationId: Long): Flow<List<WorkoutOccurrence>> =
        repository.observeOccurrences(activationId)

    suspend fun activeActivation(): TrainingActivation? = repository.activeActivation()

    suspend fun getOccurrence(id: Long): WorkoutOccurrence? = repository.getOccurrence(id)

    /** One scheduled civil day per queue position, or null in sequence mode. */
    fun preview(
        count: Int,
        startEpochDay: Long,
        mode: ScheduleMode,
        weekdays: Set<DayOfWeek>,
        startToday: Boolean = false
    ): List<Long?> = previewSchedule(count, startEpochDay, mode, weekdays, startToday)

    suspend fun activate(template: RoutineTemplate, request: ActivationRequest): Long =
        activateRoutine(template, request)

    suspend fun repeat(activationId: Long, startEpochDay: Long): Long =
        repeatBlock(activationId, startEpochDay)

    suspend fun cancelActive() {
        repository.activeActivation()?.let { cancelActivation(it.id) }
    }

    suspend fun finishBlock(activationId: Long) = finishTrainingBlock(activationId)

    suspend fun move(occurrenceId: Long, newEpochDay: Long) =
        moveOccurrence(occurrenceId, newEpochDay)

    /** Dates the postponed occurrence and its pending suffix would move to, without writing. */
    suspend fun previewMove(occurrenceId: Long, newEpochDay: Long): List<OccurrenceDateChange> =
        moveOccurrence.preview(occurrenceId, newEpochDay)

    /** Dates pending occurrences would take under a new mode, without writing. */
    suspend fun previewSwitchMode(
        activationId: Long,
        mode: ScheduleMode,
        weekdays: Set<DayOfWeek>,
        startEpochDay: Long
    ): List<OccurrenceDateChange> =
        switchScheduleMode.preview(activationId, mode, weekdays, startEpochDay)

    suspend fun switchMode(
        activationId: Long,
        mode: ScheduleMode,
        weekdays: Set<DayOfWeek>,
        startEpochDay: Long
    ) = switchScheduleMode(activationId, mode, weekdays, startEpochDay)

    suspend fun select(occurrenceId: Long) = selectOccurrence(occurrenceId)

    suspend fun start(occurrenceId: Long): WorkoutOccurrence = startOccurrence(occurrenceId)

    suspend fun edit(occurrenceId: Long, drafts: List<OccurrenceEntryDraft>) =
        editOccurrence(occurrenceId, drafts)
}
