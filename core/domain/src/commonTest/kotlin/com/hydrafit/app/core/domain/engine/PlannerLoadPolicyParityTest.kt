package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/**
 * End-to-end load-policy parity: identical synthetic history goes through the real
 * [BuildPlannerLoadInputsUseCase], its output builds one [PlanRequest], and the Deterministic engine
 * and the shared [WeeklyPlanSanitizer] must then derive the same working load from the same
 * progression-adjusted e1RM bound. The per-class tests exercise each stage with independently supplied
 * numbers, so only this test proves the stages agree.
 */
class PlannerLoadPolicyParityTest {

    private val engine = DeterministicWorkoutPlannerEngine(FakeCatalog)
    private val sanitizer = WeeklyPlanSanitizer(FakeCatalog)
    private val builder = BuildPlannerLoadInputsUseCase(
        catalog = FakeCatalog,
        buildRecentWeights = BuildRecentWeightsUseCase(),
        progressWeights = ProgressWeightsUseCase()
    )

    @Test
    fun deterministicAndSanitizerAgreeWithoutProgression() = runTest {
        val inputs = builder(build = sources(completedSets()), nowMillis = NOW)

        // Epley(100 x 8) = 126.667; BALANCED compound 6 reps -> 126.667 x 0.765 = 96.9 -> 97.5 kg.
        assertEquals(97.5, deterministicWeight(inputs))
        assertEquals(97.5, sanitizedWeight(inputs))
    }

    @Test
    fun deterministicAndSanitizerAgreeWithEarnedProgression() = runTest {
        val inputs = builder(build = sources(completedSets()), latestPlan = acceptedPrescription())

        // Baseline + one earned increment = 129.167; x 0.765 = 98.8125 -> 100 kg.
        assertEquals(100.0, deterministicWeight(inputs))
        assertEquals(100.0, sanitizedWeight(inputs))
    }

    @Test
    fun deterministicAndSanitizerAgreeOnADeloadWeek() = runTest {
        val inputs = builder(build = sources(completedSets()), latestPlan = acceptedPrescription())

        // 129.167 x 0.765 x 0.8 = 79.05 -> 80 kg.
        assertEquals(80.0, deterministicWeight(inputs, isDeload = true))
        assertEquals(80.0, sanitizedWeight(inputs, isDeload = true))
    }

    @Test
    fun bothEnginesClampToTheEquipmentCeilingBelowTheConvertedBound() = runTest {
        val inputs = builder(build = sources(completedSets()))
        val ceiling = mapOf(EquipmentTag.CABLE_MACHINE to 60.0)

        assertEquals(60.0, deterministicWeight(inputs, equipmentMaxWeights = ceiling))
        assertEquals(60.0, sanitizedWeight(inputs, equipmentMaxWeights = ceiling))
    }

    @Test
    fun bothEnginesWithholdWhenRecentEvidenceIsInsufficient() = runTest {
        val inputs = builder(build = sources(listOf(set(day = 1))))

        assertNull(deterministicWeight(inputs))
        assertNull(sanitizedWeight(inputs))
    }

    @Test
    fun sanitizerKeepsABelowBoundProposalWhileACapIsActive() = runTest {
        val inputs = builder(build = sources(completedSets()))

        // The converted bound is 97.5 kg; a model proposal under it is not rounded or raised.
        assertEquals(50.0, sanitizedWeight(inputs, proposedWeightKg = 50.0))
    }

    private suspend fun builder(
        build: WorkoutPlanSources,
        latestPlan: AcceptedPlan? = null,
        nowMillis: Long = NOW
    ): PlannerLoadInputs = builder(
        sources = build,
        latestPlan = latestPlan,
        pauseIncrements = false,
        utcOffsetMillis = 0L,
        nowMillis = nowMillis
    )

    private fun deterministicWeight(
        inputs: PlannerLoadInputs,
        isDeload: Boolean = false,
        equipmentMaxWeights: Map<EquipmentTag, Double> = emptyMap()
    ): Double? {
        val request = request(
            daysPerWeek = 3,
            splitPreference = SplitType.PUSH_PULL_LEGS,
            inputs = inputs,
            isDeload = isDeload,
            equipmentMaxWeights = equipmentMaxWeights
        )
        return engine.plan(request, CATALOG)
            .days.flatMap { it.exercises }
            .firstOrNull { it.exerciseId == EXERCISE }
            ?.suggestedWeightKg
    }

