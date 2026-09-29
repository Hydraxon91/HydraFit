package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.time.TimeProvider
import kotlinx.coroutines.flow.Flow

/**
 * Persists a generated plan as history only when the user accepts it. Exercise names and movement
 * patterns are snapshotted from the catalog at acceptance time.
 */
class AcceptWeeklyPlanUseCase(
    private val repository: PlanHistoryRepository,
    private val catalog: ExerciseCatalog,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(plan: WeeklyPlan) {
        val byId = catalog.all().associateBy { it.id }
        repository.accept(
            AcceptedPlan(
                engine = plan.engine,
                acceptedAtMillis = timeProvider.nowMillis(),
                weekNumber = plan.weekNumber,
                cycleNumber = plan.cycleNumber,
                days = plan.days.map { day ->
                    AcceptedDay(
                        dayIndex = day.dayIndex,
                        focus = day.focus,
                        exercises = day.exercises.map { planned ->
                            val exercise = byId[planned.exerciseId]
                            AcceptedExercise(
                                exerciseId = planned.exerciseId,
                                sets = planned.sets,
                                reps = planned.reps,
                                name = exercise?.name ?: planned.exerciseId,
                                movementPattern = exercise?.movementPattern ?: MovementPattern.CORE,
                                suggestedWeightKg = planned.suggestedWeightKg
                            )
                        }
                    )
                }
            )
        )
    }
}

class ObserveAcceptedPlanUseCase(private val repository: PlanHistoryRepository) {
    operator fun invoke(): Flow<AcceptedPlan?> = repository.observeLatest()
}
