package com.hydrafit.app.core.domain.schedule

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.time.TimeProvider

/**
 * Persists a frozen activation and generates one pending occurrence per workout, then points the
 * scheduling cursor at the first occurrence. Prescriptions arrive already snapshotted so this sits
 * below both routine activation and block repetition. When a block is already active it must be
 * explicitly replaced ([ActivationRequest.replaceActive]) or the call is rejected.
 */
class CreateTrainingActivationUseCase(
    private val scheduleRepository: WorkoutScheduleRepository,
    private val previewSchedule: PreviewWorkoutScheduleUseCase,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(
        workouts: List<ActivationWorkout>,
        request: ActivationRequest
    ): Long {
        if (workouts.isEmpty()) throw ScheduleException("A block needs at least one workout")
        val now = timeProvider.nowMillis()
        val dates = previewSchedule(
            count = workouts.size,
            startEpochDay = request.startEpochDay,
            mode = request.mode,
            weekdays = request.weekdays,
            startToday = request.startToday
        )

        val existing = scheduleRepository.activeActivation()
        if (existing != null) {
            if (!request.replaceActive) {
                throw ScheduleException("A training block is already active")
            }
            scheduleRepository.updateActivationHeader(
                existing.copy(
                    status = ActivationStatus.CANCELLED,
                    endedAtMillis = now,
                    revision = existing.revision + 1
                )
            )
        }

        val activationId = scheduleRepository.insertActivation(
            TrainingActivation(
                templateId = request.templateId,
                templateRevision = request.templateRevision,
                sourcePlanId = request.sourcePlanId,
                name = request.name,
                createdAtMillis = now,
                startEpochDay = request.startEpochDay,
                mode = request.mode,
                weekdays = request.weekdays,
                status = ActivationStatus.ACTIVE,
                weekNumber = request.weekNumber,
                cycleNumber = request.cycleNumber,
                workouts = workouts
            )
        )
        val saved = scheduleRepository.getActivation(activationId)
            ?: throw ScheduleException("The activation could not be reloaded")
        val occurrences = saved.workouts.sortedBy { it.position }.mapIndexed { index, workout ->
            WorkoutOccurrence(
                activationId = activationId,
                activationWorkoutId = workout.id,
                queuePosition = index,
                scheduledEpochDay = dates.getOrNull(index),
                status = OccurrenceStatus.PENDING,
                entries = workout.entries.sortedBy { it.position }.map { entry ->
                    OccurrenceEntry(
                        sourceActivationEntryId = entry.id,
                        position = entry.position,
                        exerciseId = entry.exerciseId,
                        exerciseName = entry.exerciseName,
                        movementPattern = entry.movementPattern,
                        requiredEquipment = entry.requiredEquipment,
                        involvements = entry.involvements,
                        isUnilateral = entry.isUnilateral,
                        sets = entry.sets,
                        reps = entry.reps,
                        weightKg = entry.weightKg
                    )
                }
            )
        }
        val inserted = scheduleRepository.insertOccurrences(occurrences)
        scheduleRepository.setScheduleState(
            WorkoutScheduleState(
                activeActivationId = activationId,
                selectedOccurrenceId = inserted.minByOrNull { it.queuePosition }?.id,
                legacyFallbackEnabled = false
            )
        )
        return activationId
    }
}

/**
 * Snapshots a routine template's prescriptions from the current catalog and activates it. Names,
 * movement patterns, equipment and muscle mappings are frozen, so later catalog edits cannot change
 * what was activated.
 */
class ActivateRoutineUseCase(
    private val catalog: ExerciseCatalog,
    private val createActivation: CreateTrainingActivationUseCase
) {
    suspend operator fun invoke(template: RoutineTemplate, request: ActivationRequest): Long {
        if (template.workouts.isEmpty()) {
            throw ScheduleException(
                "A routine needs at least one workout"
            )
        }
        val byId = catalog.all().associateBy { it.id }
        val workouts = template.workouts.sortedBy { it.position }.map { workout ->
            ActivationWorkout(
                position = workout.position,
                name = workout.name,
                focus = workout.focus,
                entries = workout.entries.sortedBy { it.position }.map { entry ->
                    val exercise = byId[entry.exerciseId]
                    ActivationEntry(
                        position = entry.position,
                        exerciseId = entry.exerciseId,
                        exerciseName = exercise?.name ?: entry.exerciseId,
                        movementPattern = exercise?.movementPattern ?: MovementPattern.CORE,
                        requiredEquipment = exercise?.requiredEquipment ?: emptySet(),
                        involvements = exercise?.effectiveInvolvements,
                        isUnilateral = exercise?.isUnilateral ?: false,
                        sets = entry.sets,
                        reps = entry.reps,
                        weightKg = entry.weightKg
                    )
                }
            )
        }
        val provenance = request.copy(
            templateId = request.templateId ?: template.id.takeIf { it != 0L },
            templateRevision =
            request.templateRevision ?: template.revision.takeIf { template.id != 0L },
            sourcePlanId = request.sourcePlanId ?: template.sourcePlanId
        )
        return createActivation(workouts, provenance)
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
