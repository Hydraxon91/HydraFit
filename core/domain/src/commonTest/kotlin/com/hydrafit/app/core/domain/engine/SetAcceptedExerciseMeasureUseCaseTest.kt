package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.routine.PrescriptionBounds
import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class SetAcceptedExerciseMeasureUseCaseTest {

    @Test
    fun persistsTheRepsMeasureOnTheSlot() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = SetAcceptedExerciseMeasureUseCase(repository)

        val updated = useCase(plan(), dayIndex = 0, position = 0, reps = 12, durationSeconds = null)

        assertEquals(12, updated?.reps)
        assertNull(updated?.durationSeconds)
        assertEquals(listOf(MeasureCall(1L, 0, 0, 12, null)), repository.calls)
    }

    @Test
    fun persistsTheSecondsMeasureOnTheSlot() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = SetAcceptedExerciseMeasureUseCase(repository)

        val updated = useCase(plan(), dayIndex = 0, position = 0, reps = null, durationSeconds = 45)

        assertNull(updated?.reps)
        assertEquals(45, updated?.durationSeconds)
        assertEquals(listOf(MeasureCall(1L, 0, 0, null, 45)), repository.calls)
    }

    @Test
    fun missingDayOrPositionIsANoOp() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = SetAcceptedExerciseMeasureUseCase(repository)

        assertNull(useCase(plan(), dayIndex = 3, position = 0, reps = 10, durationSeconds = null))
        assertNull(useCase(plan(), dayIndex = 0, position = 5, reps = 10, durationSeconds = null))
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun rejectsBothOrNeitherMeasure() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = SetAcceptedExerciseMeasureUseCase(repository)

        assertFailsWith<IllegalArgumentException> {
            useCase(plan(), 0, 0, reps = 10, durationSeconds = 45)
        }
        assertFailsWith<IllegalArgumentException> {
            useCase(plan(), 0, 0, reps = null, durationSeconds = null)
        }
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun rejectsOutOfBoundsMeasure() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = SetAcceptedExerciseMeasureUseCase(repository)

        assertFailsWith<IllegalArgumentException> {
            useCase(plan(), 0, 0, reps = PrescriptionBounds.MAX_REPS + 1, durationSeconds = null)
        }
        assertFailsWith<IllegalArgumentException> {
            useCase(plan(), 0, 0, reps = 0, durationSeconds = null)
        }
        assertFailsWith<IllegalArgumentException> {
            useCase(plan(), 0, 0, reps = null, durationSeconds = 0)
        }
        assertTrue(repository.calls.isEmpty())
    }

    private fun plan() = AcceptedPlan(
        engine = PlannerEngineId.DETERMINISTIC,
        acceptedAtMillis = 0L,
        id = 1L,
        days = listOf(
            AcceptedDay(
                dayIndex = 0,
                focus = SplitFocus.PUSH,
                exercises = listOf(
                    AcceptedExercise(
                        exerciseId = "barbell-bench",
                        sets = 4,
                        reps = 6,
                        name = "Barbell Bench",
                        movementPattern = MovementPattern.HORIZONTAL_PUSH,
                        suggestedWeightKg = 82.5
                    )
                )
            )
        )
    )

    private data class MeasureCall(
        val planId: Long,
        val dayIndex: Int,
        val position: Int,
        val reps: Int?,
        val durationSeconds: Int?
    )

    private class FakePlanHistoryRepository : PlanHistoryRepository {
        val calls = mutableListOf<MeasureCall>()

        override fun observeLatest(): Flow<AcceptedPlan?> = flowOf(null)

        override fun observeHistory(): Flow<List<AcceptedPlan>> = flowOf(emptyList())

        override suspend fun latest(): AcceptedPlan? = null

        override suspend fun accept(plan: AcceptedPlan) = Unit

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

        override suspend fun setMeasure(
            planId: Long,
            dayIndex: Int,
            position: Int,
            reps: Int?,
            durationSeconds: Int?
        ) {
            calls += MeasureCall(planId, dayIndex, position, reps, durationSeconds)
        }

        override suspend fun delete(planId: Long) = Unit

        override suspend fun clear() = Unit
    }
}
