package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest

class AcceptWeeklyPlanUseCaseTest {

    @Test
    fun snapshotsExerciseNamesAndMovementPatternsFromTheCatalog() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = AcceptWeeklyPlanUseCase(repository, FakeCatalog, TimeProvider { 42L })

        useCase(
            WeeklyPlan(
                engine = PlannerEngineId.GEMINI_API,
                weekNumber = 3,
                cycleNumber = 2,
                days = listOf(
                    WorkoutDay(
                        dayIndex = 0,
                        focus = SplitFocus.PUSH,
                        exercises = listOf(
                            PlannedExercise(
                                exerciseId = "bench-press",
                                sets = 4,
                                reps = 8,
                                suggestedWeightKg = 82.5
                            )
                        )
                    )
                )
            )
        )

        val accepted = requireNotNull(repository.stored)
        assertEquals(PlannerEngineId.GEMINI_API, accepted.engine)
        assertEquals(42L, accepted.acceptedAtMillis)
        assertEquals(3, accepted.weekNumber)
        assertEquals(2, accepted.cycleNumber)
        val exercise = accepted.days.single().exercises.single()
        assertEquals("bench-press", exercise.exerciseId)
        assertEquals("Bench Press", exercise.name)
        assertEquals(4, exercise.sets)
        assertEquals(8, exercise.reps)
        assertEquals(MovementPattern.HORIZONTAL_PUSH, exercise.movementPattern)
        assertEquals(82.5, exercise.suggestedWeightKg)
    }

    @Test
    fun fallsBackToTheIdWhenTheExerciseIsNotInTheCatalog() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = AcceptWeeklyPlanUseCase(repository, FakeCatalog, TimeProvider { 0L })

        useCase(
            WeeklyPlan(
                engine = PlannerEngineId.LOCAL_LLM,
                days = listOf(
                    WorkoutDay(
                        dayIndex = 0,
                        focus = SplitFocus.PUSH,
                        exercises = listOf(PlannedExercise("ghost", sets = 3, reps = 10))
                    )
                )
            )
        )

        val exercise = requireNotNull(repository.stored).days.single().exercises.single()
        assertEquals("ghost", exercise.name)
        assertEquals(MovementPattern.CORE, exercise.movementPattern)
    }

    @Test
    fun observeExposesTheLatestAcceptedPlan() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = ObserveAcceptedPlanUseCase(repository)

        assertTrue(useCase().first() == null)

        repository.stored = AcceptedPlan(PlannerEngineId.DETERMINISTIC, 1L, emptyList())

        assertEquals(1L, requireNotNull(useCase().first()).acceptedAtMillis)
    }

    private class FakePlanHistoryRepository : PlanHistoryRepository {
        private val state = MutableStateFlow<AcceptedPlan?>(null)

        var stored: AcceptedPlan?
            get() = state.value
            set(value) {
                state.value = value
            }

        override fun observeLatest(): Flow<AcceptedPlan?> = state

        override fun observeHistory(): Flow<List<AcceptedPlan>> = state.map { listOfNotNull(it) }

        override suspend fun latest(): AcceptedPlan? = state.value

        override suspend fun accept(plan: AcceptedPlan) {
            state.value = plan
        }

        override suspend fun substitute(
            planId: Long,
            dayIndex: Int,
            position: Int,
            newExerciseId: String,
            newExerciseName: String,
            newWeightKg: Double?,
            newLoadCapability: ExerciseLoadCapability,
            newLoadKind: LoadKind
        ) = Unit

        override suspend fun delete(planId: Long) {
            if (state.value?.id == planId) state.value = null
        }

        override suspend fun clear() {
            state.value = null
        }
    }

    private object FakeCatalog : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = listOf(
            Exercise(
                id = "bench-press",
                name = "Bench Press",
                requiredEquipment = setOf(EquipmentTag.BARBELL),
                primaryMuscles = setOf(MuscleGroup.CHEST_UPPER),
                movementPattern = MovementPattern.HORIZONTAL_PUSH
            )
        )
    }
}
