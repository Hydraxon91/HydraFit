package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WarmupGuidanceTest {

    @Test
    fun alwaysOffersEasyGeneralMovementAsATimedItemWithoutReps() {
        val guidance = buildWarmupGuidance(occurrenceWith())

        val general = guidance.preparation.single()
        assertEquals(WarmupAction.EASY_GENERAL_MOVEMENT, general.action)
        assertEquals(null, general.reps)
        assertEquals(180, general.durationSeconds)
    }

    @Test
    fun selectsPreparationFromThePatternsActuallyTrained() {
        val legDay = buildWarmupGuidance(occurrenceWith(entry(0, MovementPattern.SQUAT)))
        assertEquals(
            listOf(WarmupAction.EASY_GENERAL_MOVEMENT, WarmupAction.LOWER_BODY_PRACTICE),
            legDay.preparation.map { it.action }
        )

        val pushDay = buildWarmupGuidance(occurrenceWith(entry(0, MovementPattern.HORIZONTAL_PUSH)))
        assertEquals(
            listOf(
                WarmupAction.EASY_GENERAL_MOVEMENT,
                WarmupAction.SHOULDER_CIRCLES,
                WarmupAction.LIGHT_PUSH_PRACTICE
            ),
            pushDay.preparation.map { it.action }
        )

        val pullDay = buildWarmupGuidance(occurrenceWith(entry(0, MovementPattern.VERTICAL_PULL)))
        assertEquals(
            listOf(
                WarmupAction.EASY_GENERAL_MOVEMENT,
                WarmupAction.SHOULDER_CIRCLES,
                WarmupAction.LIGHT_PULL_PRACTICE
            ),
            pullDay.preparation.map { it.action }
        )
    }

    @Test
    fun offersShoulderCirclesOnlyForUpperBodyWork() {
        val legOnly = buildWarmupGuidance(occurrenceWith(entry(0, MovementPattern.HINGE)))
        assertTrue(legOnly.preparation.none { it.action == WarmupAction.SHOULDER_CIRCLES })
    }

    @Test
    fun practiceItemsCarryEasyRepsAndNoDuration() {
        val guidance = buildWarmupGuidance(occurrenceWith(entry(0, MovementPattern.SQUAT)))

        val practice = guidance.preparation.first { it.action == WarmupAction.LOWER_BODY_PRACTICE }
        assertEquals(10, practice.reps)
        assertEquals(null, practice.durationSeconds)
    }

    @Test
    fun rampsAnExternalTargetAtTheProfilePercentages() {
        val guidance =
            buildWarmupGuidance(occurrenceWith(entry(0, MovementPattern.SQUAT, weightKg = 100.0)))

        val ramp = guidance.ramps.single()
        assertEquals(0L, ramp.occurrenceEntryId)
        assertEquals(100.0, ramp.workingWeightKg)
        assertEquals(listOf(40, 60, 80), ramp.rampSets.map { it.percentOfWorkingWeight })
        assertEquals(listOf(8, 5, 3), ramp.rampSets.map { it.reps })
        assertEquals(listOf(40.0, 60.0, 80.0), ramp.rampSets.map { it.weightKg })
        assertTrue(ramp.rampSets.all { it.weightKg < ramp.workingWeightKg })
    }

    @Test
    fun roundsRampLoadsToThePlateIncrement() {
        val guidance =
            buildWarmupGuidance(occurrenceWith(entry(0, MovementPattern.SQUAT, weightKg = 30.0)))

        assertEquals(listOf(12.5, 17.5, 25.0), guidance.ramps.single().rampSets.map { it.weightKg })
    }

    @Test
    fun aTinyWorkingWeightYieldsNoRampInsteadOfZeroOrDuplicateLoads() {
        val guidance =
            buildWarmupGuidance(occurrenceWith(entry(0, MovementPattern.SQUAT, weightKg = 2.5)))

        assertTrue(guidance.ramps.isEmpty())
    }

    @Test
    fun skipsBodyweightAddedAndUnknownLoadTargets() {
        val bodyweight = buildWarmupGuidance(
            occurrenceWith(
                entry(
                    0,
                    MovementPattern.SQUAT,
                    weightKg = null,
                    capability = ExerciseLoadCapability.BODYWEIGHT_ONLY,
                    loadKind = LoadKind.BODYWEIGHT
                )
            )
        )
        val added = buildWarmupGuidance(
            occurrenceWith(
                entry(
                    0,
                    MovementPattern.SQUAT,
                    weightKg = 10.0,
                    capability = ExerciseLoadCapability.BODYWEIGHT_ADDABLE,
                    loadKind = LoadKind.ADDED
                )
            )
        )
        val unknown = buildWarmupGuidance(
            occurrenceWith(
                entry(
                    0,
                    MovementPattern.SQUAT,
                    weightKg = 40.0,
                    capability = ExerciseLoadCapability.UNSPECIFIED,
                    loadKind = LoadKind.LEGACY_UNSPECIFIED
                )
            )
        )

        assertTrue(bodyweight.ramps.isEmpty())
        assertTrue(added.ramps.isEmpty())
        assertTrue(unknown.ramps.isEmpty())
    }

    @Test
    fun suggestsOneRampPerMovementPatternForItsFirstRampableEntry() {
        val guidance = buildWarmupGuidance(
            occurrenceWith(
                entry(
                    0,
                    MovementPattern.HORIZONTAL_PUSH,
                    exerciseId = "bench-press",
                    weightKg = 80.0
                ),
                entry(
                    1,
                    MovementPattern.HORIZONTAL_PUSH,
                    exerciseId = "incline-press",
                    weightKg = 60.0
                ),
                entry(2, MovementPattern.SQUAT, exerciseId = "back-squat", weightKg = 100.0)
            )
        )

        assertEquals(listOf("bench-press", "back-squat"), guidance.ramps.map { it.exerciseId })
    }

    @Test
    fun letsALaterEntryOfTheSamePatternRampWhenTheFirstHasNoLoad() {
        val guidance = buildWarmupGuidance(
            occurrenceWith(
                entry(
                    0,
                    MovementPattern.HORIZONTAL_PUSH,
                    exerciseId = "push-up",
                    weightKg = null,
                    capability = ExerciseLoadCapability.BODYWEIGHT_ONLY,
                    loadKind = LoadKind.BODYWEIGHT
                ),
                entry(
                    1,
                    MovementPattern.HORIZONTAL_PUSH,
                    exerciseId = "bench-press",
                    weightKg = 80.0
                )
            )
        )

        assertEquals(listOf("bench-press"), guidance.ramps.map { it.exerciseId })
    }

    @Test
    fun ordersRampsByEntryPositionNotStorageOrder() {
        val guidance = buildWarmupGuidance(
            occurrenceWith(
                entry(1, MovementPattern.SQUAT, exerciseId = "front-squat", weightKg = 60.0),
                entry(0, MovementPattern.HINGE, exerciseId = "deadlift", weightKg = 120.0)
            )
        )

        assertEquals(listOf(0L, 1L), guidance.ramps.map { it.occurrenceEntryId })
    }

    @Test
    fun aRampPlanMatchesOnlyTheExactPrescriptionItWasBuiltFrom() {
        val entry = entry(0, MovementPattern.SQUAT, weightKg = 100.0)
        val plan = buildWarmupGuidance(occurrenceWith(entry)).ramps.single()

        assertTrue(plan.matchesPrescription(entry))
        assertFalse(plan.matchesPrescription(entry.copy(exerciseId = "front-squat")))
        assertFalse(plan.matchesPrescription(entry.copy(reps = 5)))
        assertFalse(plan.matchesPrescription(entry.copy(weightKg = 90.0)))
        assertFalse(plan.matchesPrescription(entry.copy(movementPattern = MovementPattern.HINGE)))
        assertFalse(
            plan.matchesPrescription(
                entry.copy(
                    loadCapability = ExerciseLoadCapability.BODYWEIGHT_ONLY,
                    loadKind = LoadKind.BODYWEIGHT,
                    weightKg = null
                )
            )
        )
    }

    private fun occurrenceWith(vararg entries: OccurrenceEntry) = WorkoutOccurrence(
        id = 30L,
        activationId = 5L,
        activationWorkoutId = 9L,
        queuePosition = 0,
        entries = entries.toList()
    )

    private fun entry(
        position: Int,
        movementPattern: MovementPattern,
        exerciseId: String = "back-squat",
        weightKg: Double? = 100.0,
        capability: ExerciseLoadCapability = ExerciseLoadCapability.EXTERNAL,
        loadKind: LoadKind = LoadKind.EXTERNAL
    ) = OccurrenceEntry(
        id = position.toLong(),
        position = position,
        exerciseId = exerciseId,
        exerciseName = exerciseId,
        movementPattern = movementPattern,
        sets = 3,
        reps = 8,
        weightKg = weightKg,
        loadCapability = capability,
        loadKind = loadKind
    )
}
