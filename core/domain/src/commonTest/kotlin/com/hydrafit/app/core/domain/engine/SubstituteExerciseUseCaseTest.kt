package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.ExercisePreference
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class SubstituteExerciseUseCaseTest {

    private val catalog = FakeCatalog(
        listOf(
            exercise("barbell-bench", MovementPattern.HORIZONTAL_PUSH, EquipmentTag.BARBELL),
            exercise("dumbbell-bench", MovementPattern.HORIZONTAL_PUSH, EquipmentTag.DUMBBELL),
            exercise(
                "push-up",
                MovementPattern.HORIZONTAL_PUSH,
                loadCapability = ExerciseLoadCapability.BODYWEIGHT_ONLY
            ),
            exercise("cable-fly", MovementPattern.CHEST_FLY, EquipmentTag.CABLE_MACHINE)
        )
    )

    @Test
    fun dropsTheLoadWhenTheReplacementCannotCarryTheSlotLoad() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = useCase(repository)

        val result = useCase(
            plan = plan(),
            dayIndex = 0,
            position = 0,
            request = request(equipment = emptySet()),
            selectedExerciseId = "push-up"
        )

        // The external slot's 82.5 kg cannot become bodyweight load: no number, bodyweight kind.
        assertEquals("push-up", result?.exerciseId)
        assertNull(result?.suggestedWeightKg)
        assertEquals(LoadKind.BODYWEIGHT, result?.loadKind)
        assertEquals(ExerciseLoadCapability.BODYWEIGHT_ONLY, result?.loadCapability)
        assertNull(repository.substitutions.single().newWeightKg)
        assertEquals(LoadKind.BODYWEIGHT, repository.substitutions.single().newLoadKind)
    }

    @Test
    fun updatesTheRowInTheRepository() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = useCase(repository)

        val result = useCase(
            plan = plan(),
            dayIndex = 0,
            position = 0,
            request = request(
                equipment = setOf(EquipmentTag.DUMBBELL),
                suggestedWeightsKg = mapOf("dumbbell-bench" to 100.0)
            ),
            selectedExerciseId = "dumbbell-bench"
        )

        // The replacement's own 100 kg e1RM at 6 reps converts to 77.5 kg, never the old 82.5 kg.
        val call = repository.substitutions.single()
        assertEquals(1L, call.planId)
        assertEquals(0, call.dayIndex)
        assertEquals(0, call.position)
        assertEquals("dumbbell-bench", call.newExerciseId)
        assertEquals("Dumbbell Bench", call.newExerciseName)
        assertEquals(77.5, call.newWeightKg)
        assertEquals(LoadKind.EXTERNAL, call.newLoadKind)
        assertEquals("dumbbell-bench", result?.exerciseId)
        assertEquals("Dumbbell Bench", result?.name)
    }

    @Test
    fun usesTheCandidatesOwnEstimateInsteadOfTheReplacedLoad() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = useCase(repository)

        val result = useCase(
            plan = plan(),
            dayIndex = 0,
            position = 0,
            request = request(
                equipment = setOf(EquipmentTag.DUMBBELL),
                suggestedWeightsKg = mapOf("dumbbell-bench" to 100.0)
            ),
            selectedExerciseId = "dumbbell-bench"
        )

        assertEquals(77.5, result?.suggestedWeightKg)
        assertTrue(result?.suggestedWeightKg != 82.5)
    }

    @Test
    fun aCandidateWithoutItsOwnBaselineHasNoNumericLoad() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = useCase(repository)

        val result = useCase(
            plan = plan(),
            dayIndex = 0,
            position = 0,
            request = request(equipment = setOf(EquipmentTag.DUMBBELL)),
            selectedExerciseId = "dumbbell-bench"
        )

        assertNull(result?.suggestedWeightKg)
        assertNull(repository.substitutions.single().newWeightKg)
        // The provenance records why no number was produced instead of pretending a baseline existed.
        assertEquals(
            LoadUnavailableReason.INSUFFICIENT_RECENT_EVIDENCE,
            repository.substitutions.single().explanation?.unavailableReason
        )
    }

    @Test
    fun aWithheldCandidateBaselineProducesNoNumber() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = useCase(repository)

        val result = useCase(
            plan = plan(),
            dayIndex = 0,
            position = 0,
            request = request(
                equipment = setOf(EquipmentTag.DUMBBELL),
                suggestedWeightsKg = mapOf("dumbbell-bench" to 100.0),
                withheldWeightExerciseIds = setOf("dumbbell-bench")
            ),
            selectedExerciseId = "dumbbell-bench"
        )

        assertNull(result?.suggestedWeightKg)
        assertNull(repository.substitutions.single().newWeightKg)
        assertEquals(
            LoadUnavailableReason.INSUFFICIENT_RECENT_EVIDENCE,
            repository.substitutions.single().explanation?.unavailableReason
        )
    }

    @Test
    fun aRecentWeightCapBoundsTheCandidatesBaseline() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = useCase(repository)

        val result = useCase(
            plan = plan(),
            dayIndex = 0,
            position = 0,
            request = request(
                equipment = setOf(EquipmentTag.DUMBBELL),
                suggestedWeightsKg = mapOf("dumbbell-bench" to 200.0),
                recentWeightCaps = mapOf("dumbbell-bench" to 100.0)
            ),
            selectedExerciseId = "dumbbell-bench"
        )

        assertEquals(77.5, result?.suggestedWeightKg)
    }

    @Test
    fun aSwapRecordsTheCandidatesOwnProvenance() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = useCase(repository)

        useCase(
            plan = plan().copy(
                explanation = FrozenPlanExplanation(
                    attribution = PlanAttribution.DETERMINISTIC,
                    isDeload = false,
                    deloadVolumeScale = 0.7,
                    deloadIntensityScale = 0.8
                )
            ),
            dayIndex = 0,
            position = 0,
            request = request(
                equipment = setOf(EquipmentTag.DUMBBELL),
                suggestedWeightsKg = mapOf("dumbbell-bench" to 100.0)
            ),
            selectedExerciseId = "dumbbell-bench"
        )

        val recorded = requireNotNull(repository.substitutions.single().explanation)
        val conversion = requireNotNull(recorded.conversion)
        assertEquals(6, conversion.reps)
        // 100 kg x 0.765 (6 reps) = 76.5, rounded to the 2.5 kg increment the engine records too.
        assertEquals(77.5, conversion.uncappedWorkingKg ?: Double.NaN, 0.01)
        assertEquals(77.5, repository.substitutions.single().newWeightKg)
        // No equipment ceiling was configured, so none is recorded as applied.
        assertNull(conversion.equipmentCeilingKg)
    }

    @Test
    fun aDumbbellCandidateCarriesItsImplementFacts() = runTest {
        val useCase = useCase(FakePlanHistoryRepository())
        val unilateral = FakeCatalog(
            listOf(
                exercise("barbell-bench", MovementPattern.HORIZONTAL_PUSH, EquipmentTag.BARBELL),
                exercise(
                    "dumbbell-bench",
                    MovementPattern.HORIZONTAL_PUSH,
                    EquipmentTag.DUMBBELL,
                    isUnilateral = true
                )
            )
        )
        val useCaseWithUnilateralCandidate = SubstituteExerciseUseCase(
            unilateral,
            FakePlanHistoryRepository(),
            DeterministicWorkoutPlannerEngine(unilateral),
            SuggestedWeightConfig()
        )

        val candidate = useCase.candidates(
            plan(),
            0,
            0,
            request(equipment = setOf(EquipmentTag.DUMBBELL))
        ).first { it.exerciseId == "dumbbell-bench" }
        assertTrue(candidate.requiresDumbbell)
        assertTrue(!candidate.isUnilateral)

        val unilateralCandidate = useCaseWithUnilateralCandidate.candidates(
            plan(),
            0,
            0,
            request(equipment = setOf(EquipmentTag.DUMBBELL))
        ).first { it.exerciseId == "dumbbell-bench" }
        assertTrue(unilateralCandidate.requiresDumbbell)
        assertTrue(unilateralCandidate.isUnilateral)
    }

    @Test
    fun preservesSetsAndRepsFromTheReplacedSlot() = runTest {
        val useCase = useCase(FakePlanHistoryRepository())

        val result = useCase(
            plan = plan(),
            dayIndex = 0,
            position = 0,
            request = request(equipment = setOf(EquipmentTag.DUMBBELL)),
            selectedExerciseId = "dumbbell-bench"
        )

        assertEquals(4, result?.sets)
        assertEquals(6, result?.reps)
    }

    @Test
    fun preservesTheReplacedSlotsMeasure() = runTest {
        val useCase = useCase(FakePlanHistoryRepository())
        val timedPlan = plan().copy(
            days = listOf(
                AcceptedDay(
                    dayIndex = 0,
                    focus = SplitFocus.PUSH,
                    exercises = listOf(
                        AcceptedExercise(
                            exerciseId = "barbell-bench",
                            sets = 3,
                            reps = null,
                            durationSeconds = 45,
                            name = "Barbell Bench",
                            movementPattern = MovementPattern.HORIZONTAL_PUSH,
                            suggestedWeightKg = 82.5
                        )
                    )
                )
            )
        )

        val result = useCase(
            plan = timedPlan,
            dayIndex = 0,
            position = 0,
            request = request(equipment = setOf(EquipmentTag.DUMBBELL)),
            selectedExerciseId = "dumbbell-bench"
        )

        // A timed slot stays timed: the replacement inherits the seconds, not a rep count.
        assertEquals("dumbbell-bench", result?.exerciseId)
        assertNull(result?.reps)
        assertEquals(45, result?.durationSeconds)
    }

    @Test
    fun rejectsWeightByEquipmentCeiling() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = useCase(repository)

        val result = useCase(
            plan = plan(),
            dayIndex = 0,
            position = 0,
            request = request(
                equipment = setOf(EquipmentTag.DUMBBELL),
                equipmentMaxWeights = mapOf(EquipmentTag.DUMBBELL to 60.0),
                suggestedWeightsKg = mapOf("dumbbell-bench" to 200.0)
            ),
            selectedExerciseId = "dumbbell-bench"
        )

        assertEquals(60.0, result?.suggestedWeightKg)
        assertEquals(60.0, repository.substitutions.single().newWeightKg)
    }

    @Test
    fun nullWhenNoCandidatesAvailable() = runTest {
        val repository = FakePlanHistoryRepository()
        val onlyUnavailable = FakeCatalog(
            listOf(
                exercise("barbell-bench", MovementPattern.HORIZONTAL_PUSH, EquipmentTag.BARBELL),
                exercise("cable-press", MovementPattern.HORIZONTAL_PUSH, EquipmentTag.CABLE_MACHINE)
            )
        )
        val useCase = SubstituteExerciseUseCase(
            onlyUnavailable,
            repository,
            DeterministicWorkoutPlannerEngine(onlyUnavailable),
            SuggestedWeightConfig()
        )
        val request = request(equipment = setOf(EquipmentTag.BARBELL))

        assertTrue(useCase.candidates(plan(), 0, 0, request).isEmpty())
        assertNull(useCase(plan(), 0, 0, request, "cable-press"))
        assertTrue(repository.substitutions.isEmpty())
    }

    @Test
    fun filtersByMovementPattern() = runTest {
        val useCase = useCase(FakePlanHistoryRepository())

        val candidates = useCase.candidates(
            plan(),
            0,
            0,
            request(equipment = setOf(EquipmentTag.DUMBBELL, EquipmentTag.CABLE_MACHINE))
        )

        assertEquals(listOf("dumbbell-bench", "push-up"), candidates.map { it.exerciseId })
        assertTrue(candidates.none { it.movementPattern == MovementPattern.CHEST_FLY })
    }

    @Test
    fun directArmSlotRanksCoveragePreservingSubstitutionsFirst() = runTest {
        val exercises = listOf(
            exercise("original-curl", MovementPattern.BICEPS_ISOLATION).copy(
                primaryMuscles = setOf(MuscleGroup.BICEPS),
                involvements = mapOf(MuscleGroup.BICEPS to 1.0)
            ),
            exercise("a-mismatched-curl", MovementPattern.BICEPS_ISOLATION, EquipmentTag.DUMBBELL)
                .copy(
                    primaryMuscles = setOf(MuscleGroup.CHEST_UPPER),
                    involvements = mapOf(MuscleGroup.CHEST_UPPER to 1.0)
                ),
            exercise("z-matching-curl", MovementPattern.BICEPS_ISOLATION, EquipmentTag.DUMBBELL)
                .copy(
                    primaryMuscles = setOf(MuscleGroup.BICEPS),
                    involvements = mapOf(MuscleGroup.BICEPS to 1.0)
                )
        )
        val testCatalog = FakeCatalog(exercises)
        val useCase = SubstituteExerciseUseCase(
            testCatalog,
            FakePlanHistoryRepository(),
            DeterministicWorkoutPlannerEngine(testCatalog),
            SuggestedWeightConfig()
        )
        val directPlan = plan().copy(
            days = listOf(
                AcceptedDay(
                    dayIndex = 0,
                    focus = SplitFocus.PULL,
                    exercises = listOf(
                        AcceptedExercise(
                            exerciseId = "original-curl",
                            sets = 2,
                            reps = 12,
                            name = "Original Curl",
                            movementPattern = MovementPattern.BICEPS_ISOLATION
                        )
                    )
                )
            )
        )

        val candidates = useCase.candidates(
            directPlan,
            0,
            0,
            request(equipment = setOf(EquipmentTag.DUMBBELL))
        )

        assertEquals(
            listOf("z-matching-curl", "a-mismatched-curl"),
            candidates.map { it.exerciseId }
        )
    }

    @Test
    fun preferenceReordersSubstitutionCandidatesLikeGeneration() = runTest {
        val useCase = useCase(FakePlanHistoryRepository())

        val baseline = useCase.candidates(
            plan(),
            0,
            0,
            request(equipment = setOf(EquipmentTag.DUMBBELL))
        )
        assertEquals(listOf("dumbbell-bench", "push-up"), baseline.map { it.exerciseId })

        val preferred = useCase.candidates(
            plan(),
            0,
            0,
            request(
                equipment = setOf(EquipmentTag.DUMBBELL),
                exercisePreferences = mapOf("push-up" to ExercisePreference.PREFER)
            )
        )
        // The explicit preference reorders the same available alternatives the way generation would.
        assertEquals(listOf("push-up", "dumbbell-bench"), preferred.map { it.exerciseId })
    }

    @Test
    fun excludedCandidateIsNeitherOfferedNorApplied() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = useCase(repository)
        val request = request(
            equipment = setOf(EquipmentTag.DUMBBELL),
            excludedExerciseIds = setOf("push-up")
        )

        assertTrue(useCase.candidates(plan(), 0, 0, request).none { it.exerciseId == "push-up" })
        assertNull(useCase(plan(), 0, 0, request, "push-up"))
        assertTrue(repository.substitutions.isEmpty())
    }

    @Test
    fun nullWhenTheSelectedCandidateIsSore() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = useCase(repository)

        val result = useCase(
            plan = plan(),
            dayIndex = 0,
            position = 0,
            request = request(
                equipment = setOf(EquipmentTag.DUMBBELL),
                fatigue = mapOf(MuscleGroup.CHEST_UPPER to 0.9)
            ),
            selectedExerciseId = "dumbbell-bench"
        )

        assertNull(result)
        assertTrue(repository.substitutions.isEmpty())
    }

    @Test
    fun aSwapKeepsTheSlotSupersetSoNoHalfPairIsCreated() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = useCase(repository)
        val pair = com.hydrafit.app.core.domain.routine.Superset("arms", 10, 120)
        val policy = SlotProgressionPolicy(minReps = 5, maxReps = 8, incrementKg = 2.5)
        val targets = SlotSetTargets.of(
            listOf(SetTarget(reps = 8, loadKind = LoadKind.EXTERNAL, weightKg = 60.0))
        )
        val paired = plan().let { plan ->
            plan.copy(
                days = plan.days.map { day ->
                    day.copy(
                        exercises = day.exercises.map {
                            it.copy(
                                superset = pair,
                                progressionPolicy = policy,
                                setTargets = targets
                            )
                        }
                    )
                }
            )
        }

        val result = useCase(
            plan = paired,
            dayIndex = 0,
            position = 0,
            request = request(
                equipment = setOf(EquipmentTag.DUMBBELL),
                suggestedWeightsKg = mapOf("dumbbell-bench" to 100.0)
            ),
            selectedExerciseId = "dumbbell-bench"
        )

        // A swap re-prescribes the exercise only; dropping the group here would leave the stored
        // partner unpaired and make Save-as-routine / Start block reject a malformed half pair.
        assertEquals("dumbbell-bench", result?.exerciseId)
        assertEquals(pair, result?.superset)
        assertEquals(policy, result?.progressionPolicy)
        // The ordered prescription keeps its shape and measure but takes the candidate's own load.
        val recalculated = requireNotNull(result?.setTargets)
        assertEquals(1, recalculated.targets.size)
        assertEquals(8, recalculated.targets.single().reps)
        assertEquals(72.5, recalculated.targets.single().weightKg)
        assertNull(recalculated.recipe)
    }

    @Test
    fun orderedTargetsAreRecalculatedPerSetAndTheRecipeIsDropped() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = useCase(repository)
        val recipeTargets = SlotSetTargets.of(
            targets = listOf(
                SetTarget(reps = 8, loadKind = LoadKind.EXTERNAL, weightKg = 60.0, targetRir = 2),
                SetTarget(reps = 12, loadKind = LoadKind.EXTERNAL, weightKg = 50.0, targetRir = 1)
            ),
            recipe = SetPrescriptionRecipeSnapshot(
                shape = SetPrescriptionShape.ASCENDING_LOAD,
                sets = 2,
                startReps = 8,
                endReps = 12,
                startLoadKg = 60.0,
                endLoadKg = 50.0,
                roundToKg = 2.5
            )
        )
        val targeted = plan().copy(
            days = listOf(
                AcceptedDay(
                    dayIndex = 0,
                    focus = SplitFocus.PUSH,
                    exercises = listOf(
                        AcceptedExercise(
                            exerciseId = "barbell-bench",
                            sets = 2,
                            reps = 8,
                            name = "Barbell Bench",
                            movementPattern = MovementPattern.HORIZONTAL_PUSH,
                            suggestedWeightKg = 82.5,
                            setTargets = recipeTargets
                        )
                    )
                )
            )
        )

        val result = useCase(
            plan = targeted,
            dayIndex = 0,
            position = 0,
            request = request(
                equipment = setOf(EquipmentTag.DUMBBELL),
                suggestedWeightsKg = mapOf("dumbbell-bench" to 100.0)
            ),
            selectedExerciseId = "dumbbell-bench"
        )

        val targets = requireNotNull(result?.setTargets).targets
        // Each set's load is the candidate's baseline at that set's own reps: 8 -> 72.5, 12 -> 62.5.
        assertEquals(listOf(72.5, 62.5), targets.map { it.weightKg })
        assertEquals(listOf(8, 12), targets.map { it.reps })
        assertEquals(listOf(2, 1), targets.map { it.targetRir })
        assertNull(result?.setTargets?.recipe)
        // The whole list is persisted in one call with its size intact.
        assertEquals(2, repository.substitutions.single().setTargets?.targets?.size)
    }

    @Test
    fun aCapabilityChangingSwapDropsOrderedTargetsInsteadOfCarryingExternalLoads() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = useCase(repository)
        val targets = SlotSetTargets.of(
            listOf(SetTarget(reps = 8, loadKind = LoadKind.EXTERNAL, weightKg = 60.0))
        )
        val targeted = plan().let { plan ->
            plan.copy(
                days = plan.days.map { day ->
                    day.copy(exercises = day.exercises.map { it.copy(setTargets = targets) })
                }
            )
        }

        val result = useCase(
            plan = targeted,
            dayIndex = 0,
            position = 0,
            request = request(equipment = emptySet()),
            selectedExerciseId = "push-up"
        )

        assertEquals("push-up", result?.exerciseId)
        assertEquals(LoadKind.BODYWEIGHT, result?.loadKind)
        assertNull(result?.setTargets)
    }

    private fun useCase(repository: PlanHistoryRepository) = SubstituteExerciseUseCase(
        catalog,
        repository,
        DeterministicWorkoutPlannerEngine(catalog),
        SuggestedWeightConfig()
    )

    private fun request(
        equipment: Set<EquipmentTag>,
        fatigue: Map<MuscleGroup, Double> = emptyMap(),
        equipmentMaxWeights: Map<EquipmentTag, Double> = emptyMap(),
        exercisePreferences: Map<String, ExercisePreference> = emptyMap(),
        excludedExerciseIds: Set<String> = emptySet(),
        suggestedWeightsKg: Map<String, Double> = emptyMap(),
        recentWeightCaps: Map<String, Double> = emptyMap(),
        withheldWeightExerciseIds: Set<String> = emptySet()
    ) = PlanRequest(
        daysPerWeek = 3,
        availableEquipment = equipment,
        muscleFatigue = fatigue,
        nowMillis = 0L,
        goal = TrainingGoal.BALANCED,
        equipmentMaxWeights = equipmentMaxWeights,
        exercisePreferences = exercisePreferences,
        excludedExerciseIds = excludedExerciseIds,
        suggestedWeightsKg = suggestedWeightsKg,
        recentWeightCaps = recentWeightCaps,
        withheldWeightExerciseIds = withheldWeightExerciseIds
    )

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

    private fun exercise(
        id: String,
        pattern: MovementPattern,
        equipment: EquipmentTag? = null,
        loadCapability: ExerciseLoadCapability = ExerciseLoadCapability.EXTERNAL,
        isUnilateral: Boolean = false
    ) = Exercise(
        id = id,
        name = id.split('-').joinToString(" ") { it.replaceFirstChar(Char::uppercase) },
        requiredEquipment = setOfNotNull(equipment),
        primaryMuscles = setOf(MuscleGroup.CHEST_UPPER),
        movementPattern = pattern,
        loadCapability = loadCapability,
        isUnilateral = isUnilateral
    )

    private data class SubstituteCall(
        val planId: Long,
        val dayIndex: Int,
        val position: Int,
        val newExerciseId: String,
        val newExerciseName: String,
        val newWeightKg: Double?,
        val newLoadKind: LoadKind = LoadKind.EXTERNAL,
        val setTargets: SlotSetTargets? = null,
        val explanation: ExerciseTargetExplanation? = null
    )

    private class FakeCatalog(private val exercises: List<Exercise>) : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = exercises
    }

    private class FakePlanHistoryRepository : PlanHistoryRepository {
        val substitutions = mutableListOf<SubstituteCall>()

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
        ) {
            substitutions += SubstituteCall(
                planId = planId,
                dayIndex = dayIndex,
                position = position,
                newExerciseId = newExerciseId,
                newExerciseName = newExerciseName,
                newWeightKg = newWeightKg,
                newLoadKind = newLoadKind
            )
        }

        override suspend fun substituteWithProvenance(
            planId: Long,
            dayIndex: Int,
            position: Int,
            newExerciseId: String,
            newExerciseName: String,
            newWeightKg: Double?,
            newLoadCapability: ExerciseLoadCapability,
            newLoadKind: LoadKind,
            setTargets: SlotSetTargets?,
            explanation: ExerciseTargetExplanation?
        ) {
            substitutions += SubstituteCall(
                planId = planId,
                dayIndex = dayIndex,
                position = position,
                newExerciseId = newExerciseId,
                newExerciseName = newExerciseName,
                newWeightKg = newWeightKg,
                newLoadKind = newLoadKind,
                setTargets = setTargets,
                explanation = explanation
            )
        }

        override suspend fun setMeasure(
            planId: Long,
            dayIndex: Int,
            position: Int,
            reps: Int?,
            durationSeconds: Int?
        ) = Unit

        override suspend fun delete(planId: Long) = Unit

        override suspend fun clear() = Unit
    }
}
