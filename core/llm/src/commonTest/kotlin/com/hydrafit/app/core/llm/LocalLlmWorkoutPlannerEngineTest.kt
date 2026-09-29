package com.hydrafit.app.core.llm

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.engine.SplitType
import com.hydrafit.app.core.domain.engine.TrainingGoal
import com.hydrafit.app.core.domain.engine.WeeklyPlan
import com.hydrafit.app.core.domain.engine.WeeklyPlanSanitizer
import com.hydrafit.app.core.domain.engine.WeightHistoryEntry
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngine
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class LocalLlmWorkoutPlannerEngineTest {

    @Test
    fun usesOnDeviceOutputWhenAvailable() = runTest {
        val generator = FakeGenerator(available = true, responses = listOf(THREE_DAY_PLAN))

        val plan = engine(generator).generatePlan(request())

        assertEquals(PlannerEngineId.LOCAL_LLM, plan.engine)
        assertEquals(3, plan.days.size)
        assertEquals(SplitFocus.PUSH, plan.days.first().focus)
        assertEquals("bench-press", plan.days.first().exercises.first().exerciseId)
    }

    @Test
    fun appliesRequestedSetsAndVolumeAwareReps() = runTest {
        val generator = FakeGenerator(available = true, responses = listOf(MIXED_REPS_PLAN))

        val plan = engine(generator).generatePlan(
            request(setsPerExercise = 5, accessorySetsPerExercise = 2)
        )
        val compound = plan.days.first().exercises.first { it.exerciseId == "bench-press" }
        val isolation = plan.days.first().exercises.first { it.exerciseId == "barbell-curl" }

        assertEquals(5, compound.sets)
        // Balanced compound volume 3 x 6 = 18 -> 18/5 = 3.6 -> 4 reps
        assertEquals(4, compound.reps)
        assertEquals(2, isolation.sets)
        // Balanced accessory volume 2 x 12 = 24 -> 24/2 = 12 reps
        assertEquals(12, isolation.reps)
    }

    @Test
    fun includesCompoundAndAccessorySetGuidanceInThePrompt() = runTest {
        val generator = FakeGenerator(available = true, responses = listOf(THREE_DAY_PLAN))

        engine(generator).generatePlan(
            request(setsPerExercise = 5, accessorySetsPerExercise = 2)
        )

        val prompt = requireNotNull(generator.lastPrompt)
        assertTrue(prompt.contains("5 sets for compound lifts"), prompt)
        assertTrue(prompt.contains("2 sets for accessory exercises"), prompt)
    }

    @Test
    fun includesThePerDayFocusScheduleInThePrompt() = runTest {
        val generator = FakeGenerator(available = true, responses = listOf(THREE_DAY_PLAN))

        engine(generator).generatePlan(
            request(daysPerWeek = 3, splitPreference = SplitType.PUSH_PULL_LEGS)
        )

        val prompt = requireNotNull(generator.lastPrompt)
        assertTrue(prompt.contains("Day 1: PUSH"), prompt)
        assertTrue(prompt.contains("Day 2: PULL"), prompt)
        assertTrue(prompt.contains("Day 3: LEGS"), prompt)
    }

    @Test
    fun pinsEachDayToItsResolvedFocusInTheSchema() = runTest {
        val generator = FakeGenerator(available = true, responses = listOf(THREE_DAY_PLAN))

        engine(generator).generatePlan(
            request(daysPerWeek = 3, splitPreference = SplitType.PUSH_PULL_LEGS)
        )

        val schema = requireNotNull(generator.lastSchema)
        assertTrue(schema.contains("\"enum\": [\"PUSH\"]"), schema)
        assertTrue(schema.contains("\"enum\": [\"PULL\"]"), schema)
        assertTrue(schema.contains("\"enum\": [\"LEGS\"]"), schema)
    }

    @Test
    fun mentionsTheDeloadWeekInThePrompt() = runTest {
        val generator = FakeGenerator(available = true, responses = listOf(THREE_DAY_PLAN))

        engine(generator).generatePlan(request(isDeload = true))

        assertTrue(requireNotNull(generator.lastPrompt).contains("deload week"))
    }

    @Test
    fun requestsJsonConstrainedOutput() = runTest {
        val generator = FakeGenerator(available = true, responses = listOf(THREE_DAY_PLAN))

        engine(generator).generatePlan(request())

        assertTrue(requireNotNull(generator.lastSchema).contains("days"))
    }

    @Test
    fun includesGoalAndGoalSpecificRepTargetsInThePrompt() = runTest {
        val generator = FakeGenerator(available = true, responses = listOf(THREE_DAY_PLAN))

        engine(generator).generatePlan(request(goal = TrainingGoal.STRENGTH))

        val prompt = requireNotNull(generator.lastPrompt)
        assertTrue(prompt.contains("Training goal: STRENGTH"), prompt)
        assertTrue(prompt.contains("Scale reps to keep volume steady"), prompt)
        // Strength compound volume 4 x 5 = 20 total reps
        assertTrue(prompt.contains("about 20 total reps for compound lifts"), prompt)
    }

    @Test
    fun omitsWorkoutDataUnlessEnabled() = runTest {
        val generator = FakeGenerator(available = true, responses = listOf(THREE_DAY_PLAN))

        engine(generator).generatePlan(request())

        assertFalse(requireNotNull(generator.lastPrompt).contains("Recent working weights"))
        assertFalse(requireNotNull(generator.lastSchema).contains("suggestedWeightKg"))
    }

    @Test
    fun sendsRecentWeightsWhenEnabled() = runTest {
        val generator = FakeGenerator(available = true, responses = listOf(THREE_DAY_PLAN))

        engine(generator).generatePlan(
            request(
                includeWorkoutData = true,
                recentWeights = listOf(WeightHistoryEntry("barbell-row", 0L, 50.0, 8))
            )
        )

        assertTrue(requireNotNull(generator.lastPrompt).contains("Recent working weights"))
        assertTrue(requireNotNull(generator.lastSchema).contains("suggestedWeightKg"))
    }

    @Test
    fun sendsProgressedWeightsOnlyWhenEnabled() = runTest {
        val generator = FakeGenerator(available = true, responses = listOf(THREE_DAY_PLAN))

        engine(generator).generatePlan(request(suggestedWeightsKg = mapOf("barbell-row" to 52.5)))
        assertFalse(requireNotNull(generator.lastPrompt).contains("Progressed starting weights"))

        engine(generator).generatePlan(
            request(
                includeWorkoutData = true,
                suggestedWeightsKg = mapOf("barbell-row" to 52.5)
            )
        )
        val prompt = requireNotNull(generator.lastPrompt)
        assertTrue(prompt.contains("Progressed starting weights"))
        assertTrue(prompt.contains("barbell-row: 52.5kg"))
    }

    @Test
    fun warnsTheModelAboutRecentlyUsedListNumbers() = runTest {
        val generator = FakeGenerator(available = true, responses = listOf(THREE_DAY_PLAN))

        engine(generator).generatePlan(
            request(
                recentExerciseIdsByPattern = mapOf(
                    MovementPattern.HORIZONTAL_PULL to setOf("barbell-row")
                )
            )
        )

        val prompt = requireNotNull(generator.lastPrompt)
        assertTrue(
            prompt.contains(
                "Avoid reusing these list numbers unless no other suitable exercise exists: 3"
            ),
            prompt
        )
    }

    @Test
    fun constrainsTheSchemaToTheRequestedDayAndExerciseCounts() = runTest {
        val generator = FakeGenerator(available = true, responses = listOf(THREE_DAY_PLAN))

        engine(generator).generatePlan(request())

        val schema = requireNotNull(generator.lastSchema)
        assertTrue(schema.contains("\"minItems\": 3"), schema)
        assertTrue(schema.contains("\"maxItems\": 3"), schema)
        assertTrue(schema.contains("\"minItems\": 4"), schema)
        assertTrue(schema.contains("\"maxItems\": 6"), schema)
    }

    @Test
    fun sizesTheDayConstraintFromTheRequest() = runTest {
        val generator = FakeGenerator(available = true, responses = listOf(THREE_DAY_PLAN))

        engine(generator).generatePlan(request(daysPerWeek = 5))

        val schema = requireNotNull(generator.lastSchema)
        assertTrue(schema.contains("\"minItems\": 5"), schema)
        assertTrue(schema.contains("\"maxItems\": 5"), schema)
    }

    @Test
    fun constrainsTheSchemaToTheListNumbers() = runTest {
        val generator = FakeGenerator(available = true, responses = listOf(THREE_DAY_PLAN))

        engine(generator).generatePlan(request())

        val schema = requireNotNull(generator.lastSchema)
        assertTrue(schema.contains("\"enum\": [\"1\", \"2\", \"3\", \"4\"]"), schema)
    }

    @Test
    fun omitsTheIdEnumWhenNoExerciseMatchesTheEquipment() = runTest {
        val generator = FakeGenerator(available = true, responses = listOf(THREE_DAY_PLAN))

        engine(generator, catalog = DumbbellOnlyCatalog).generatePlan(request())

        val schema = requireNotNull(generator.lastSchema)
        assertTrue(schema.contains("\"exerciseId\": {\"type\": \"string\"}"), schema)
    }

    @Test
    fun mapsOnDeviceListNumbersToCatalogIds() = runTest {
        val generator = FakeGenerator(available = true, responses = listOf(INDEXED_PLAN))

        val plan = engine(generator).generatePlan(request())

        assertEquals(PlannerEngineId.LOCAL_LLM, plan.engine)
        assertEquals("bench-press", plan.days.first().exercises.first().exerciseId)
    }

    @Test
    fun dropsListNumbersOutsideTheCatalog() = runTest {
        val generator = FakeGenerator(available = true, responses = listOf(OUT_OF_RANGE_PLAN))

        val plan = engine(generator).generatePlan(request())

        assertEquals(PlannerEngineId.DETERMINISTIC, plan.engine)
    }

    @Test
    fun trimsExtraDaysToTheRequestedCount() = runTest {
        val generator = FakeGenerator(available = true, responses = listOf(FOUR_DAY_PLAN))

        val plan = engine(generator).generatePlan(request())

        assertEquals(3, plan.days.size)
        assertEquals(listOf(0, 1, 2), plan.days.map { it.dayIndex })
    }

    @Test
    fun retriesOnceWhenTheOnDevicePlanIsIncomplete() = runTest {
        val generator = FakeGenerator(
            available = true,
            responses = listOf(ONE_DAY_PLAN, THREE_DAY_PLAN)
        )

        val plan = engine(generator).generatePlan(request())

        assertEquals(PlannerEngineId.LOCAL_LLM, plan.engine)
        assertEquals(3, plan.days.size)
        assertEquals(2, generator.generateCalls)
    }

    @Test
    fun fallsBackWhenEveryOnDevicePlanIsIncomplete() = runTest {
        val generator = FakeGenerator(available = true, responses = listOf(ONE_DAY_PLAN))

        val plan = engine(generator).generatePlan(request())

        assertEquals(PlannerEngineId.DETERMINISTIC, plan.engine)
        assertEquals(2, generator.generateCalls)
    }

    @Test
    fun fallsBackWhenADayHasNoUsableExercises() = runTest {
        val generator =
            FakeGenerator(available = true, responses = listOf(UNKNOWN_EXERCISES_PLAN))

        val plan = engine(generator).generatePlan(request())

        assertEquals(PlannerEngineId.DETERMINISTIC, plan.engine)
    }

    @Test
    fun fallsBackToDeterministicOnOutOfMemoryError() = runTest {
        val generator = FakeGenerator(
            available = true,
            failure = { throw OutOfMemoryError("model too big") }
        )

        val plan = engine(generator).generatePlan(request())

        assertEquals(PlannerEngineId.DETERMINISTIC, plan.engine)
        assertEquals(1, generator.generateCalls)
    }

    @Test
    fun fallsBackWhenTheModelIsUnavailable() = runTest {
        val generator = FakeGenerator(available = false)

        val plan = engine(generator).generatePlan(request())

        assertEquals(PlannerEngineId.DETERMINISTIC, plan.engine)
        assertEquals(0, generator.generateCalls)
    }

    @Test
    fun reportsResourceFallbackForOutOfMemory() = runTest {
        val logger = RecordingLogger()
        val generator = FakeGenerator(
            available = true,
            failure = { throw OutOfMemoryError("model too big") }
        )

        engine(generator, logger).generatePlan(request())

        assertEquals(OnDevicePlannerFallback.OUT_OF_MEMORY, logger.reasons.single())
    }

    @Test
    fun reportsUnexpectedFailureWhenEveryAttemptFails() = runTest {
        val logger = RecordingLogger()
        val generator = FakeGenerator(available = true, responses = listOf("I am not JSON"))

        val plan = engine(generator, logger).generatePlan(request())

        assertEquals(PlannerEngineId.DETERMINISTIC, plan.engine)
        assertEquals(OnDevicePlannerFallback.UNEXPECTED_FAILURE, logger.reasons.single())
    }

    private class RecordingLogger : OnDevicePlannerLogger {
        val reasons = mutableListOf<OnDevicePlannerFallback>()
        val causes = mutableListOf<Throwable>()

        override fun onFallback(reason: OnDevicePlannerFallback, cause: Throwable) {
            reasons += reason
            causes += cause
        }
    }

    @Test
    fun fallsBackWhenOnDeviceOutputIsUnparseable() = runTest {
        val generator = FakeGenerator(available = true, responses = listOf("I am not JSON"))

        val plan = engine(generator).generatePlan(request())

        assertEquals(PlannerEngineId.DETERMINISTIC, plan.engine)
        assertEquals(2, generator.generateCalls)
    }

    private fun engine(
        generator: OnDeviceTextGenerator,
        logger: OnDevicePlannerLogger = NoopOnDevicePlannerLogger,
        catalog: ExerciseCatalog = FakeCatalog
    ) = LocalLlmWorkoutPlannerEngine(
        generator = generator,
        fallback = DeterministicStub,
        catalog = catalog,
        sanitizer = WeeklyPlanSanitizer(catalog),
        logger = logger
    )

    private fun request(
        daysPerWeek: Int = 3,
        goal: TrainingGoal = TrainingGoal.BALANCED,
        splitPreference: SplitType = SplitType.AUTO,
        setsPerExercise: Int = goal.defaultSets,
        accessorySetsPerExercise: Int = goal.accessorySets,
        recentExerciseIdsByPattern: Map<MovementPattern, Set<String>> = emptyMap(),
        suggestedWeightsKg: Map<String, Double> = emptyMap(),
        includeWorkoutData: Boolean = false,
        isDeload: Boolean = false,
        recentWeights: List<WeightHistoryEntry> = emptyList()
    ) = PlanRequest(
        daysPerWeek = daysPerWeek,
        availableEquipment = setOf(EquipmentTag.BARBELL),
        muscleFatigue = emptyMap(),
        splitPreference = splitPreference,
        nowMillis = 0L,
        goal = goal,
        setsPerExercise = setsPerExercise,
        accessorySetsPerExercise = accessorySetsPerExercise,
        recentExerciseIdsByPattern = recentExerciseIdsByPattern,
        suggestedWeightsKg = suggestedWeightsKg,
        includeWorkoutData = includeWorkoutData,
        isDeload = isDeload,
        recentWeights = recentWeights
    )

    private class FakeGenerator(
        private val available: Boolean,
        private val responses: List<String> = listOf(""),
        private val failure: (() -> Unit)? = null
    ) : OnDeviceTextGenerator {
        var generateCalls: Int = 0
            private set

        var lastSchema: String? = null
            private set
        var lastPrompt: String? = null
            private set

        override fun isAvailable(): Boolean = available

        override fun generate(prompt: String, jsonSchema: String?): String {
            generateCalls++
            lastSchema = jsonSchema
            lastPrompt = prompt
            failure?.invoke()
            return responses[(generateCalls - 1).coerceAtMost(responses.lastIndex)]
        }
    }

    private object DeterministicStub : WorkoutPlannerEngine {
        override val id: PlannerEngineId = PlannerEngineId.DETERMINISTIC

        override suspend fun generatePlan(request: PlanRequest): WeeklyPlan =
            WeeklyPlan(engine = PlannerEngineId.DETERMINISTIC, days = emptyList())
    }

    private object FakeCatalog : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = listOf(
            exercise("bench-press", MovementPattern.HORIZONTAL_PUSH),
            exercise("overhead-press", MovementPattern.VERTICAL_PUSH),
            exercise("barbell-row", MovementPattern.HORIZONTAL_PULL),
            exercise("barbell-curl", MovementPattern.BICEPS_ISOLATION)
        )

        private fun exercise(id: String, pattern: MovementPattern) = Exercise(
            id = id,
            name = id,
            requiredEquipment = setOf(EquipmentTag.BARBELL),
            primaryMuscles = setOf(MuscleGroup.CHEST),
            movementPattern = pattern
        )
    }

    private object DumbbellOnlyCatalog : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = listOf(
            Exercise(
                id = "dumbbell-curl",
                name = "Dumbbell Curl",
                requiredEquipment = setOf(EquipmentTag.DUMBBELL),
                primaryMuscles = setOf(MuscleGroup.BICEPS),
                movementPattern = MovementPattern.BICEPS_ISOLATION
            )
        )
    }

    private companion object {
        // Distinct compounds per day, with the reusable accessory repeated across days.
        val THREE_DAY_PLAN = days(
            listOf(
                "PUSH" to listOf("bench-press", "barbell-curl"),
                "PULL" to listOf("barbell-row", "barbell-curl"),
                "LEGS" to listOf("overhead-press", "barbell-curl")
            )
        )
        val FOUR_DAY_PLAN = days(
            listOf(
                "PUSH" to listOf("bench-press", "barbell-curl"),
                "PULL" to listOf("barbell-row", "barbell-curl"),
                "LEGS" to listOf("overhead-press", "barbell-curl"),
                "UPPER" to listOf("bench-press", "barbell-curl")
            )
        )
        val ONE_DAY_PLAN = days(
            listOf("PUSH" to listOf("bench-press", "barbell-curl"))
        )
        val UNKNOWN_EXERCISES_PLAN = days(
            listOf(
                "PUSH" to listOf("not-a-real-exercise"),
                "PULL" to listOf("not-a-real-exercise"),
                "LEGS" to listOf("not-a-real-exercise")
            )
        )
        val INDEXED_PLAN = days(
            listOf(
                "PUSH" to listOf("1", "4"),
                "PULL" to listOf("3", "4"),
                "LEGS" to listOf("2", "4")
            )
        )
        val OUT_OF_RANGE_PLAN = days(
            listOf(
                "PUSH" to listOf("99"),
                "PULL" to listOf("99"),
                "LEGS" to listOf("99")
            )
        )
        val MIXED_REPS_PLAN = days(
            listOf(
                "PUSH" to listOf("bench-press", "barbell-curl"),
                "PULL" to listOf("barbell-row", "barbell-curl"),
                "LEGS" to listOf("overhead-press", "barbell-curl")
            )
        )

        private fun days(days: List<Pair<String, List<String>>>): String {
            val entries = days.joinToString(",") { (focus, exerciseIds) ->
                val exercises = exerciseIds.joinToString(",") {
                    """{"exerciseId":"$it","sets":3,"reps":8}"""
                }
                """{"focus":"$focus","exercises":[$exercises]}"""
            }
            return """{"days":[$entries]}"""
        }
    }
}