    private suspend fun sanitizedWeight(
        inputs: PlannerLoadInputs,
        isDeload: Boolean = false,
        equipmentMaxWeights: Map<EquipmentTag, Double> = emptyMap(),
        proposedWeightKg: Double = ABOVE_ANY_BOUND
    ): Double? {
        val request = request(
            daysPerWeek = 1,
            splitPreference = SplitType.AUTO,
            inputs = inputs,
            isDeload = isDeload,
            equipmentMaxWeights = equipmentMaxWeights
        )
        val modelPlan = WeeklyPlan(
            engine = PlannerEngineId.GEMINI_API,
            days = listOf(
                WorkoutDay(
                    dayIndex = 0,
                    focus = SplitFocus.FULL_BODY,
                    exercises = listOf(
                        PlannedExercise(
                            exerciseId = EXERCISE,
                            sets = 3,
                            reps = 6,
                            suggestedWeightKg = proposedWeightKg
                        ),
                        PlannedExercise(
                            exerciseId = "cable-row",
                            sets = 3,
                            reps = 6,
                            suggestedWeightKg = proposedWeightKg
                        )
                    )
                )
            )
        )
        return sanitizer.sanitize(modelPlan, request)!!
            .days.single().exercises
            .first { it.exerciseId == EXERCISE }
            .suggestedWeightKg
    }

    private fun request(
        daysPerWeek: Int,
        splitPreference: SplitType,
        inputs: PlannerLoadInputs,
        isDeload: Boolean,
        equipmentMaxWeights: Map<EquipmentTag, Double>
    ) = PlanRequest(
        daysPerWeek = daysPerWeek,
        availableEquipment = setOf(EquipmentTag.CABLE_MACHINE),
        muscleFatigue = emptyMap(),
        splitPreference = splitPreference,
        nowMillis = NOW,
        goal = TrainingGoal.BALANCED,
        suggestedWeightsKg = inputs.suggestedWeightsKg,
        recentWeightCaps = inputs.recentWeightCaps,
        withheldWeightExerciseIds = inputs.withheldWeightExerciseIds,
        equipmentMaxWeights = equipmentMaxWeights,
        includeWorkoutData = true,
        isDeload = isDeload
    )

    /** Three completed external sessions inside the window, then an accepted external prescription. */
    private fun completedSets(): List<WorkoutSet> =
        listOf(98L, 99L, 100L).flatMap { day -> List(3) { set(day) } }

    private fun acceptedPrescription(): AcceptedPlan = AcceptedPlan(
        engine = PlannerEngineId.DETERMINISTIC,
        acceptedAtMillis = 0L,
        id = 1L,
        days = listOf(
            AcceptedDay(
                dayIndex = 0,
                focus = SplitFocus.PULL,
                exercises = listOf(
                    AcceptedExercise(
                        exerciseId = EXERCISE,
                        sets = 3,
                        reps = 8,
                        name = "Lat Pulldown",
                        movementPattern = MovementPattern.VERTICAL_PULL,
                        suggestedWeightKg = 80.0,
                        loadCapability = ExerciseLoadCapability.EXTERNAL,
                        loadKind = LoadKind.EXTERNAL
                    )
                )
            )
        )
    )

    private fun sources(sets: List<WorkoutSet>): WorkoutPlanSources = WorkoutPlanSources(
        availableEquipment = setOf(EquipmentTag.CABLE_MACHINE),
        selectedEngine = PlannerEngineId.DETERMINISTIC,
        daysPerWeek = 3,
        loggedSets = emptyList(),
        loggedWorkoutSets = sets
    )

    private fun set(day: Long): WorkoutSet = WorkoutSet(
        exerciseId = EXERCISE,
        reps = 8,
        weightKg = 100.0,
        loadKind = LoadKind.EXTERNAL,
        performedAtMillis = day * DAY_MILLIS
    )

    private object FakeCatalog : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = CATALOG
    }

    private companion object {
        const val EXERCISE = "lat-pulldown"
        const val DAY_MILLIS = 86_400_000L
        const val NOW = 100 * DAY_MILLIS

        /** Higher than any converted bound here, so the sanitizer always clamps unless withheld. */
        const val ABOVE_ANY_BOUND = 500.0

        val CATALOG = listOf(
            exercise(EXERCISE, MovementPattern.VERTICAL_PULL, MuscleGroup.LATS),
            exercise("cable-row", MovementPattern.HORIZONTAL_PULL, MuscleGroup.UPPER_BACK),
            exercise("biceps-curl", MovementPattern.BICEPS_ISOLATION, MuscleGroup.BICEPS),
            exercise("triceps-pushdown", MovementPattern.TRICEPS_ISOLATION, MuscleGroup.TRICEPS)
        )

        fun exercise(id: String, pattern: MovementPattern, primary: MuscleGroup) = Exercise(
            id = id,
            name = id,
            requiredEquipment = setOf(EquipmentTag.CABLE_MACHINE),
            primaryMuscles = setOf(primary),
            movementPattern = pattern,
            loadCapability = ExerciseLoadCapability.EXTERNAL
        )
    }
}
