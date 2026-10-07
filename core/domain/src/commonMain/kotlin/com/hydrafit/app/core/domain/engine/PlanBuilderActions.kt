package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.routine.ConvertPlanToTemplateUseCase
import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.routine.SaveRoutineTemplateUseCase
import com.hydrafit.app.core.domain.schedule.ActivateRoutineUseCase
import com.hydrafit.app.core.domain.schedule.ActivationRequest
import com.hydrafit.app.core.domain.schedule.PreviewWorkoutScheduleUseCase
import com.hydrafit.app.core.domain.schedule.ScheduleException
import com.hydrafit.app.core.domain.schedule.ScheduleMode
import com.hydrafit.app.core.domain.schedule.TrainingActivation
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleRepository
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.time.localEpochDay
import kotlinx.coroutines.flow.Flow

/**
 * The plan-facing action surface a feature may call: accepting a draft plan, swapping a single
 * exercise inside an accepted one, and turning an accepted plan into a scheduled training block or
 * a reusable routine. Grouping them keeps the SplitBuilder ViewModel within its dependency budget
 * instead of injecting each use case separately.
 */
class PlanBuilderActions(
    private val acceptWeeklyPlan: AcceptWeeklyPlanUseCase,
    private val substituteExercise: SubstituteExerciseUseCase,
    private val planHistoryRepository: PlanHistoryRepository,
    private val convertPlanToTemplate: ConvertPlanToTemplateUseCase,
    private val saveRoutineTemplate: SaveRoutineTemplateUseCase,
    private val activateRoutine: ActivateRoutineUseCase,
    private val previewWorkoutSchedule: PreviewWorkoutScheduleUseCase,
    private val scheduleRepository: WorkoutScheduleRepository,
    private val timeProvider: TimeProvider
) {
    suspend fun accept(plan: WeeklyPlan) = acceptWeeklyPlan(plan)

    /** The active training block, or null; lets the schedule dialog warn about replacing one. */
    fun observeActiveActivation(): Flow<TrainingActivation?> =
        scheduleRepository.observeActiveActivation()

    suspend fun swapCandidates(
        plan: AcceptedPlan,
        dayIndex: Int,
        position: Int,
        request: PlanRequest
    ): List<SwapCandidate> = substituteExercise.candidates(plan, dayIndex, position, request)

    suspend fun substitute(
        plan: AcceptedPlan,
        dayIndex: Int,
        position: Int,
        request: PlanRequest,
        selectedExerciseId: String
    ): AcceptedExercise? =
        substituteExercise.invoke(plan, dayIndex, position, request, selectedExerciseId)

    /** Today as a local civil day, for the "start today" default. */
    fun todayEpochDay(): Long =
        localEpochDay(timeProvider.nowMillis(), timeProvider.utcOffsetMillis())

    /** One scheduled civil day per queue position, or null in sequence mode. */
    fun previewSchedule(
        count: Int,
        startEpochDay: Long,
        mode: ScheduleMode,
        weekdays: Set<DayOfWeek>,
        startToday: Boolean = false
    ): List<Long?> = previewWorkoutSchedule(count, startEpochDay, mode, weekdays, startToday)

    /** Turns an already-accepted plan into a frozen, active training block. Returns the block id. */
    suspend fun scheduleAcceptedPlan(accepted: AcceptedPlan, request: ActivationRequest): Long {
        requireGeneratedFrequency(accepted.days.size, request)
        val template = convertPlanToTemplate(accepted, request.name)
        return activateRoutine(
            template,
            request.copy(
                sourcePlanId = accepted.id,
                weekNumber = accepted.weekNumber,
                cycleNumber = accepted.cycleNumber
            )
        )
    }

    /** Accepts a draft plan and immediately schedules it as an active block. */
    suspend fun acceptAndSchedule(plan: WeeklyPlan, request: ActivationRequest): Long {
        // Validate before accepting so an invalid schedule never writes accepted-plan history.
        requireGeneratedFrequency(plan.days.size, request)
        acceptWeeklyPlan(plan)
        val accepted = planHistoryRepository.latest()
            ?: throw ScheduleException("The plan could not be accepted")
        return scheduleAcceptedPlan(accepted, request)
    }

    /**
     * A generated plan keeps its weekly frequency: in chosen-weekday mode it needs exactly one
     * chosen weekday per workout, so a block is never stretched across weeks (that would require
     * frequency-aware volume planning, which is not approved).
     */
    private fun requireGeneratedFrequency(count: Int, request: ActivationRequest) {
        if (request.mode == ScheduleMode.WEEKDAY && request.weekdays.size != count) {
            throw ScheduleException("A generated plan needs one chosen weekday per workout")
        }
    }

    /** Copies an accepted plan into a new editable routine and returns its id. */
    suspend fun saveAcceptedPlanAsRoutine(accepted: AcceptedPlan, name: String): Long =
        saveRoutineTemplate(convertPlanToTemplate(accepted, name)).id

    /**
     * Copies a draft (not-yet-accepted) plan into a new routine without accepting it, so saving a
     * routine never advances the accepted-plan ordinal.
     */
    suspend fun saveDraftPlanAsRoutine(plan: WeeklyPlan, name: String): Long =
        saveRoutineTemplate(convertPlanToTemplate(plan, name)).id

    /** Converts an accepted plan into an unsaved routine draft (for previewing/editing). */
    fun toRoutineDraft(accepted: AcceptedPlan, name: String): RoutineTemplate =
        convertPlanToTemplate(accepted, name)
}
