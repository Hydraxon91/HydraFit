package com.hydrafit.app.core.llm

import com.hydrafit.app.core.domain.engine.DeterministicWorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.engine.WeeklyPlan
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngine
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun fallsBackWhenOnDeviceOutputIsUnparseable() = runTest {
        val generator = FakeGenerator(available = true, responses = listOf("I am not JSON"))

        val plan = engine(generator).generatePlan(request())

        assertEquals(PlannerEngineId.DETERMINISTIC, plan.engine)
        assertEquals(2, generator.generateCalls)
    }

    private fun engine(generator: OnDeviceTextGenerator) = LocalLlmWorkoutPlannerEngine(
        generator = generator,
        fallback = DeterministicStub,
        catalog = FakeCatalog
    )

    private fun request(setsPerExercise: Int = 3) = PlanRequest(
        daysPerWeek = 3,
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

        override fun isAvailable(): Boolean = available

        override fun generate(prompt: String): String {
            generateCalls++
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
