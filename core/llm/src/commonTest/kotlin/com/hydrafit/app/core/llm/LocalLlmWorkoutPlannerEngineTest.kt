package com.hydrafit.app.core.llm

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.engine.WeeklyPlan
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngine
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class LocalLlmWorkoutPlannerEngineTest {

    @Test
    fun usesOnDeviceOutputWhenAvailable() = runTest {
        val generator = FakeGenerator(
            available = true,
            response = """{"days":[{"focus":"PUSH",""" +
                """"exercises":[{"exerciseId":"bench-press","sets":4,"reps":6}]}]}"""
        )

        val plan = engine(generator).generatePlan(request())

        assertEquals(PlannerEngineId.LOCAL_LLM, plan.engine)
        assertEquals(SplitFocus.PUSH, plan.days.single().focus)
        assertEquals("bench-press", plan.days.single().exercises.single().exerciseId)
    }

    @Test
    fun fallsBackToDeterministicOnOutOfMemoryError() = runTest {
        val generator =
            FakeGenerator(available = true, failure = { throw OutOfMemoryError("model too big") })

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
        val generator = FakeGenerator(available = true, response = "I am not JSON")

        val plan = engine(generator).generatePlan(request())

        assertEquals(PlannerEngineId.DETERMINISTIC, plan.engine)
    }

    private fun engine(generator: OnDeviceTextGenerator) = LocalLlmWorkoutPlannerEngine(
        generator = generator,
        fallback = DeterministicStub,
        catalog = FakeCatalog
    )

    private fun request() = PlanRequest(
        daysPerWeek = 3,
        availableEquipment = setOf(EquipmentTag.BARBELL),
        muscleFatigue = emptyMap(),
        nowMillis = 0L
    )

    private class FakeGenerator(
        private val available: Boolean,
        private val response: String = "",
        private val failure: (() -> Unit)? = null
    ) : OnDeviceTextGenerator {
        var generateCalls: Int = 0
            private set

        override fun isAvailable(): Boolean = available

        override fun generate(prompt: String): String {
            generateCalls++
            failure?.invoke()
            return response
        }
    }

    private object DeterministicStub : WorkoutPlannerEngine {
        override val id: PlannerEngineId = PlannerEngineId.DETERMINISTIC

        override suspend fun generatePlan(request: PlanRequest): WeeklyPlan =
            WeeklyPlan(engine = PlannerEngineId.DETERMINISTIC, days = emptyList())
    }

    private object FakeCatalog : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = listOf(
            Exercise(
                id = "bench-press",
                name = "Bench Press",
                requiredEquipment = setOf(EquipmentTag.BARBELL),
                primaryMuscles = setOf(MuscleGroup.CHEST)
            )
        )
    }
}
