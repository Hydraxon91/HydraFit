package com.hydrafit.app.core.llm

import com.hydrafit.app.core.domain.engine.DeterministicWorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.engine.WeeklyPlan
import com.hydrafit.app.core.domain.engine.WeeklyPlanSanitizer
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngine
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun appliesRequestedSetsAndCompoundIsolationReps() = runTest {
        val generator = FakeGenerator(available = true, responses = listOf(MIXED_REPS_PLAN))

        val plan = engine(generator).generatePlan(request(setsPerExercise = 5))
        val compound = plan.days.first().exercises.first { it.exerciseId == "bench-press" }
        val isolation = plan.days.first().exercises.first { it.exerciseId == "barbell-curl" }

        assertEquals(5, compound.sets)
        assertEquals(DeterministicWorkoutPlannerEngine.COMPOUND_REPS, compound.reps)
        assertEquals(5, isolation.sets)
        assertEquals(DeterministicWorkoutPlannerEngine.ISOLATION_REPS, isolation.reps)
    }

    @Test
    fun requestsJsonConstrainedOutput() = runTest {
        val generator = FakeGenerator(available = true, responses = listOf(THREE_DAY_PLAN))

        engine(generator).generatePlan(request())

        assertTrue(requireNotNull(generator.lastSchema).contains("days"))
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

    private fun request(daysPerWeek: Int = 3, setsPerExercise: Int = 3) = PlanRequest(
        daysPerWeek = daysPerWeek,
        availableEquipment = setOf(EquipmentTag.BARBELL),
        muscleFatigue = emptyMap(),
        nowMillis = 0L,
        setsPerExercise = setsPerExercise
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

        override fun isAvailable(): Boolean = available

        override fun generate(prompt: String, jsonSchema: String?): String {
            generateCalls++
            lastSchema = jsonSchema
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
        private val DAY = listOf("bench-press", "overhead-press")

        val THREE_DAY_PLAN = days(foci = listOf("PUSH", "PULL", "LEGS"), exerciseIds = DAY)
        val FOUR_DAY_PLAN = days(
            foci = listOf("PUSH", "PULL", "LEGS", "UPPER"),
            exerciseIds = DAY
        )
        val ONE_DAY_PLAN = days(foci = listOf("PUSH"), exerciseIds = DAY)
        val UNKNOWN_EXERCISES_PLAN = days(
            foci = listOf("PUSH", "PULL", "LEGS"),
            exerciseIds = listOf("not-a-real-exercise")
        )
        val INDEXED_PLAN = days(
            foci = listOf("PUSH", "PULL", "LEGS"),
            exerciseIds = listOf("1", "2")
        )
        val OUT_OF_RANGE_PLAN = days(
            foci = listOf("PUSH", "PULL", "LEGS"),
            exerciseIds = listOf("99")
        )
        val MIXED_REPS_PLAN = days(
            foci = listOf("PUSH", "PULL", "LEGS"),
            exerciseIds = listOf("bench-press", "barbell-curl")
        )

        private fun days(foci: List<String>, exerciseIds: List<String>): String {
            val exercises = exerciseIds.joinToString(",") {
                """{"exerciseId":"$it","sets":3,"reps":8}"""
            }
            val days = foci.joinToString(",") { """{"focus":"$it","exercises":[$exercises]}""" }
            return """{"days":[$days]}"""
        }
    }
}
