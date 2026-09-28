package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class GenerateWeeklySplitUseCaseTest {

    @Test
    fun delegatesToTheInjectedEngine() = runTest {
        val expected = WeeklyPlan(engine = PlannerEngineId.DETERMINISTIC, days = emptyList())
        val useCase = GenerateWeeklySplitUseCase(FakeEngine(expected))

        val actual = useCase(request())

        assertEquals(expected, actual)
    }

    @Test
    fun passesTheRequestThroughUnchanged() = runTest {
        val engine = FakeEngine(WeeklyPlan(PlannerEngineId.DETERMINISTIC, emptyList()))
        val request = request()

        GenerateWeeklySplitUseCase(engine)(request)

        assertEquals(request, engine.received)
    }

    private fun request() = PlanRequest(
        daysPerWeek = 4,
        availableEquipment = setOf(EquipmentTag.DUMBBELL),
        muscleFatigue = mapOf(MuscleGroup.CHEST to 0.5),
        nowMillis = 123L
    )

    private class FakeEngine(private val plan: WeeklyPlan) : WorkoutPlannerEngine {
        override val id: PlannerEngineId = PlannerEngineId.DETERMINISTIC
        var received: PlanRequest? = null

        override suspend fun generatePlan(request: PlanRequest): WeeklyPlan {
            received = request
            return plan
        }
    }
}
