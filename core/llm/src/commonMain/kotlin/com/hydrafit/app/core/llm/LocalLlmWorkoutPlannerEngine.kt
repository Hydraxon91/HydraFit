package com.hydrafit.app.core.llm

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.WeeklyPlan
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.parseWeeklyPlan
import kotlin.coroutines.cancellation.CancellationException

class LocalLlmWorkoutPlannerEngine(
    private val generator: OnDeviceTextGenerator,
    private val fallback: WorkoutPlannerEngine,
    private val catalog: ExerciseCatalog
) : WorkoutPlannerEngine {

    override val id: PlannerEngineId = PlannerEngineId.LOCAL_LLM

    override suspend fun generatePlan(request: PlanRequest): WeeklyPlan {
        if (!generator.isAvailable()) return fallback.generatePlan(request)
        return try {
            parseWeeklyPlan(generator.generate(prompt(request)), PlannerEngineId.LOCAL_LLM)
        } catch (outOfMemory: OutOfMemoryError) {
            fallback.generatePlan(request)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            fallback.generatePlan(request)
        }
    }

    private suspend fun prompt(request: PlanRequest): String {
        val equipment = request.availableEquipment.joinToString(", ") { it.name }
        val fatigue = request.muscleFatigue.entries.joinToString(", ") {
            "${it.key.name}=${it.value}"
        }
        val exerciseIds = catalog.all()
            .filter { it.isAvailableWith(request.availableEquipment) }
            .joinToString(", ") { it.id }

        return buildString {
            appendLine("You are a strength coach. Reply with JSON only, no prose.")
            appendLine("Days per week: ${request.daysPerWeek}")
            appendLine("Available equipment: $equipment")
            appendLine("Muscle fatigue (0.0-1.0): $fatigue")
            appendLine("Choose ONLY exerciseId values from this list: $exerciseIds")
            appendLine(
                "JSON shape: " +
                    """{"days":[{"focus":"PUSH|PULL|LEGS|UPPER|LOWER|FULL_BODY",""" +
                    """"exercises":[{"exerciseId":"...","sets":3,"reps":8}]}]}"""
            )
        }
    }
}
