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
    fun acceptanceCarriesTheSlotPolicyTargetsAndPair() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = AcceptWeeklyPlanUseCase(repository, FakeCatalog, TimeProvider { 0L })
        val policy = SlotProgressionPolicy(minReps = 5, maxReps = 8, incrementKg = 2.5)
        val targets = SlotSetTargets.of(
            listOf(
                SetTarget(reps = 8, loadKind = LoadKind.EXTERNAL, weightKg = 60.0),
                SetTarget(reps = 6, loadKind = LoadKind.EXTERNAL, weightKg = 65.0)
            )
        )
        val pair = com.hydrafit.app.core.domain.routine.Superset("arms", 10, 120)

        useCase(
            WeeklyPlan(
                engine = PlannerEngineId.DETERMINISTIC,
                days = listOf(
                    WorkoutDay(
                        dayIndex = 0,
                        focus = SplitFocus.PUSH,
                        exercises = listOf(
                            PlannedExercise(
                                exerciseId = "bench-press",
                                sets = 2,
                                reps = 8,
                                suggestedWeightKg = 60.0,
                                progressionPolicy = policy,
                                setTargets = targets,
                                superset = pair
                            )
                        )
                    )
                )
            )
        )

        // 0.10.1/0.10.2 promised these reach the accepted plan; before this fix only superset did.
        val accepted = requireNotNull(repository.stored).days.single().exercises.single()
        assertEquals(policy, accepted.progressionPolicy)
        assertEquals(targets, accepted.setTargets)
        assertEquals(pair, accepted.superset)
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

    @Test
    fun recordsDeterministicAndAiAttributionForTheVolumeExplanation() = runTest {
        val deterministic = FakePlanHistoryRepository()
        AcceptWeeklyPlanUseCase(deterministic, FakeCatalog, TimeProvider { 0L })(
            WeeklyPlan(
                engine = PlannerEngineId.DETERMINISTIC,
                days = emptyList(),
                armCoverage = coverage()
            )
        )
        assertEquals(PlanAttribution.DETERMINISTIC, deterministic.stored?.volumeAttribution)
        assertEquals(coverage(), deterministic.stored?.armCoverage)

        val ai = FakePlanHistoryRepository()
        AcceptWeeklyPlanUseCase(ai, FakeCatalog, TimeProvider { 0L })(
            WeeklyPlan(
                engine = PlannerEngineId.GEMINI_API,
                days = emptyList(),
                armCoverage = coverage()
            )
        )
        assertEquals(PlanAttribution.AI_GENERATED, ai.stored?.volumeAttribution)
    }

    @Test
    fun freezesRecordedReasoningOntoTheAcceptedPlan() = runTest {
        val repository = FakePlanHistoryRepository()
        val recorded = ExerciseTargetExplanation(
            baseline = LoadBaselineExplanation(
                source = LoadBaselineSource.RECENT_PERFORMANCE,
                recentEstimateKg = 100.0,
                qualifyingSetCount = 3,
                baselineKg = 100.0
            ),
            progression = LoadProgressionExplanation(
                outcome = ProgressionOutcome.APPLIED,
                increments = 1,
                deltaKg = 2.5
            ),
            conversion = LoadConversionExplanation(
                reps = 8,
                intensityFraction = 0.765,
                uncappedWorkingKg = 77.5
            ),
            setCount = SetCountExplanation(
                baseSets = 3,
                deloadApplied = false,
                deloadVolumeScale = 0.7,
                finalSets = 3
            )
        )

        AcceptWeeklyPlanUseCase(repository, FakeCatalog, TimeProvider { 0L })(
            WeeklyPlan(
                engine = PlannerEngineId.DETERMINISTIC,
                weekNumber = 2,
                cycleNumber = 1,
                days = listOf(
                    WorkoutDay(
                        dayIndex = 0,
                        focus = SplitFocus.PUSH,
                        exercises = listOf(
                            PlannedExercise(
                                exerciseId = "bench-press",
                                sets = 3,
                                reps = 8,
                                suggestedWeightKg = 77.5,
                                explanation = recorded
                            )
                        )
                    )
                ),
                explanation = PlanExplanation(
                    engine = PlannerEngineId.DETERMINISTIC,
                    attribution = PlanAttribution.DETERMINISTIC,
                    isDeload = false,
                    deloadVolumeScale = 0.7,
                    deloadIntensityScale = 0.8
                )
            )
        )

        val accepted = requireNotNull(repository.stored)
        // The slot record is carried verbatim; the generation-time fatigue-skip list (F3, dropped)
        // has no persisted counterpart.
        assertEquals(recorded, accepted.days.single().exercises.single().explanation)
        assertEquals(
            FrozenPlanExplanation(
                attribution = PlanAttribution.DETERMINISTIC,
                isDeload = false,
                deloadVolumeScale = 0.7,
                deloadIntensityScale = 0.8
            ),
            accepted.explanation
        )
    }

    @Test
    fun freezesTheReasoningOfARealGeneratedPlan() = runTest {
        val origin = LoadOriginExplanation(
            baseline = LoadBaselineExplanation(
                source = LoadBaselineSource.RECENT_PERFORMANCE,
                recentEstimateKg = 100.0,
                qualifyingSetCount = 3,
                baselineKg = 100.0
            ),
            progression = LoadProgressionExplanation(
                outcome = ProgressionOutcome.APPLIED,
                increments = 1,
                deltaKg = 2.5
            )
        )
        val generated = DeterministicWorkoutPlannerEngine(FakeCatalog).plan(
            PlanRequest(
                daysPerWeek = 2,
                availableEquipment = setOf(EquipmentTag.BARBELL),
                muscleFatigue = emptyMap(),
                nowMillis = 0L,
                setsPerExercise = 3,
                accessorySetsPerExercise = 2,
                suggestedWeightsKg = mapOf("bench-press" to 102.5),
                recentWeightCaps = mapOf("bench-press" to 102.5),
                loadExplanations = mapOf("bench-press" to origin)
            ),
            FakeCatalog.all()
        )
        val repository = FakePlanHistoryRepository()

        AcceptWeeklyPlanUseCase(repository, FakeCatalog, TimeProvider { 0L })(generated)

        val accepted = requireNotNull(repository.stored)
        val generatedSlots = generated.days.flatMap { it.exercises }
        assertTrue(generatedSlots.isNotEmpty())
        assertEquals(
            generatedSlots.map { it.exerciseId to it.explanation },
            accepted.days.flatMap { it.exercises }.map { it.exerciseId to it.explanation }
        )
        assertEquals(generated.explanation?.attribution, accepted.explanation?.attribution)
        assertEquals(generated.explanation?.isDeload, accepted.explanation?.isDeload)
        assertEquals(
            generated.explanation?.deloadVolumeScale,
            accepted.explanation?.deloadVolumeScale
        )
        assertEquals(
            generated.explanation?.deloadIntensityScale,
            accepted.explanation?.deloadIntensityScale
        )
    }

    private fun coverage() = listOf(
        ArmMuscleCoverage(
            muscle = MuscleGroup.BICEPS,
            targetSets = 4,
            isTargetEnforced = true,
            directIsolationSets = 4,
            estimatedOtherInvolvementCredits = 1.0,
            unmetReason = null
        )
    )

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

        override suspend fun setMeasure(
            planId: Long,
            dayIndex: Int,
            position: Int,
            reps: Int?,
            durationSeconds: Int?,
            distanceMeters: Int?
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
