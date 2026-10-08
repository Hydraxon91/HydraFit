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
            request = request(equipment = setOf(EquipmentTag.DUMBBELL)),
            selectedExerciseId = "dumbbell-bench"
        )

        assertEquals(
            SubstituteCall(1L, 0, 0, "dumbbell-bench", "Dumbbell Bench", 82.5),
            repository.substitutions.single()
        )
        assertEquals("dumbbell-bench", result?.exerciseId)
        assertEquals("Dumbbell Bench", result?.name)
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
    fun rejectsWeightByEquipmentCeiling() = runTest {
        val repository = FakePlanHistoryRepository()
        val useCase = useCase(repository)

        val result = useCase(
            plan = plan(),
            dayIndex = 0,
            position = 0,
            request = request(
                equipment = setOf(EquipmentTag.DUMBBELL),
                equipmentMaxWeights = mapOf(EquipmentTag.DUMBBELL to 60.0)
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
            DeterministicWorkoutPlannerEngine(onlyUnavailable)
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
            DeterministicWorkoutPlannerEngine(testCatalog)
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

    private fun useCase(repository: PlanHistoryRepository) =
        SubstituteExerciseUseCase(catalog, repository, DeterministicWorkoutPlannerEngine(catalog))

    private fun request(
        equipment: Set<EquipmentTag>,
        fatigue: Map<MuscleGroup, Double> = emptyMap(),
        equipmentMaxWeights: Map<EquipmentTag, Double> = emptyMap(),
        exercisePreferences: Map<String, ExercisePreference> = emptyMap()
    ) = PlanRequest(
        daysPerWeek = 3,
        availableEquipment = equipment,
        muscleFatigue = fatigue,
        nowMillis = 0L,
        goal = TrainingGoal.BALANCED,
        equipmentMaxWeights = equipmentMaxWeights,
        exercisePreferences = exercisePreferences
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
        loadCapability: ExerciseLoadCapability = ExerciseLoadCapability.EXTERNAL
    ) = Exercise(
        id = id,
        name = id.split('-').joinToString(" ") { it.replaceFirstChar(Char::uppercase) },
        requiredEquipment = setOfNotNull(equipment),
        primaryMuscles = setOf(MuscleGroup.CHEST_UPPER),
        movementPattern = pattern,
        loadCapability = loadCapability
    )

    private data class SubstituteCall(
        val planId: Long,
        val dayIndex: Int,
        val position: Int,
        val newExerciseId: String,
        val newExerciseName: String,
        val newWeightKg: Double?,
        val newLoadKind: LoadKind = LoadKind.EXTERNAL
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

        override suspend fun delete(planId: Long) = Unit

        override suspend fun clear() = Unit
    }
}
