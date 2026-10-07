package com.hydrafit.app.core.domain.schedule

import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.time.TimeProvider

/**
 * Builds a frozen activation and persists it with its occurrences and the scheduling cursor in one
 * transaction. Prescriptions arrive already snapshotted, so this sits below both routine activation
 * and block repetition. When a block is already active it must be explicitly replaced
 * ([ActivationRequest.replaceActive]) or the write is rejected atomically.
 */
class CreateTrainingActivationUseCase(
    private val scheduleRepository: WorkoutScheduleRepository,
    private val previewSchedule: PreviewWorkoutScheduleUseCase,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(
        workouts: List<ActivationWorkout>,
        request: ActivationRequest,
        acceptedPlan: AcceptedPlan? = null
    ): Long {
        if (workouts.isEmpty()) throw ScheduleException("A block needs at least one workout")
        val dates = previewSchedule(
            count = workouts.size,
            startEpochDay = request.startEpochDay,
            mode = request.mode,
            weekdays = request.weekdays,
            startToday = request.startToday
        )
        val activation = TrainingActivation(
            templateId = request.templateId,
            templateRevision = request.templateRevision,
            sourcePlanId = request.sourcePlanId,
            name = request.name,
            createdAtMillis = timeProvider.nowMillis(),
            startEpochDay = request.startEpochDay,
            mode = request.mode,
            weekdays = request.weekdays,
            status = ActivationStatus.ACTIVE,
            weekNumber = request.weekNumber,
            cycleNumber = request.cycleNumber,
            workouts = workouts
        )
        return scheduleRepository.acceptAndActivate(
            acceptedPlan = acceptedPlan,
            activation = activation,
            scheduledEpochDays = dates,
            replaceActive = request.replaceActive
        )
    }
}

/**
 * Snapshots a routine template's prescriptions from the current catalog and activates it. Names,
 * movement patterns, equipment and muscle mappings are frozen, so later catalog edits cannot change
 * what was activated. Archived routines and unknown exercises are rejected.
 */
class ActivateRoutineUseCase(
    private val catalog: ExerciseCatalog,
    private val createActivation: CreateTrainingActivationUseCase
) {
    /** The frozen activation workouts for [template], or a [ScheduleException] when it is unusable. */
    suspend fun buildWorkouts(template: RoutineTemplate): List<ActivationWorkout> {
        if (template.isArchived) {
            throw ScheduleException("An archived routine cannot be started")
        }
        if (template.workouts.isEmpty()) {
            throw ScheduleException("A routine needs at least one workout")
        }
        val byId = catalog.all().associateBy { it.id }
        return template.workouts.sortedBy { it.position }.map { workout ->
            ActivationWorkout(
                position = workout.position,
                name = workout.name,
                focus = workout.focus,
                entries = workout.entries.sortedBy { it.position }.map { entry ->
                    val exercise = byId[entry.exerciseId]
                        ?: throw ScheduleException(
                            "This routine references an unknown exercise: ${entry.exerciseId}"
                        )
                    ActivationEntry(
                        position = entry.position,
                        exerciseId = entry.exerciseId,
                        exerciseName = exercise.name,
                        movementPattern = exercise.movementPattern,
                        requiredEquipment = exercise.requiredEquipment,
                        involvements = exercise.effectiveInvolvements,
                        isUnilateral = exercise.isUnilateral,
                        sets = entry.sets,
                        reps = entry.reps,
                        weightKg = entry.weightKg
                    )
                }
            )
        }
    }

    suspend operator fun invoke(
        template: RoutineTemplate,
        request: ActivationRequest,
        acceptedPlan: AcceptedPlan? = null
    ): Long {
        val workouts = buildWorkouts(template)
        val provenance = request.copy(
            templateId = request.templateId ?: template.id.takeIf { it != 0L },
            templateRevision =
            request.templateRevision ?: template.revision.takeIf { template.id != 0L },
            sourcePlanId = request.sourcePlanId ?: template.sourcePlanId
        )
        return createActivation(workouts, provenance, acceptedPlan)
    }
}

/**
 * Repeats a previous block from its frozen master prescriptions, creating a fresh activation and
 * occurrence ids so the original block's history is untouched.
 */
class RepeatTrainingBlockUseCase(
    private val scheduleRepository: WorkoutScheduleRepository,
    private val createActivation: CreateTrainingActivationUseCase
) {
    suspend operator fun invoke(activationId: Long, startEpochDay: Long): Long {
        val source = scheduleRepository.getActivation(activationId)
            ?: throw ScheduleException("That training block no longer exists")
        val workouts = source.workouts.sortedBy { it.position }.map { workout ->
            workout.copy(
                id = 0,
                entries = workout.entries.sortedBy { it.position }.map { it.copy(id = 0) }
            )
        }
        return createActivation(
            workouts,
            ActivationRequest(
                name = source.name,
                startEpochDay = startEpochDay,
                mode = source.mode,
                weekdays = source.weekdays,
                templateId = source.templateId,
                templateRevision = source.templateRevision,
                sourcePlanId = source.sourcePlanId,
                weekNumber = source.weekNumber,
                cycleNumber = source.cycleNumber,
                replaceActive = true
            )
        )
    }
}
