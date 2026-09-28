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
        var prompt = prompt(request)
        while (attempt < MAX_ATTEMPTS) {
            attempt++
            val plan = try {
                // Loading the model and generating are blocking, so keep them off the main thread.
                val output = withContext(Dispatchers.Default) {
                    generator.generate(prompt, PLAN_SCHEMA)
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
            // Tell the model why the previous answer was rejected instead of repeating it verbatim.
            prompt = prompt(request) + correction(request)
        }
        val cause = lastFailure
            ?: IllegalStateException("On-device plan did not satisfy the request")
        logger.onFallback(OnDevicePlannerFallback.UNEXPECTED_FAILURE, cause)
        return fallback.generatePlan(request)
    }

    private fun correction(request: PlanRequest): String =
        "\nYour previous answer was rejected. Return exactly ${request.daysPerWeek} day items, " +
            "and give every day 4 to 6 exercises whose exerciseId appears in the list."

    private suspend fun prompt(request: PlanRequest): String {
        val equipment = request.availableEquipment.joinToString(", ") { it.name }
        val fatigue = request.muscleFatigue.entries.joinToString(", ") {
            "${it.key.name}=${it.value}"
        }
        val availableExercises = catalog.all()
            .filter { it.isAvailableWith(request.availableEquipment) }

        val sets = request.setsPerExercise
        val days = request.daysPerWeek
        return buildString {
            appendLine("You are a strength coach.")
            appendLine("Reply with one JSON object only. No markdown. No prose.")
            appendLine("The object has one key \"days\": a list of exactly $days day items.")
            appendLine("Each day item has \"focus\" and \"exercises\".")
            appendLine("\"focus\" is one of: PUSH, PULL, LEGS, UPPER, LOWER, FULL_BODY.")
            appendLine("Each \"exercises\" list has 4 to 6 different exercise items.")
            appendLine(
                "Each exercise item has \"exerciseId\", \"sets\" (always $sets) and \"reps\"."
            )
            appendLine("Use 6 reps for compound lifts and 12 reps for isolation exercises.")
            appendLine("Split preference: ${request.splitPreference.name}")
            appendLine("Available equipment: $equipment")
            appendLine("Muscle fatigue (0.0-1.0): $fatigue")
            appendLine(
                "Every \"exerciseId\" must be copied exactly from this list. " +
                    "Never invent an id and never add a prefix:"
            )
            availableExercises.forEach { exercise ->
                appendLine("- ${exercise.id} (${exercise.name})")
            }
            appendLine(
                "Produce exactly $days day items and 4 to 6 exercises in every day. " +
                    "The example below only shows the shape; do not copy its counts:"
            )
            appendLine("""{"days":[<day>, <day>, ...]}""")
            appendLine(DAY_SHAPE)
            appendLine(
                """<exercise> = {"exerciseId":"<id from the list above>","sets":$sets,"reps":6}"""
            )
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 2

        const val DAY_SHAPE =
            """<day> = {"focus":"<PUSH|PULL|LEGS|UPPER|LOWER|FULL_BODY>",""" +
                """"exercises":[<exercise>, <exercise>, ...]}"""

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
