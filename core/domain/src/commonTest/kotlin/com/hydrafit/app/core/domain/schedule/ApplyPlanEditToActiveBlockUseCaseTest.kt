package com.hydrafit.app.core.domain.schedule

import com.hydrafit.app.core.domain.engine.AcceptedDay
import com.hydrafit.app.core.domain.engine.AcceptedExercise
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.ExerciseTargetExplanation
import com.hydrafit.app.core.domain.engine.FatigueSetEffect
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SetCountExplanation
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ApplyPlanEditToActiveBlockUseCaseTest {

    private val block = RecordingBlock()
    private val useCase = ApplyPlanEditToActiveBlockUseCase(
        catalog = object : ExerciseCatalog {
            override suspend fun all(): List<Exercise> = listOf(incline)
        },
        block = block
    )

    @Test
    fun anEditTargetsTheBlockWorkoutInSortedDayOrderWithTheCatalogIdentity() = runTest {
        val edited = slot("incline-press").copy(reps = 10, explanation = explanation)

        val changed = useCase(plan(), dayIndex = 4, position = 1, edited = edited)

        assertEquals(2, changed)
        val call = block.calls.single()
        assertEquals(PLAN_ID, call.sourcePlanId)
        // Days 1 and 4 become block workouts 0 and 1, as the routine conversion numbers them.
        assertEquals(1, call.workoutPosition)
        assertEquals("row", call.previousExerciseId)
        val prescription = call.prescription
        assertEquals(1, prescription.position)
        assertEquals("incline-press", prescription.exerciseId)
        assertEquals("Incline Press", prescription.exerciseName)
        assertEquals(MovementPattern.HORIZONTAL_PUSH, prescription.movementPattern)
        assertEquals(
            setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH),
            prescription.requiredEquipment
        )
        assertEquals(10, prescription.reps)
        assertEquals(3, prescription.sets)
        assertEquals(explanation, prescription.explanation)
    }

    @Test
    fun anUnsavedDraftAMissingSlotOrAnUnknownExerciseWritesNothing() = runTest {
        assertEquals(0, useCase(plan().copy(id = 0L), 4, 1, slot("incline-press")))
        assertEquals(0, useCase(plan(), dayIndex = 2, position = 0, edited = slot("incline-press")))
        assertEquals(0, useCase(plan(), dayIndex = 4, position = 7, edited = slot("incline-press")))
        assertEquals(0, useCase(plan(), dayIndex = 4, position = 1, edited = slot("unknown")))
        assertTrue(block.calls.isEmpty())
    }

    private fun plan() = AcceptedPlan(
        engine = PlannerEngineId.DETERMINISTIC,
        acceptedAtMillis = 0L,
        id = PLAN_ID,
        days = listOf(
            AcceptedDay(4, SplitFocus.PULL, listOf(slot("pull-up"), slot("row"))),
            AcceptedDay(1, SplitFocus.PUSH, listOf(slot("bench-press")))
        )
    )

    private fun slot(id: String) = AcceptedExercise(
        exerciseId = id,
        sets = 3,
        reps = 8,
        name = id,
        movementPattern = MovementPattern.HORIZONTAL_PUSH,
        suggestedWeightKg = 50.0
    )

    private val incline = Exercise(
        id = "incline-press",
        name = "Incline Press",
        requiredEquipment = setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH),
        primaryMuscles = setOf(MuscleGroup.CHEST_UPPER),
        movementPattern = MovementPattern.HORIZONTAL_PUSH
    )

    private val explanation = ExerciseTargetExplanation(
        setCount = SetCountExplanation(
            baseSets = 3,
            deloadApplied = false,
            deloadVolumeScale = 0.7,
            fatigueEffect = FatigueSetEffect.NONE,
            finalSets = 3
        )
    )

    private class RecordingBlock : PlanSlotBlockRepository {
        data class Call(
            val sourcePlanId: Long,
            val workoutPosition: Int,
            val previousExerciseId: String,
            val prescription: ActivationEntry
        )

        val calls = mutableListOf<Call>()

        override suspend fun applyPlanSlotEdit(
            sourcePlanId: Long,
            workoutPosition: Int,
            previousExerciseId: String,
            prescription: ActivationEntry
        ): Int {
            calls += Call(sourcePlanId, workoutPosition, previousExerciseId, prescription)
            return 2
        }
    }

    private companion object {
        const val PLAN_ID = 9L
    }
}
