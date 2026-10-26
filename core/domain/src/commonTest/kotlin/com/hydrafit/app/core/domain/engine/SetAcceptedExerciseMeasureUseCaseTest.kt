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
        assertEquals(listOf(MeasureCall(1L, 0, 0, 12, null, null)), repository.calls)
    }

    @Test
    fun persistsTheSecondsMeasureOnTheSlot() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = SetAcceptedExerciseMeasureUseCase(repository)

        val updated = useCase(plan(), dayIndex = 0, position = 0, reps = null, durationSeconds = 45)

        assertNull(updated?.reps)
        assertEquals(45, updated?.durationSeconds)
        assertEquals(listOf(MeasureCall(1L, 0, 0, null, 45, null)), repository.calls)
    }

    @Test
    fun persistsTheDistanceMeasureOnTheSlot() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = SetAcceptedExerciseMeasureUseCase(repository)

        val updated = useCase(
            plan(),
            dayIndex = 0,
            position = 0,
            reps = null,
            durationSeconds = null,
            distanceMeters = 400
        )

        assertNull(updated?.reps)
        assertNull(updated?.durationSeconds)
        assertEquals(400, updated?.distanceMeters)
        assertEquals(listOf(MeasureCall(1L, 0, 0, null, null, 400)), repository.calls)
    }

    @Test
    fun rejectsDistanceMixedWithAnotherMeasure() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = SetAcceptedExerciseMeasureUseCase(repository)

        assertFailsWith<IllegalArgumentException> {
            useCase(plan(), 0, 0, reps = 10, durationSeconds = null, distanceMeters = 400)
        }
        assertFailsWith<IllegalArgumentException> {
            useCase(plan(), 0, 0, reps = null, durationSeconds = 45, distanceMeters = 400)
        }
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun rejectsOutOfBoundsDistance() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = SetAcceptedExerciseMeasureUseCase(repository)

        assertFailsWith<IllegalArgumentException> {
            useCase(
                plan(),
                0,
                0,
                reps = null,
                durationSeconds = null,
                distanceMeters = PrescriptionBounds.MAX_DISTANCE_METERS + 1
            )
        }
        assertTrue(repository.calls.isEmpty())
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

    @Test
    fun switchingToForceClearsTheNumericLoadAndTheRepTargets() = runTest {
        val weighted = plan().copy(
            days = listOf(
                plan().days.single().copy(
                    exercises = listOf(
                        plan().days.single().exercises.single().copy(
                            setTargets = SlotSetTargets(
                                targets = listOf(
                                    SetTarget(reps = 6, weightKg = 82.5),
                                    SetTarget(reps = 6, weightKg = 82.5),
                                    SetTarget(reps = 6, weightKg = 82.5),
                                    SetTarget(reps = 6, weightKg = 82.5)
                                )
                            )
                        )
                    )
                )
            )
        )

        val updated = SetAcceptedExerciseMeasureUseCase(FakePlanHistoryRepository())(
            weighted,
            dayIndex = 0,
            position = 0,
            reps = null,
            durationSeconds = null,
            forceNewtons = 250.0
        )

        assertEquals(250.0, updated?.forceNewtons)
        // Force is load-free and ordered targets are rep work, so neither survives the change.
        assertNull(updated?.suggestedWeightKg)
        assertNull(updated?.setTargets)
    }

    @Test
    fun aRepsMeasureKeepsTheLoadAndTargetsWhileAnotherNonRepsMeasureDropsTheTargets() = runTest {
        val targets = SlotSetTargets(targets = listOf(SetTarget(reps = 6, weightKg = 82.5)))
        val weighted = plan().copy(
            days = listOf(
                plan().days.single().copy(
                    exercises = listOf(
                        plan().days.single().exercises.single().copy(setTargets = targets)
                    )
                )
            )
        )

        val timed = SetAcceptedExerciseMeasureUseCase(FakePlanHistoryRepository())(
            weighted,
            dayIndex = 0,
            position = 0,
            reps = null,
            durationSeconds = 45
        )
        assertEquals(82.5, timed?.suggestedWeightKg)
        assertNull(timed?.setTargets)

        val reps = SetAcceptedExerciseMeasureUseCase(FakePlanHistoryRepository())(
            weighted,
            dayIndex = 0,
            position = 0,
            reps = 8,
            durationSeconds = null
        )
        assertEquals(82.5, reps?.suggestedWeightKg)
        assertEquals(targets, reps?.setTargets)
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
        val durationSeconds: Int?,
        val distanceMeters: Int?,
        val forceNewtons: Double? = null
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
            durationSeconds: Int?,
            distanceMeters: Int?,
            forceNewtons: Double?
        ) {
            calls += MeasureCall(
                planId,
                dayIndex,
                position,
                reps,
                durationSeconds,
                distanceMeters,
                forceNewtons
            )
        }

        override suspend fun delete(planId: Long) = Unit

        override suspend fun clear() = Unit
    }
}
