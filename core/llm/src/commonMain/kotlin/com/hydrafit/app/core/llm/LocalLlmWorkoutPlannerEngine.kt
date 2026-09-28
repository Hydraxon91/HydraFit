package com.hydrafit.app.core.llm

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.WeeklyPlan
import com.hydrafit.app.core.domain.engine.WeeklyPlanSanitizer
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.parseWeeklyPlan
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalLlmWorkoutPlannerEngine(
    private val generator: OnDeviceTextGenerator,
    private val fallback: WorkoutPlannerEngine,
    private val catalog: ExerciseCatalog,
    private val sanitizer: WeeklyPlanSanitizer,
    private val logger: OnDevicePlannerLogger = NoopOnDevicePlannerLogger
) : WorkoutPlannerEngine {

    override val id: PlannerEngineId = PlannerEngineId.LOCAL_LLM

    override suspend fun generatePlan(request: PlanRequest): WeeklyPlan {
        if (!generator.isAvailable()) return fallback.generatePlan(request)

        var attempt = 0
        var lastFailure: Throwable? = null
        while (attempt < MAX_ATTEMPTS) {
            attempt++
            val plan = try {
                // Loading the model and generating are blocking, so keep them off the main thread.
                val output = withContext(Dispatchers.Default) {
                    generator.generate(prompt(request), PLAN_SCHEMA)
                }
                sanitizer.sanitize(parseWeeklyPlan(output, PlannerEngineId.LOCAL_LLM), request)
            } catch (outOfMemory: OutOfMemoryError) {
                logger.onFallback(OnDevicePlannerFallback.OUT_OF_MEMORY, outOfMemory)
                return fallback.generatePlan(request)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                lastFailure = failure
                null
            }
            if (plan != null) return plan
        }
        val cause = lastFailure
            ?: IllegalStateException("On-device plan did not satisfy the request")
        logger.onFallback(OnDevicePlannerFallback.UNEXPECTED_FAILURE, cause)
        return fallback.generatePlan(request)
    }

    private suspend fun prompt(request: PlanRequest): String {
        val equipment = request.availableEquipment.joinToString(", ") { it.name }
        val fatigue = request.muscleFatigue.entries.joinToString(", ") {
            "${it.key.name}=${it.value}"
        }
        val exerciseIds = catalog.all()
            .filter { it.isAvailableWith(request.availableEquipment) }
            .joinToString(", ") { it.id }

        val sets = request.setsPerExercise
        return buildString {
            appendLine("You are a strength coach. Reply with one JSON object only.")
            appendLine("Do not use markdown, prose, or nested \"days\" inside a day.")
            appendLine("Top level: {\"days\":[<day>, <day>, ...]}.")
            appendLine("Build exactly ${request.daysPerWeek} days.")
            appendLine("Each day has a \"focus\" and 4 to 6 \"exercises\".")
            appendLine("Available equipment: $equipment")
            appendLine("Muscle fatigue (0.0-1.0): $fatigue")
            appendLine("Choose ONLY exerciseId values from this list: $exerciseIds")
            appendLine(
                "Use one focus value per day from: PUSH, PULL, LEGS, UPPER, LOWER, FULL_BODY"
            )
            appendLine("Use exactly $sets sets for every exercise.")
            appendLine("Use 6 reps for compound lifts and 12 reps for isolation exercises.")
            appendLine(
                "Example: " +
                    """{"days":[{"focus":"PUSH","exercises":""" +
                    """[{"exerciseId":"barbell-bench-press","sets":$sets,"reps":6},""" +
                    """{"exerciseId":"overhead-press","sets":$sets,"reps":6}]},""" +
                    """{"focus":"PULL","exercises":""" +
                    """[{"exerciseId":"barbell-row","sets":$sets,"reps":6},""" +
                    """{"exerciseId":"barbell-curl","sets":$sets,"reps":12}]}]}"""
            )
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 2

        val PLAN_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "days": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "focus": {
                        "type": "string",
                        "enum": ["PUSH", "PULL", "LEGS", "UPPER", "LOWER", "FULL_BODY"]
                      },
                      "exercises": {
                        "type": "array",
                        "items": {
                          "type": "object",
                          "properties": {
                            "exerciseId": {"type": "string"},
                            "sets": {"type": "integer"},
                            "reps": {"type": "integer"}
                          },
                          "required": ["exerciseId", "sets", "reps"]
                        }
                      }
                    },
                    "required": ["focus", "exercises"]
                  }
                }
              },
              "required": ["days"]
            }
        """.trimIndent()
    }
}
