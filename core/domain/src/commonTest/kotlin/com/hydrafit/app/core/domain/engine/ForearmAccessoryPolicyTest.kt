package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExercisePreference
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.equipment.MovementPatternGuardrail
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForearmAccessoryPolicyTest {
    private val wrist = Exercise(
        id = "wrist",
        name = "Wrist curl",
        requiredEquipment = setOf(EquipmentTag.DUMBBELL, EquipmentTag.BENCH),
        primaryMuscles = setOf(MuscleGroup.FOREARMS),
        movementPattern = MovementPattern.FOREARM_ISOLATION,
        isUnilateral = true,
        involvements = mapOf(MuscleGroup.FOREARMS to 1.0)
    )
    private val engine = DeterministicWorkoutPlannerEngine(object : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = listOf(wrist)
    })

    @Test
    fun forearmsUseOnlyTheApprovedAccessoryPoolsAndNeverCountAsDirectArms() {
        val expected = setOf(SplitFocus.PULL, SplitFocus.UPPER, SplitFocus.FULL_BODY)
        SplitFocus.entries.forEach { focus ->
            assertEquals(
                focus in expected,
                MovementPattern.FOREARM_ISOLATION in DirectArmCoverage.isolationPatternsFor(focus)
            )
        }
        assertFalse(MovementPattern.FOREARM_ISOLATION.isCompound)
        assertFalse(DirectArmCoverage.qualifies(wrist, MuscleGroup.BICEPS))
        assertFalse(DirectArmCoverage.qualifies(wrist, MuscleGroup.TRICEPS))
        assertFalse(
            MovementPatternGuardrail.conflicts(wrist.movementPattern, wrist.effectiveInvolvements)
        )
        assertTrue(
            MovementPatternGuardrail.conflicts(
                wrist.movementPattern,
                mapOf(MuscleGroup.BICEPS to 1.0)
            )
        )
    }

    @Test
    fun newAccessoryIsSelectedWithItsOwnSetCountAndKeepsTheHardGates() {
        val request = PlanRequest(
            daysPerWeek = 2,
            availableEquipment = wrist.requiredEquipment,
            muscleFatigue = emptyMap(),
            nowMillis = 0L,
            accessorySetsPerExercise = 2,
            exercisePreferences = mapOf(wrist.id to ExercisePreference.PREFER)
        )
        val planned = engine.plan(request, listOf(wrist)).days.first().exercises.single()
        assertEquals(wrist.id, planned.exerciseId)
        assertEquals(2, planned.sets)
        listOf(
            request.copy(availableEquipment = setOf(EquipmentTag.DUMBBELL)),
            request.copy(excludedExerciseIds = setOf(wrist.id)),
            request.copy(muscleFatigue = mapOf(MuscleGroup.FOREARMS to 1.0))
        ).forEach { constrained ->
            assertTrue(engine.plan(constrained, listOf(wrist)).days.all { it.exercises.isEmpty() })
        }
    }
}
