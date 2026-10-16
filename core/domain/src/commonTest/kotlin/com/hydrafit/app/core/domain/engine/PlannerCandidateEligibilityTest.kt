package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExercisePreference
import com.hydrafit.app.core.domain.equipment.MovementPattern
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class PlannerCandidateEligibilityTest {
    private val catalog = listOf(
        Exercise(
            "press",
            "Press",
            setOf(EquipmentTag.BARBELL),
            emptySet(),
            movementPattern = MovementPattern.HORIZONTAL_PUSH
        ),
        Exercise(
            "squat",
            "Squat",
            setOf(EquipmentTag.BARBELL),
            emptySet(),
            movementPattern = MovementPattern.SQUAT
        )
    )
    private fun request() = PlanRequest(
        daysPerWeek = 4,
        availableEquipment = setOf(EquipmentTag.BARBELL),
        muscleFatigue = emptyMap(),
        nowMillis = 0L
    )

    @Test
    fun manualOnlyIdentitiesCannotOptIntoPlanningThroughOverridesOrPreference() {
        val ids = setOf(
            "dumbbell-shrug",
            "ez-bar-shrug",
            "trap-bar-shrug",
            "barbell-shrug",
            "forehead-neck-bridge",
            "self-resisted-neck-lateral-flexion"
        )
        val overridden = ids.map { catalog.first().copy(id = it) }
        val preferred = request().copy(
            exercisePreferences = ids.associateWith { ExercisePreference.PREFER }
        )
        assertEquals(
            catalog,
            PlannerCandidateEligibility.candidates(catalog + overridden, preferred)
        )
    }

    @Test
    fun manualOnlyPatternsExcludeCustomProfilesWithoutHidingOtherExercises() {
        val manual = listOf(MovementPattern.TRAP_ISOLATION, MovementPattern.NECK_ISOLATION)
            .map { catalog.first().copy(id = "custom-$it", movementPattern = it, isCustom = true) }
        assertEquals(catalog, PlannerCandidateEligibility.candidates(catalog + manual, request()))
        assertFailsWith<PlanGenerationException> {
            PlannerCandidateEligibility.requireWorkouts(manual, request())
        }
    }

    @Test
    fun aSingleEmptyRequestedWorkoutFailsEvenWhenAnotherHasWork() {
        val failure = assertFailsWith<PlanGenerationException> {
            PlannerCandidateEligibility.requireWorkouts(
                catalog,
                request().copy(
                    excludedExerciseIds = setOf("squat"),
                    exercisePreferences = mapOf("squat" to ExercisePreference.PREFER)
                )
            )
        }
        assertEquals(PlanFailureReason.NO_ELIGIBLE_EXERCISES, failure.reason)
        assertFalse(failure.transient)
    }

    @Test
    fun equipmentCanCauseInfeasibilityWithoutAnyExclusion() {
        assertFailsWith<PlanGenerationException> {
            PlannerCandidateEligibility.requireWorkouts(
                catalog,
                request().copy(availableEquipment = emptySet())
            )
        }
    }

    @Test
    fun sparseButEligibleWorkoutsDoNotRequireArmTargetsOrExerciseCounts() {
        PlannerCandidateEligibility.requireWorkouts(catalog, request())
        assertEquals(catalog, PlannerCandidateEligibility.candidates(catalog, request()))
        assertEquals(
            listOf(catalog.first()),
            PlannerCandidateEligibility.candidates(
                catalog,
                request().copy(excludedExerciseIds = setOf("squat"))
            )
        )
    }
}
