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
    fun libraryOnlySeededIdsAreNeverPlannedEvenWhenPreferredOrAvailable() {
        val libraryOnly = PlannerLibraryOnlyExercises.ids.map { catalog.first().copy(id = it) }
        val preferred = request().copy(
            exercisePreferences = PlannerLibraryOnlyExercises.ids
                .associateWith { ExercisePreference.PREFER }
        )
        assertEquals(
            catalog,
            PlannerCandidateEligibility.candidates(catalog + libraryOnly, preferred)
        )
    }

    @Test
    fun customExercisesStayPlannableBesideTheLibraryOnlyList() {
        val custom = catalog.first().copy(id = "custom-1", name = "My Press", isCustom = true)
        assertEquals(
            catalog + custom,
            PlannerCandidateEligibility.candidates(catalog + custom, request())
        )
    }

    @Test
    fun manualOnlyPatternsExcludeCustomProfilesWithoutHidingOtherExercises() {
        val manual = listOf(MovementPattern.NECK_ISOLATION)
            .map { catalog.first().copy(id = "custom-$it", movementPattern = it, isCustom = true) }
        assertEquals(catalog, PlannerCandidateEligibility.candidates(catalog + manual, request()))
        assertFailsWith<PlanGenerationException> {
            PlannerCandidateEligibility.requireWorkouts(manual, request())
        }
    }

    @Test
    fun shrugsAndTrapProfilesAreEligibleForPlanning() {
        val shrug = catalog.first().copy(
            id = "barbell-shrug",
            movementPattern = MovementPattern.TRAP_ISOLATION
        )
        val customTrap = catalog.first().copy(
            id = "custom-trap",
            movementPattern = MovementPattern.TRAP_ISOLATION,
            isCustom = true
        )
        assertEquals(
            catalog + shrug + customTrap,
            PlannerCandidateEligibility.candidates(catalog + shrug + customTrap, request())
        )
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
