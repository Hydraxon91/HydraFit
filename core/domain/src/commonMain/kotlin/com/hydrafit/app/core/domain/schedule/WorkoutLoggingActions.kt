package com.hydrafit.app.core.domain.schedule

import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ObserveAcceptedPlanUseCase
import com.hydrafit.app.core.domain.workout.GetWorkoutSummaryUseCase
import com.hydrafit.app.core.domain.workout.WorkoutSummary
import kotlinx.coroutines.flow.Flow

/**
 * The training context the Logger needs: the accepted plan (legacy weekday focus and week/cycle
 * stamping), the active block's queue, the actions that start and conclude a workout occurrence, and
 * the summary of a workout it just concluded. Grouping them keeps the Logger ViewModel within its
 * dependency budget.
 */
class WorkoutLoggingActions(
    private val observeAcceptedPlanUseCase: ObserveAcceptedPlanUseCase,
    private val scheduleRepository: WorkoutScheduleRepository,
    private val startWorkoutOccurrence: StartWorkoutOccurrenceUseCase,
    private val finishWorkoutOccurrence: FinishWorkoutOccurrenceUseCase,
    private val skipWorkoutOccurrence: SkipWorkoutOccurrenceUseCase,
    private val getWorkoutSummary: GetWorkoutSummaryUseCase
) {
    fun observeAcceptedPlan(): Flow<AcceptedPlan?> = observeAcceptedPlanUseCase()

    fun observeScheduleState(): Flow<WorkoutScheduleState> =
        scheduleRepository.observeScheduleState()

    fun observeActiveActivation(): Flow<TrainingActivation?> =
        scheduleRepository.observeActiveActivation()

    fun observeOccurrences(activationId: Long): Flow<List<WorkoutOccurrence>> =
        scheduleRepository.observeOccurrences(activationId)

    suspend fun start(occurrenceId: Long): WorkoutOccurrence = startWorkoutOccurrence(occurrenceId)

    suspend fun finish(occurrenceId: Long, mode: FinishMode): WorkoutScheduleState =
        finishWorkoutOccurrence(occurrenceId, mode)

    suspend fun skip(occurrenceId: Long): WorkoutScheduleState = skipWorkoutOccurrence(occurrenceId)

    /** The post-finish summary of [occurrenceId], or null when it no longer exists. */
    suspend fun summarize(occurrenceId: Long): WorkoutSummary? = getWorkoutSummary(occurrenceId)
}
