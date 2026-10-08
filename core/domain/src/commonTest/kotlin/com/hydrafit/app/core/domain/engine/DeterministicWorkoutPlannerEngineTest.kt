package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.FatigueCalculator
import com.hydrafit.app.core.domain.fatigue.FatigueReplayFixture
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.fatigue.MuscleTarget
import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.math.nextDown
import kotlin.math.nextUp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeterministicWorkoutPlannerEngineTest {

    private val engine = DeterministicWorkoutPlannerEngine(EmptyCatalog)
    private val everything = setOf(
        EquipmentTag.BARBELL,
        EquipmentTag.DUMBBELL,
        EquipmentTag.BENCH,
        EquipmentTag.PULL_UP_BAR
    )

    @Test
    fun reportsDeterministicEngineId() {
        val plan = engine.plan(request(daysPerWeek = 2), catalog())

        assertEquals(PlannerEngineId.DETERMINISTIC, plan.engine)
    }

    @Test
    fun producesRequestedNumberOfDays() {
        val plan = engine.plan(request(daysPerWeek = 4, equipment = everything), catalog())

        assertEquals(4, plan.days.size)
        assertEquals(listOf(0, 1, 2, 3), plan.days.map { it.dayIndex })
    }

    @Test
    fun rejectsDaysOutsideSupportedRange() {
        assertFailsWith<IllegalArgumentException> {
            engine.plan(request(daysPerWeek = 1), catalog())
        }
        assertFailsWith<IllegalArgumentException> {
            engine.plan(request(daysPerWeek = 7), catalog())
        }
    }

    @Test
    fun rejectsSetsOutsideSupportedRange() {
        assertFailsWith<IllegalArgumentException> {
            engine.plan(request(daysPerWeek = 2, setsPerExercise = 0), catalog())
        }
        assertFailsWith<IllegalArgumentException> {
            engine.plan(request(daysPerWeek = 2, setsPerExercise = 9), catalog())
        }
    }

    @Test
    fun autoSelectsFullBodyForTwoOrThreeDays() {
        val focuses = engine.plan(request(daysPerWeek = 2), catalog()).days.map { it.focus }

        assertEquals(listOf(SplitFocus.FULL_BODY, SplitFocus.FULL_BODY), focuses)
    }

    @Test
    fun autoSelectsUpperLowerForFourDays() {
        val focuses = engine.plan(request(daysPerWeek = 4), catalog()).days.map { it.focus }

        assertEquals(
            listOf(SplitFocus.UPPER, SplitFocus.LOWER, SplitFocus.UPPER, SplitFocus.LOWER),
            focuses
        )
    }

    @Test
    fun autoSelectsPushPullLegsForFiveDays() {
        val focuses = engine.plan(request(daysPerWeek = 5), catalog()).days.map { it.focus }

        assertEquals(
            listOf(
                SplitFocus.PUSH,
                SplitFocus.PULL,
                SplitFocus.LEGS,
                SplitFocus.PUSH,
                SplitFocus.PULL
            ),
            focuses
        )
    }

    @Test
    fun explicitPreferenceOverridesAutoSelection() {
        val focuses = engine
            .plan(request(daysPerWeek = 3, split = SplitType.PUSH_PULL_LEGS), catalog())
            .days
            .map { it.focus }

        assertEquals(listOf(SplitFocus.PUSH, SplitFocus.PULL, SplitFocus.LEGS), focuses)
    }

    @Test
    fun excludesExercisesWhoseEquipmentIsUnavailable() {
        val exercises = listOf(
            exercise("squat", MovementPattern.SQUAT, required = setOf(EquipmentTag.BARBELL)),
            exercise("goblet-squat", MovementPattern.SQUAT, required = setOf(EquipmentTag.DUMBBELL))
        )

        val plan = engine.plan(
            request(daysPerWeek = 2, equipment = setOf(EquipmentTag.DUMBBELL)),
            exercises
        )

        val plannedIds = plan.days.flatMap { day -> day.exercises.map { it.exerciseId } }.toSet()
        assertEquals(setOf("goblet-squat"), plannedIds)
    }

    @Test
    fun prefersBarbellBenchWhenAvailable() {
        val exercises = listOf(
            exercise(
                "barbell-bench-press",
                MovementPattern.HORIZONTAL_PUSH,
                MuscleGroup.CHEST_UPPER,
                setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH)
            ),
            exercise(
                "dumbbell-bench-press",
                MovementPattern.HORIZONTAL_PUSH,
                MuscleGroup.CHEST_UPPER,
                setOf(EquipmentTag.DUMBBELL, EquipmentTag.BENCH)
            ),
            exercise("push-up", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER)
        )

        val plan = engine.plan(request(daysPerWeek = 2, equipment = everything), exercises)

        val pushIds = plan.days.flatMap { it.exercises.map { it.exerciseId } }
        assertTrue("barbell-bench-press" in pushIds)
    }

    @Test
    fun prefersDumbbellBenchWhenBarbellIsUnavailable() {
        val exercises = listOf(
            exercise(
                "barbell-bench-press",
                MovementPattern.HORIZONTAL_PUSH,
                MuscleGroup.CHEST_UPPER,
                setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH)
            ),
            exercise(
                "dumbbell-bench-press",
                MovementPattern.HORIZONTAL_PUSH,
                MuscleGroup.CHEST_UPPER,
                setOf(EquipmentTag.DUMBBELL, EquipmentTag.BENCH)
            )
        )

        val plan = engine.plan(
            request(
                daysPerWeek = 2,
                equipment = setOf(EquipmentTag.DUMBBELL, EquipmentTag.BENCH)
            ),
            exercises
        )

        val pushIds = plan.days.flatMap { it.exercises.map { it.exerciseId } }
        assertTrue("dumbbell-bench-press" in pushIds)
    }

    @Test
    fun fallsBackToBodyweightWhenNothingElseIsAvailable() {
        val exercises = listOf(
            exercise(
                "barbell-bench-press",
                MovementPattern.HORIZONTAL_PUSH,
                MuscleGroup.CHEST_UPPER,
                setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH)
            ),
            exercise("push-up", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER)
        )

        val plan = engine.plan(request(daysPerWeek = 2, equipment = emptySet()), exercises)

        val pushIds = plan.days.flatMap { it.exercises.map { it.exerciseId } }
        assertEquals(listOf("push-up"), pushIds)
    }

    @Test
    fun defersTrapsFromWeeklyVolumeDeficitTargeting() {
        // A trapezius-only accessory has no weekly-volume deficit of its own (TRAPS is excluded from
        // planner targeting, since it overlaps UPPER_BACK), so it is not pulled in to chase volume.
        val trapsOnly = Exercise(
            id = "shrug",
            name = "shrug",
            requiredEquipment = emptySet(),
            primaryMuscles = setOf(MuscleGroup.TRAPS),
            movementPattern = MovementPattern.SHOULDER_ISOLATION,
            involvements = mapOf(MuscleGroup.TRAPS to 1.0)
        )

        val plan = engine.plan(
            request(daysPerWeek = 2, equipment = everything),
            richCatalog() + trapsOnly
        )
        val ids = plan.days.flatMap { it.exercises.map { it.exerciseId } }

        assertFalse("shrug" in ids)
    }

    @Test
    fun neverRepeatsAPatternWithinADay() {
        val exercises = listOf(
            exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER),
            exercise("push-up", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER)
        )

        val plan = engine.plan(
            request(daysPerWeek = 3, split = SplitType.PUSH_PULL_LEGS, equipment = everything),
            exercises
        )

        val pushDay = plan.days.first { it.focus == SplitFocus.PUSH }
        assertEquals(listOf("bench-press"), pushDay.exercises.map { it.exerciseId })
    }

    @Test
    fun fullBodyDaysUseDifferentTemplates() {
        val plan = engine.plan(
            request(daysPerWeek = 3, equipment = everything),
            catalog()
        )

        val idsByDay = plan.days.map { day -> day.exercises.map { it.exerciseId } }
        assertTrue(idsByDay[0].isNotEmpty())
        assertTrue(idsByDay[0] != idsByDay[1])
        assertTrue(idsByDay[1] != idsByDay[2])
    }

    @Test
    fun prefersExercisesWhosePrimaryMusclesAreLessFatigued() {
        val exercises = listOf(
            exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER),
            exercise("push-up", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.SIDE_DELTS)
        )

        val plan = engine.plan(
            request(
                daysPerWeek = 3,
                split = SplitType.PUSH_PULL_LEGS,
                fatigue = mapOf(MuscleGroup.CHEST_UPPER to 0.6)
            ),
            exercises
        )

        val pushIds = plan.days.first {
            it.focus == SplitFocus.PUSH
        }.exercises.map { it.exerciseId }
        assertEquals(listOf("push-up"), pushIds)
    }

    @Test
    fun prefersAFreshAlternativeOverASorePreferredEquipment() {
        val exercises = listOf(
            exercise(
                "barbell-bench-press",
                MovementPattern.HORIZONTAL_PUSH,
                MuscleGroup.CHEST_UPPER,
                setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH)
            ),
            exercise("push-up", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.SIDE_DELTS)
        )

        val plan = engine.plan(
            request(
                daysPerWeek = 3,
                split = SplitType.PUSH_PULL_LEGS,
                equipment = everything,
                fatigue = mapOf(MuscleGroup.CHEST_UPPER to 0.6)
            ),
            exercises
        )

        val pushIds = plan.days.first { it.focus == SplitFocus.PUSH }
            .exercises.map { it.exerciseId }
        assertEquals(listOf("push-up"), pushIds)
    }

    @Test
    fun rotatesAwayFromThePreviousAcceptedExerciseWhenAnEquivalentExists() {
        val plan = engine.plan(
            request(
                daysPerWeek = 3,
                split = SplitType.PUSH_PULL_LEGS,
                recentExerciseIdsByPattern = mapOf(
                    MovementPattern.HORIZONTAL_PUSH to setOf("bench-press")
                )
            ),
            listOf(
                exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER),
                exercise("push-up", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER)
            )
        )

        val pushIds = plan.days.first { it.focus == SplitFocus.PUSH }
            .exercises.map { it.exerciseId }
        assertEquals(listOf("push-up"), pushIds)
    }

    @Test
    fun repeatsThePreviousExerciseWhenNoEquivalentAlternativeExists() {
        val plan = engine.plan(
            request(
                daysPerWeek = 3,
                split = SplitType.PUSH_PULL_LEGS,
                recentExerciseIdsByPattern = mapOf(
                    MovementPattern.HORIZONTAL_PUSH to setOf("bench-press")
                )
            ),
            listOf(
                exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER)
            )
        )

        val pushIds = plan.days.first { it.focus == SplitFocus.PUSH }
            .exercises.map { it.exerciseId }
        assertEquals(listOf("bench-press"), pushIds)
    }

    @Test
    fun doesNotRepeatACompoundAcrossDaysThatShareTheSamePattern() {
        val plan = engine.plan(
            request(daysPerWeek = 3),
            listOf(
                exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER),
                exercise("push-up", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER)
            )
        )

        val horizontalPushes = plan.days
            .flatMap { day -> day.exercises.map { it.exerciseId } }
            .filter { it == "bench-press" || it == "push-up" }
        assertEquals(
            horizontalPushes.distinct().size,
            horizontalPushes.size,
            "a compound must not repeat across days"
        )
    }

    @Test
    fun dropsASharedCompoundRatherThanRepeatingItWhenNoAlternativeExists() {
        val plan = engine.plan(
            request(daysPerWeek = 3),
            listOf(
                exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER)
            )
        )

        val appearances = plan.days.sumOf { day ->
            day.exercises.count { it.exerciseId == "bench-press" }
        }
        assertEquals(1, appearances)
    }

    @Test
    fun skipsExercisesAboveTheFatigueSkipThreshold() {
        val plan = engine.plan(
            request(
                daysPerWeek = 3,
                split = SplitType.PUSH_PULL_LEGS,
                fatigue = mapOf(MuscleGroup.CHEST_UPPER to 0.9)
            ),
            listOf(
                exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER)
            )
        )

        val pushDay = plan.days.first { it.focus == SplitFocus.PUSH }
        assertTrue(pushDay.exercises.isEmpty())
    }

    @Test
    fun picksTheNextCandidateWhenTheTopRankedOneIsSore() {
        // "sore" has the lower weighted fatigue (0.7 x 0.85 = 0.595) so it sorts first, but its raw
        // targeted fatigue (0.85) is above the skip threshold. "fresh" is heavier-weighted (1.0 x 0.7
        // = 0.70) but its raw targeted fatigue (0.70) is below the threshold, so it must be picked.
        val sore = Exercise(
            id = "sore-push",
            name = "sore-push",
            requiredEquipment = emptySet(),
            primaryMuscles = setOf(MuscleGroup.CHEST_UPPER),
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            involvements = mapOf(MuscleGroup.CHEST_UPPER to 0.7)
        )
        val fresh = Exercise(
            id = "fresh-push",
            name = "fresh-push",
            requiredEquipment = emptySet(),
            primaryMuscles = setOf(MuscleGroup.TRICEPS),
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            involvements = mapOf(MuscleGroup.TRICEPS to 1.0)
        )
        val plan = engine.plan(
            request(
                daysPerWeek = 3,
                split = SplitType.PUSH_PULL_LEGS,
                fatigue = mapOf(MuscleGroup.CHEST_UPPER to 0.85, MuscleGroup.TRICEPS to 0.7)
            ),
            listOf(sore, fresh)
        )

        val push = plan.days.first { it.focus == SplitFocus.PUSH }

        assertEquals("fresh-push", push.exercises.singleOrNull()?.exerciseId)
    }

    @Test
    fun reducesSetsWhenAPrimaryMuscleIsFatigued() {
        val plan = engine.plan(
            request(
                daysPerWeek = 3,
                split = SplitType.PUSH_PULL_LEGS,
                fatigue = mapOf(MuscleGroup.CHEST_UPPER to 0.7),
                setsPerExercise = 4
            ),
            listOf(
                exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER)
            )
        )

        val planned = plan.days.first { it.focus == SplitFocus.PUSH }.exercises.single()
        assertEquals(3, planned.sets)
    }

    @Test
    fun reductionAndSkipBoundariesUseUnroundedScores() {
        for ((fatigue, expectedSets) in listOf(
            0.65.nextDown() to 4,
            0.65 to 3,
            0.65.nextUp() to 3,
            0.80.nextDown() to 3,
            0.80 to null,
            0.80.nextUp() to null
        )) {
            val plan = engine.plan(
                request(
                    daysPerWeek = 3,
                    split = SplitType.PUSH_PULL_LEGS,
                    fatigue = mapOf(MuscleGroup.CHEST_UPPER to fatigue),
                    setsPerExercise = 4
                ),
                listOf(
                    exercise(
                        "bench-press",
                        MovementPattern.HORIZONTAL_PUSH,
                        MuscleGroup.CHEST_UPPER
                    )
                )
            )
            val picks = plan.days.first { it.focus == SplitFocus.PUSH }.exercises
            assertEquals(expectedSets, picks.singleOrNull()?.sets, "fatigue=$fatigue")
        }
    }

    @Test
    fun targetedInvolvementBoundaryUsesRawMaximumFatigue() {
        for ((weight, fatigue, expectedSets) in listOf(
            Triple(0.7.nextDown(), 0.8, 4),
            Triple(0.7, 0.65, 3),
            Triple(0.7, 0.8, null)
        )) {
            val candidate = exercise(
                "bench",
                MovementPattern.HORIZONTAL_PUSH,
                MuscleGroup.CHEST_UPPER
            )
                .copy(
                    involvements = mapOf(
                        MuscleGroup.CHEST_UPPER to 1.0,
                        MuscleGroup.SIDE_DELTS to weight
                    )
                )
            val plan = engine.plan(
                request(
                    daysPerWeek = 3,
                    split = SplitType.PUSH_PULL_LEGS,
                    fatigue = mapOf(
                        MuscleGroup.CHEST_UPPER to 0.1,
                        MuscleGroup.SIDE_DELTS to fatigue
                    ),
                    setsPerExercise = 4
                ),
                listOf(candidate)
            )
            val picks = plan.days.first { it.focus == SplitFocus.PUSH }.exercises
            assertEquals(expectedSets, picks.singleOrNull()?.sets)
        }
    }

    @Test
    fun replayBackExerciseIsReducedAtPeakButNotAtEvaluationAfterTheSplitReBaseline() {
        val calculator = FatigueCalculator()
        // With BACK split (LATS 0.50 / UPPER_BACK 0.35 / LOWER_BACK 0.15) the LATS peak is
        // ~0.6938 (>= 0.65 reduce, < 0.80 skip) and the evaluation is ~0.5487 (no reduction), so
        // this fixture no longer demonstrates the skip threshold.
        for ((instant, expectedSets) in listOf(
            FatigueReplayFixture.EVALUATION_MILLIS to 4,
            FatigueReplayFixture.PEAK_MILLIS to 3
        )) {
            val plan = engine.plan(
                request(
                    daysPerWeek = 3,
                    split = SplitType.PUSH_PULL_LEGS,
                    fatigue = calculator.calculate(FatigueReplayFixture.sets, instant),
                    setsPerExercise = 4
                ),
                listOf(exercise("row", MovementPattern.HORIZONTAL_PULL, MuscleGroup.LATS))
            )
            val picks = plan.days.first { it.focus == SplitFocus.PULL }.exercises
            assertEquals(expectedSets, picks.singleOrNull()?.sets)
        }
    }

    @Test
    fun candidateOrderingUsesTheNewNonlinearScoresAndMaximumWeightedInvolvement() {
        val sets = List(12) {
            LoggedSet(0L, listOf(MuscleTarget(MuscleGroup.CHEST_UPPER, 1.0)), reps = 8)
        } + List(8) {
            LoggedSet(0L, listOf(MuscleTarget(MuscleGroup.SIDE_DELTS, 1.0)), reps = 8)
        }
        val chest = exercise(
            "chest",
            MovementPattern.HORIZONTAL_PUSH,
            MuscleGroup.CHEST_UPPER
        ).copy(
            involvements = mapOf(MuscleGroup.CHEST_UPPER to 0.7, MuscleGroup.SIDE_DELTS to 0.3)
        )
        val shoulders = exercise(
            "shoulders",
            MovementPattern.HORIZONTAL_PUSH,
            MuscleGroup.SIDE_DELTS
        ).copy(involvements = mapOf(MuscleGroup.SIDE_DELTS to 1.0))
        for (candidates in listOf(listOf(chest, shoulders), listOf(shoulders, chest))) {
            val plan = engine.plan(
                request(
                    daysPerWeek = 3,
                    split = SplitType.PUSH_PULL_LEGS,
                    fatigue = FatigueCalculator().calculate(sets, 0L)
                ),
                candidates
            )
            // New: max(0.7 × 2/3, 0.3 × 4/7) < 4/7. Linear normalization chose shoulders.
            val picked = plan.days.first { it.focus == SplitFocus.PUSH }.exercises.single()
            assertEquals("chest", picked.exerciseId)
            assertEquals(2, picked.sets)
        }
    }

    @Test
    fun honorsRequestedSetsCount() {
        val plan = engine.plan(
            request(daysPerWeek = 3, split = SplitType.PUSH_PULL_LEGS, setsPerExercise = 5),
            listOf(
                exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER)
            )
        )

        val planned = plan.days.first { it.focus == SplitFocus.PUSH }.exercises.single()
        assertEquals(5, planned.sets)
    }

    @Test
    fun usesCompoundRepsForCompoundPatternsAndHigherRepsForIsolation() {
        val plan = engine.plan(
            request(daysPerWeek = 3, split = SplitType.PUSH_PULL_LEGS),
            listOf(
                exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER),
                exercise("pushdown", MovementPattern.TRICEPS_ISOLATION, MuscleGroup.TRICEPS)
            )
        )

        val push = plan.days.first { it.focus == SplitFocus.PUSH }.exercises.associateBy {
            it.exerciseId
        }
        assertEquals(
            DeterministicWorkoutPlannerEngine.COMPOUND_REPS,
            push.getValue("bench-press").reps
        )
        assertEquals(
            DeterministicWorkoutPlannerEngine.ISOLATION_REPS,
            push.getValue("pushdown").reps
        )
    }

    @Test
    fun appliesGoalSpecificRepTargetsAndDefaultSets() {
        TrainingGoal.entries.forEach { goal ->
            val plan = engine.plan(
                request(daysPerWeek = 3, split = SplitType.PUSH_PULL_LEGS, goal = goal),
                listOf(
                    exercise(
                        "bench-press",
                        MovementPattern.HORIZONTAL_PUSH,
                        MuscleGroup.CHEST_UPPER
                    ),
                    exercise("pushdown", MovementPattern.TRICEPS_ISOLATION, MuscleGroup.TRICEPS)
                )
            )
            val push = plan.days.first { it.focus == SplitFocus.PUSH }.exercises.associateBy {
                it.exerciseId
            }

            assertEquals(goal.defaultSets, push.getValue("bench-press").sets)
            assertEquals(goal.compoundReps, push.getValue("bench-press").reps)
            assertEquals(goal.accessorySets, push.getValue("pushdown").sets)
            assertEquals(goal.isolationReps, push.getValue("pushdown").reps)
        }
    }

    @Test
    fun usesTheAccessorySetCountForAccessorySlotsOnly() {
        val plan = engine.plan(
            request(
                daysPerWeek = 3,
                split = SplitType.PUSH_PULL_LEGS,
                setsPerExercise = 5,
                accessorySetsPerExercise = 2
            ),
            listOf(
                exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER),
                exercise("pushdown", MovementPattern.TRICEPS_ISOLATION, MuscleGroup.TRICEPS)
            )
        )
        val push = plan.days.first { it.focus == SplitFocus.PUSH }.exercises.associateBy {
            it.exerciseId
        }

        assertEquals(5, push.getValue("bench-press").sets)
        assertEquals(2, push.getValue("pushdown").sets)
    }

    @Test
    fun derivesTheWorkingWeightFromTheOneRepMaxAtThePlannedReps() {
        val plan = engine.plan(
            request(
                daysPerWeek = 3,
                split = SplitType.PUSH_PULL_LEGS,
                suggestedWeightsKg = mapOf("bench-press" to 82.5)
            ),
            listOf(
                exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER),
                exercise("push-up", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER)
            )
        )

        val benchPress = plan.days.first { it.focus == SplitFocus.PUSH }
            .exercises.single { it.exerciseId == "bench-press" }

        // Balanced compound at the default 3 sets -> 6 reps -> NSCA 85% x 0.9 buffer = 76.5%
        // 82.5 x 0.765 = 63.1125 -> nearest 2.5 = 62.5
        assertEquals(6, benchPress.reps)
        assertEquals(62.5, benchPress.suggestedWeightKg)
    }

    @Test
    fun setOverrideChangesVolumeNotTheRepBand() {
        val plan = engine.plan(
            request(
                daysPerWeek = 3,
                split = SplitType.PUSH_PULL_LEGS,
                setsPerExercise = 6,
                suggestedWeightsKg = mapOf("bench-press" to 100.0)
            ),
            listOf(
                exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER)
            )
        )

        val benchPress = plan.days.first { it.focus == SplitFocus.PUSH }.exercises.single()

        // The 6 sets carry the volume; reps stay at the goal's compound band (6).
        // 100 x intensity(6 reps) = 100 x 0.765 = 76.5 -> nearest 2.5 = 77.5
        assertEquals(6, benchPress.sets)
        assertEquals(TrainingGoal.BALANCED.compoundReps, benchPress.reps)
        assertEquals(77.5, benchPress.suggestedWeightKg)
    }

    @Test
    fun clampsTheSuggestedWeightToTheEquipmentMaximum() {
        val plan = engine.plan(
            request(
                daysPerWeek = 3,
                split = SplitType.PUSH_PULL_LEGS,
                equipment = setOf(EquipmentTag.CABLE_MACHINE),
                suggestedWeightsKg = mapOf("cable-press" to 200.0),
                equipmentMaxWeights = mapOf(EquipmentTag.CABLE_MACHINE to 100.0)
            ),
            listOf(
                exercise(
                    "cable-press",
                    MovementPattern.HORIZONTAL_PUSH,
                    MuscleGroup.CHEST_UPPER,
                    setOf(EquipmentTag.CABLE_MACHINE)
                )
            )
        )

        val press = plan.days.first { it.focus == SplitFocus.PUSH }
            .exercises.single { it.exerciseId == "cable-press" }

        // 200 -> 152.5 before the cap; the machine maxes at 100 kg.
        assertEquals(100.0, press.suggestedWeightKg)
    }

    @Test
    fun ordersCandidatesByWeightedFatigue() {
        val evenlyLoaded = Exercise(
            id = "even",
            name = "even",
            requiredEquipment = emptySet(),
            primaryMuscles = setOf(MuscleGroup.CHEST_UPPER),
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            involvements = mapOf(MuscleGroup.CHEST_UPPER to 1.0, MuscleGroup.SIDE_DELTS to 1.0)
        )
        val lightlyLoaded = Exercise(
            id = "light",
            name = "light",
            requiredEquipment = emptySet(),
            primaryMuscles = setOf(MuscleGroup.SIDE_DELTS),
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            involvements = mapOf(MuscleGroup.CHEST_UPPER to 0.3)
        )
        val plan = engine.plan(
            request(
                daysPerWeek = 3,
                split = SplitType.PUSH_PULL_LEGS,
                fatigue = mapOf(MuscleGroup.CHEST_UPPER to 0.4, MuscleGroup.SIDE_DELTS to 0.4)
            ),
            listOf(evenlyLoaded, lightlyLoaded)
        )

        val push = plan.days.first { it.focus == SplitFocus.PUSH }

        // even = 0.4; light = 0.3 x 0.4 = 0.12 -> the lightly loaded option wins.
        assertEquals("light", push.exercises.first().exerciseId)
    }

    @Test
    fun aLowWeightMuscleDoesNotTriggerTheSorenessSkip() {
        val exercise = Exercise(
            id = "bench",
            name = "bench",
            requiredEquipment = emptySet(),
            primaryMuscles = setOf(MuscleGroup.SIDE_DELTS),
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            involvements = mapOf(MuscleGroup.CHEST_UPPER to 1.0, MuscleGroup.SIDE_DELTS to 0.3)
        )
        val plan = engine.plan(
            request(
                daysPerWeek = 3,
                split = SplitType.PUSH_PULL_LEGS,
                fatigue = mapOf(MuscleGroup.SIDE_DELTS to 0.9)
            ),
            listOf(exercise)
        )

        val push = plan.days.first { it.focus == SplitFocus.PUSH }

        // A 0.3-weight shoulder is below the 0.7 "targeted" threshold, so the sore shoulder does
        // not skip the exercise (only its 1.0 chest matters, and that is fresh).
        assertTrue(push.exercises.any { it.exerciseId == "bench" })
    }

    @Test
    fun omitsTheWeightWhenTheRequestHasNoOneRepMax() {
        val plan = engine.plan(
            request(daysPerWeek = 3, split = SplitType.PUSH_PULL_LEGS),
            listOf(
                exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER)
            )
        )

        val benchPress = plan.days.first { it.focus == SplitFocus.PUSH }.exercises.single()
        assertEquals(null, benchPress.suggestedWeightKg)
    }

    @Test
    fun doesNotGenerateLoadForABodyweightOnlyExerciseEvenWithABaseline() {
        val plan = engine.plan(
            request(
                daysPerWeek = 2,
                suggestedWeightsKg = mapOf("ab-roll" to 32.5)
            ),
            listOf(
                exercise(
                    "ab-roll",
                    MovementPattern.CORE,
                    MuscleGroup.ABS,
                    loadCapability = ExerciseLoadCapability.BODYWEIGHT_ONLY
                )
            )
        )

        val entry = plan.days.flatMap { it.exercises }.first { it.exerciseId == "ab-roll" }
        assertNull(entry.suggestedWeightKg)
        assertEquals(LoadKind.BODYWEIGHT, entry.loadKind)
        assertEquals(ExerciseLoadCapability.BODYWEIGHT_ONLY, entry.loadCapability)
    }

    @Test
    fun keepsTheGeneratedLoadForAnExternalExercise() {
        val plan = engine.plan(
            request(
                daysPerWeek = 3,
                split = SplitType.PUSH_PULL_LEGS,
                suggestedWeightsKg = mapOf("bench-press" to 100.0)
            ),
            listOf(
                exercise(
                    "bench-press",
                    MovementPattern.HORIZONTAL_PUSH,
                    MuscleGroup.CHEST_UPPER,
                    loadCapability = ExerciseLoadCapability.EXTERNAL
                )
            )
        )

        val entry = plan.days.flatMap { it.exercises }.first { it.exerciseId == "bench-press" }
        assertTrue((entry.suggestedWeightKg ?: 0.0) > 0.0)
        assertEquals(LoadKind.EXTERNAL, entry.loadKind)
    }

    @Test
    fun deloadWeekReducesSetsAndWeight() {
        val plan = engine.plan(
            request(
                daysPerWeek = 3,
                split = SplitType.PUSH_PULL_LEGS,
                setsPerExercise = 4,
                suggestedWeightsKg = mapOf("bench-press" to 100.0),
                isDeload = true
            ),
            listOf(
                exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER)
            )
        )

        val benchPress = plan.days.first { it.focus == SplitFocus.PUSH }.exercises.single()

        // 4 sets x 0.7 = 2.8 -> 3 sets; reps via volume-aware for 3 sets -> 6 (compound volume 18/3)
        assertEquals(3, benchPress.sets)
        // 100 x intensity(6 reps) x 0.8 = 100 x 0.765 x 0.8 = 61.2 -> nearest 2.5 = 60.0
        assertEquals(60.0, benchPress.suggestedWeightKg)
    }

    @Test
    fun nonDeloadWeekIsUnchanged() {
        val plan = engine.plan(
            request(
                daysPerWeek = 3,
                split = SplitType.PUSH_PULL_LEGS,
                setsPerExercise = 4,
                suggestedWeightsKg = mapOf("bench-press" to 100.0)
            ),
            listOf(
                exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER)
            )
        )

        val benchPress = plan.days.first { it.focus == SplitFocus.PUSH }.exercises.single()
        assertEquals(4, benchPress.sets)
        // 100 x intensity(6 reps) = 100 x 0.765 = 76.5 -> nearest 2.5 = 77.5
        assertEquals(77.5, benchPress.suggestedWeightKg)
    }

    @Test
    fun isDeterministicForTheSameInput() {
        val exercises = catalog()
        val request = request(
            daysPerWeek = 4,
            equipment = everything,
            recentExerciseIdsByPattern = mapOf(
                MovementPattern.HORIZONTAL_PUSH to setOf("bench-press")
            )
        )

        assertEquals(engine.plan(request, exercises), engine.plan(request, exercises))
    }

    @Test
    fun returnsDaysWithNoExercisesWhenNothingMatchesTheFocus() {
        val plan = engine.plan(
            request(daysPerWeek = 3, split = SplitType.PUSH_PULL_LEGS),
            listOf(exercise("plank", MovementPattern.CORE))
        )

        val byFocus = plan.days.associateBy { it.focus }
        assertTrue(byFocus.getValue(SplitFocus.PUSH).exercises.isEmpty())
        assertTrue(byFocus.getValue(SplitFocus.PULL).exercises.isEmpty())
        assertEquals(
            listOf("plank"),
            byFocus.getValue(SplitFocus.LEGS).exercises.map {
                it.exerciseId
            }
        )
    }

    @Test
    fun keepsEveryDayWithinTheTargetExerciseCountRange() {
        val plan = engine.plan(request(daysPerWeek = 3, equipment = everything), richCatalog())

        val minimum = PlannerExerciseCounts.TARGET_MIN_PER_DAY
        val maximum = PlannerExerciseCounts.TARGET_MAX_PER_DAY
        val sizes = plan.days.map { it.exercises.size }
        assertTrue(sizes.all { it in minimum..maximum }, "sizes=$sizes")
    }

    @Test
    fun neverExceedsTheTargetMaximumPerDayEvenWithAPlentifulCatalog() {
        val plan = engine.plan(request(daysPerWeek = 6, equipment = everything), richCatalog())

        assertTrue(
            plan.days.all { it.exercises.size <= PlannerExerciseCounts.TARGET_MAX_PER_DAY }
        )
    }

    @Test
    fun placesCompoundsBeforeIsolationsInEveryDay() {
        val catalog = richCatalog()
        val plan = engine.plan(request(daysPerWeek = 3, equipment = everything), catalog)

        val byId = catalog.associateBy { it.id }
        plan.days.forEach { day ->
            val patterns = day.exercises.mapNotNull { byId[it.exerciseId]?.movementPattern }
            val firstIsolation = patterns.indexOfFirst { !it.isCompound }
            if (firstIsolation >= 0) {
                assertTrue(
                    patterns.drop(firstIsolation).none { it.isCompound },
                    "day ${day.dayIndex} ordering=$patterns"
                )
            }
        }
    }

    @Test
    fun prefersTheIsolationOnTheMuscleWithTheLargerRemainingDeficit() {
        val bench = exercise("bench", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER)
            .copy(involvements = mapOf(MuscleGroup.CHEST_UPPER to 1.0, MuscleGroup.TRICEPS to 0.5))
        val pushdown = exercise(
            "a-pushdown",
            MovementPattern.TRICEPS_ISOLATION,
            MuscleGroup.TRICEPS,
            setOf(EquipmentTag.BARBELL)
        )
        val shoulderFly = exercise(
            "z-fly",
            MovementPattern.SHOULDER_ISOLATION,
            MuscleGroup.SIDE_DELTS
        )
        val plan = engine.plan(
            request(
                daysPerWeek = 2,
                split = SplitType.FULL_BODY,
                equipment = setOf(EquipmentTag.BARBELL)
            ),
            listOf(bench, pushdown, shoulderFly)
        )

        // Unmet direct triceps coverage takes precedence over discretionary shoulder isolation.
        val day0 = plan.days.first().exercises.map { it.exerciseId }
        assertTrue(
            day0.indexOf("a-pushdown") in 0 until day0.indexOf("z-fly"),
            "day0=$day0"
        )
    }

    @Test
    fun providesDirectArmCoverageAcrossAllGoalsEvenWhenCompoundCreditsAreHigh() {
        val exercises = listOf(
            exercise("squat", MovementPattern.SQUAT, MuscleGroup.QUADS),
            exercise("press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER).copy(
                involvements = mapOf(
                    MuscleGroup.CHEST_UPPER to 1.0,
                    MuscleGroup.TRICEPS to 1.0
                )
            ),
            exercise("row", MovementPattern.HORIZONTAL_PULL, MuscleGroup.UPPER_BACK).copy(
                involvements = mapOf(
                    MuscleGroup.UPPER_BACK to 1.0,
                    MuscleGroup.BICEPS to 1.0
                )
            ),
            exercise("curl", MovementPattern.BICEPS_ISOLATION, MuscleGroup.BICEPS),
            exercise("extension", MovementPattern.TRICEPS_ISOLATION, MuscleGroup.TRICEPS)
        )

        TrainingGoal.entries.forEach { goal ->
            val plan = engine.plan(
                request(
                    daysPerWeek = 2,
                    split = SplitType.FULL_BODY,
                    equipment = everything,
                    goal = goal,
                    setsPerExercise = 8,
                    accessorySetsPerExercise = 2
                ),
                exercises
            )

            val biceps = plan.armCoverage.single { it.muscle == MuscleGroup.BICEPS }
            val triceps = plan.armCoverage.single { it.muscle == MuscleGroup.TRICEPS }
            assertEquals(4, biceps.directIsolationSets, "goal=$goal biceps=$biceps")
            assertEquals(4, triceps.directIsolationSets, "goal=$goal triceps=$triceps")
            assertEquals(8.0, biceps.estimatedOtherInvolvementCredits, "goal=$goal")
            assertEquals(8.0, triceps.estimatedOtherInvolvementCredits, "goal=$goal")
        }
    }

    @Test
    fun providesDirectArmCoverageAcrossSupportedTrainingFrequencies() {
        (2..6).forEach { daysPerWeek ->
            val plan = engine.plan(
                request(daysPerWeek = daysPerWeek, equipment = everything),
                richCatalog()
            )

            assertTrue(
                plan.armCoverage.all { it.directIsolationSets >= it.targetSets },
                "daysPerWeek=$daysPerWeek coverage=${plan.armCoverage}"
            )
        }
    }

    @Test
    fun selectedAccessorySetCountDeterminesWhetherCoverageFitsAvailableDays() {
        val exercises = listOf(
            exercise("squat", MovementPattern.SQUAT, MuscleGroup.QUADS),
            exercise("press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER),
            exercise("row", MovementPattern.HORIZONTAL_PULL, MuscleGroup.UPPER_BACK),
            exercise("curl", MovementPattern.BICEPS_ISOLATION, MuscleGroup.BICEPS),
            exercise("extension", MovementPattern.TRICEPS_ISOLATION, MuscleGroup.TRICEPS)
        )

        val lowSets = engine.plan(
            request(
                daysPerWeek = 2,
                split = SplitType.FULL_BODY,
                equipment = everything,
                accessorySetsPerExercise = 1
            ),
            exercises
        )
        val normalSets = engine.plan(
            request(
                daysPerWeek = 2,
                split = SplitType.FULL_BODY,
                equipment = everything,
                accessorySetsPerExercise = 2
            ),
            exercises
        )

        assertTrue(lowSets.armCoverage.any { it.directIsolationSets < it.targetSets })
        assertTrue(
            lowSets.armCoverage.all {
                it.directIsolationSets >= it.targetSets ||
                    it.unmetReason == ArmCoverageUnmetReason.NOT_MET_WITH_AVAILABLE_CANDIDATES
            }
        )
        assertTrue(normalSets.armCoverage.all { it.directIsolationSets >= it.targetSets })
        assertTrue(
            lowSets.days.all {
                it.exercises.size <= PlannerExerciseCounts.TARGET_MAX_PER_DAY
            }
        )
    }

    @Test
    fun customIsolationPatternMustInvolveTheMatchingArmMuscleToCount() {
        val mismatchedCurl = exercise(
            "misclassified-curl",
            MovementPattern.BICEPS_ISOLATION,
            MuscleGroup.TRICEPS
        )

        val plan = engine.plan(
            request(daysPerWeek = 2, split = SplitType.FULL_BODY, equipment = everything),
            listOf(mismatchedCurl)
        )

        assertEquals(
            0,
            plan.armCoverage.single { it.muscle == MuscleGroup.BICEPS }.directIsolationSets
        )
    }

    @Test
    fun reportsWhenEquipmentLeavesNoCompatibleDirectArmCandidate() {
        val plan = engine.plan(
            request(daysPerWeek = 2, split = SplitType.FULL_BODY, equipment = emptySet()),
            listOf(
                exercise(
                    "curl",
                    MovementPattern.BICEPS_ISOLATION,
                    MuscleGroup.BICEPS,
                    setOf(EquipmentTag.DUMBBELL)
                ),
                exercise(
                    "extension",
                    MovementPattern.TRICEPS_ISOLATION,
                    MuscleGroup.TRICEPS,
                    setOf(EquipmentTag.CABLE_MACHINE)
                )
            )
        )

        assertTrue(
            plan.armCoverage.all {
                it.unmetReason == ArmCoverageUnmetReason.NO_COMPATIBLE_AVAILABLE_CANDIDATE
            }
        )
    }

    @Test
    fun doesNotForceSoreCandidatesToMeetDirectArmCoverage() {
        val plan = engine.plan(
            request(
                daysPerWeek = 2,
                split = SplitType.FULL_BODY,
                equipment = everything,
                fatigue = mapOf(MuscleGroup.BICEPS to 0.9, MuscleGroup.TRICEPS to 0.9)
            ),
            listOf(
                exercise("curl", MovementPattern.BICEPS_ISOLATION, MuscleGroup.BICEPS),
                exercise("extension", MovementPattern.TRICEPS_ISOLATION, MuscleGroup.TRICEPS)
            )
        )

        assertTrue(
            plan.armCoverage.all {
                it.unmetReason ==
                    ArmCoverageUnmetReason.ALL_COMPATIBLE_CANDIDATES_SKIPPED_FOR_FATIGUE
            }
        )
        assertTrue(plan.days.flatMap { it.exercises }.isEmpty())
    }

    @Test
    fun marksNormalArmTargetsAsNotEnforcedDuringDeload() {
        val plan = engine.plan(
            request(daysPerWeek = 2, equipment = everything, isDeload = true),
            richCatalog()
        )

        assertTrue(plan.armCoverage.none { it.isTargetEnforced })
    }

    @Test
    fun stopsAddingAnExerciseOnceItsMusclesReachTheWeeklyCeiling() {
        val plan = engine.plan(
            request(
                daysPerWeek = 6,
                split = SplitType.FULL_BODY,
                goal = TrainingGoal.STRENGTH,
                accessorySetsPerExercise = 3
            ),
            listOf(exercise("curl", MovementPattern.BICEPS_ISOLATION, MuscleGroup.BICEPS))
        )

        val bicepsSets = plan.days.sumOf { day ->
            day.exercises.filter { it.exerciseId == "curl" }.sumOf { it.sets }
        }
        // Five 3-set days reach the STRENGTH ceiling of 15; the sixth day is refused.
        val ceiling = WeeklyVolumeTargets.forGoal(TrainingGoal.STRENGTH).maxSets
        assertEquals(ceiling, bicepsSets.toDouble())
    }

    @Test
    fun rankCandidatesOrdersByFatigueThenDeficitThenFreshness() {
        val target = WeeklyVolumeTargets.forGoal(TrainingGoal.BALANCED)
        val noVolume = MuscleGroup.entries.associateWith { 0.0 }

        // Fatigue dominates: the lower weighted fatigue sorts first.
        val highFatigue = exercise("high", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER)
        val lowFatigue = exercise("low", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.SIDE_DELTS)
        val byFatigue = engine.rankCandidates(
            candidates = listOf(highFatigue, lowFatigue),
            fatigue = mapOf(MuscleGroup.CHEST_UPPER to 0.6, MuscleGroup.SIDE_DELTS to 0.1),
            weekUsed = emptySet(),
            recentExerciseIdsByPattern = emptyMap(),
            weeklyVolume = noVolume,
            target = target
        )
        assertEquals(listOf("low", "high"), byFatigue.map { it.id })

        // With fatigue tied, the larger remaining deficit sorts first.
        val satisfied =
            exercise("satisfied", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER)
        val lacking = exercise("lacking", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.SIDE_DELTS)
        val volumes = noVolume.toMutableMap().apply {
            this[MuscleGroup.CHEST_UPPER] = target.targetSets
        }
        val byDeficit = engine.rankCandidates(
            candidates = listOf(satisfied, lacking),
            fatigue = emptyMap(),
            weekUsed = emptySet(),
            recentExerciseIdsByPattern = emptyMap(),
            weeklyVolume = volumes,
            target = target
        )
        assertEquals(listOf("lacking", "satisfied"), byDeficit.map { it.id })

        // With fatigue and deficit tied, an exercise not yet used this week sorts first.
        val used = exercise("used", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER)
        val fresh = exercise("fresh", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER)
        val byFreshness = engine.rankCandidates(
            candidates = listOf(used, fresh),
            fatigue = emptyMap(),
            weekUsed = setOf("used"),
            recentExerciseIdsByPattern = emptyMap(),
            weeklyVolume = noVolume,
            target = target
        )
        assertEquals(listOf("fresh", "used"), byFreshness.map { it.id })
    }

    @Test
    fun pickFirstNonSoreSkipsTargetedOverSkipThreshold() {
        val sore = Exercise(
            id = "sore",
            name = "sore",
            requiredEquipment = emptySet(),
            primaryMuscles = setOf(MuscleGroup.CHEST_UPPER),
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            involvements = mapOf(MuscleGroup.CHEST_UPPER to 1.0)
        )
        val fresh = Exercise(
            id = "fresh",
            name = "fresh",
            requiredEquipment = emptySet(),
            primaryMuscles = setOf(MuscleGroup.SIDE_DELTS),
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            involvements = mapOf(MuscleGroup.SIDE_DELTS to 1.0)
        )
        val fatigue = mapOf(MuscleGroup.CHEST_UPPER to 0.85, MuscleGroup.SIDE_DELTS to 0.5)

        assertEquals("fresh", engine.pickFirstNonSore(listOf(sore, fresh), fatigue)?.id)
        assertNull(engine.pickFirstNonSore(listOf(sore), fatigue))
    }

    @Test
    fun twoDayPlanOutputIsUnchangedAfterTheRankingExtraction() {
        val plan = engine.plan(request(daysPerWeek = 2, equipment = everything), catalog())

        assertEquals(
            "FULL_BODY[back-squat:3:6:null,bench-press:3:6:null,pull-up:3:6:null," +
                "curl:2:12:null,calf-raise:2:12:null,plank:2:12:null];" +
                "FULL_BODY[rdl:3:6:null,ohp:3:6:null,barbell-row:3:6:null," +
                "curl:2:12:null,calf-raise:2:12:null,plank:2:12:null]",
            render(plan)
        )
    }

    @Test
    fun fourDayPlanOutputIsUnchangedAfterTheRankingExtraction() {
        val plan = engine.plan(request(daysPerWeek = 4, equipment = everything), catalog())

        assertEquals(
            "UPPER[bench-press:3:6:null,ohp:3:6:null,barbell-row:3:6:null,pull-up:3:6:null," +
                "curl:2:12:null];" +
                "LOWER[back-squat:3:6:null,rdl:3:6:null,calf-raise:2:12:null,plank:2:12:null];" +
                "UPPER[curl:2:12:null];" +
                "LOWER[goblet-squat:3:6:null,calf-raise:2:12:null,plank:2:12:null]",
            render(plan)
        )
    }

    private fun render(plan: WeeklyPlan): String = plan.days.joinToString(";") { day ->
        day.focus.name + "[" + day.exercises.joinToString(",") { exercise ->
            "${exercise.exerciseId}:${exercise.sets}:${exercise.reps}:${exercise.suggestedWeightKg}"
        } + "]"
    }

    private fun request(
        daysPerWeek: Int,
        equipment: Set<EquipmentTag> = emptySet(),
        fatigue: Map<MuscleGroup, Double> = emptyMap(),
        split: SplitType = SplitType.AUTO,
        goal: TrainingGoal = TrainingGoal.BALANCED,
        setsPerExercise: Int = goal.defaultSets,
        accessorySetsPerExercise: Int = goal.accessorySets,
        recentExerciseIdsByPattern: Map<MovementPattern, Set<String>> = emptyMap(),
        suggestedWeightsKg: Map<String, Double> = emptyMap(),
        equipmentMaxWeights: Map<EquipmentTag, Double> = emptyMap(),
        isDeload: Boolean = false
    ) = PlanRequest(
        daysPerWeek = daysPerWeek,
        availableEquipment = equipment,
        muscleFatigue = fatigue,
        splitPreference = split,
        nowMillis = 0L,
        goal = goal,
        setsPerExercise = setsPerExercise,
        accessorySetsPerExercise = accessorySetsPerExercise,
        recentExerciseIdsByPattern = recentExerciseIdsByPattern,
        suggestedWeightsKg = suggestedWeightsKg,
        equipmentMaxWeights = equipmentMaxWeights,
        isDeload = isDeload
    )

    private fun exercise(
        id: String,
        pattern: MovementPattern,
        primary: MuscleGroup = MuscleGroup.ABS,
        required: Set<EquipmentTag> = emptySet(),
        loadCapability: ExerciseLoadCapability = ExerciseLoadCapability.EXTERNAL
    ) = Exercise(
        id = id,
        name = id,
        requiredEquipment = required,
        primaryMuscles = setOf(primary),
        movementPattern = pattern,
        loadCapability = loadCapability
    )

    private fun catalog(): List<Exercise> = listOf(
        exercise(
            "back-squat",
            MovementPattern.SQUAT,
            MuscleGroup.QUADS,
            setOf(EquipmentTag.BARBELL)
        ),
        exercise(
            "goblet-squat",
            MovementPattern.SQUAT,
            MuscleGroup.QUADS,
            setOf(EquipmentTag.DUMBBELL)
        ),
        exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER),
        exercise("barbell-row", MovementPattern.HORIZONTAL_PULL, MuscleGroup.LATS),
        exercise("plank", MovementPattern.CORE),
        exercise("rdl", MovementPattern.HINGE, MuscleGroup.HAMSTRINGS),
        exercise("ohp", MovementPattern.VERTICAL_PUSH, MuscleGroup.SIDE_DELTS),
        exercise(
            "pull-up",
            MovementPattern.VERTICAL_PULL,
            MuscleGroup.LATS,
            setOf(EquipmentTag.PULL_UP_BAR)
        ),
        exercise("calf-raise", MovementPattern.CALF_RAISE, MuscleGroup.CALVES),
        exercise("lunge", MovementPattern.LUNGE, MuscleGroup.QUADS),
        exercise("curl", MovementPattern.BICEPS_ISOLATION, MuscleGroup.BICEPS)
    )

    /** One exercise per major pattern (plus every isolation family) so a week can fill each day. */
    private fun richCatalog(): List<Exercise> = listOf(
        exercise("squat", MovementPattern.SQUAT, MuscleGroup.QUADS),
        exercise("hinge", MovementPattern.HINGE, MuscleGroup.HAMSTRINGS),
        exercise("hpush", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST_UPPER),
        exercise("vpush", MovementPattern.VERTICAL_PUSH, MuscleGroup.SIDE_DELTS),
        exercise("hpull", MovementPattern.HORIZONTAL_PULL, MuscleGroup.LATS),
        exercise("vpull", MovementPattern.VERTICAL_PULL, MuscleGroup.LATS),
        exercise("curl", MovementPattern.BICEPS_ISOLATION, MuscleGroup.BICEPS),
        exercise("pushdown", MovementPattern.TRICEPS_ISOLATION, MuscleGroup.TRICEPS),
        exercise("lateral", MovementPattern.SHOULDER_ISOLATION, MuscleGroup.SIDE_DELTS),
        exercise("leg-curl", MovementPattern.LEG_ISOLATION, MuscleGroup.HAMSTRINGS),
        exercise("calf", MovementPattern.CALF_RAISE, MuscleGroup.CALVES),
        exercise("plank", MovementPattern.CORE, MuscleGroup.ABS)
    )

    private object EmptyCatalog : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = emptyList()
    }
}
