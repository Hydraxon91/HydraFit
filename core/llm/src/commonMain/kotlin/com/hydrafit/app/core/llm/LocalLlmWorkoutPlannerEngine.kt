package com.hydrafit.app.core.llm

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.WeeklyPlan
import com.hydrafit.app.core.domain.engine.WeeklyPlanSanitizer
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.parseWeeklyPlan
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.time.isoDateUtc
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

        val availableExercises = catalog.all()
            .filter { it.isAvailableWith(request.availableEquipment) }
        val availableIds = availableExercises.map { it.id }
        var attempt = 0
        var lastFailure: Throwable? = null
        var prompt = prompt(request, availableExercises)
        val schema = planSchema(request.daysPerWeek, availableExercises.size)
        while (attempt < MAX_ATTEMPTS) {
            attempt++
            val plan = try {
                // Loading the model and generating are blocking, so keep them off the main thread.
                val output = withContext(Dispatchers.Default) {
                    generator.generate(prompt, schema)
                }
                val parsed = parseWeeklyPlan(output, PlannerEngineId.LOCAL_LLM)
                sanitizer.sanitize(parsed.withCatalogIds(availableIds), request)
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
            prompt = prompt(request, availableExercises) + correction(request)
        }
        val cause = lastFailure
            ?: IllegalStateException("On-device plan did not satisfy the request")
        logger.onFallback(OnDevicePlannerFallback.UNEXPECTED_FAILURE, cause)
        return fallback.generatePlan(request)
    }

    private fun correction(request: PlanRequest): String =
        "\nYour previous answer was rejected. Return exactly ${request.daysPerWeek} day items, " +
            "and give every day 4 to 6 exercises by their list number."

    /**
     * The model returns each exercise as its 1-based number from the prompt list. Reading numbers
     * keeps the constrained grammar tiny (the whole catalog as an enum was too slow) while still
     * making an id outside the catalog impossible. Plain ids pass through for robustness.
     */
    private fun WeeklyPlan.withCatalogIds(ids: List<String>): WeeklyPlan = copy(
        days = days.map { day ->
            day.copy(
                exercises = day.exercises.mapNotNull { planned ->
                    val number = planned.exerciseId.toIntOrNull()
                    val exerciseId = if (number != null) {
                        ids.getOrNull(number - 1)
                    } else {
                        planned.exerciseId
                    }
                    exerciseId?.let { planned.copy(exerciseId = it) }
                }
            )
        }
    )

    private fun prompt(request: PlanRequest, availableExercises: List<Exercise>): String {
        val equipment = request.availableEquipment.joinToString(", ") { it.displayName }
        val fatigue = request.muscleFatigue.entries.joinToString(", ") {
            "${it.key.name}=${it.value}"
        }

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
            appendLine("Training goal: ${request.goal.name}")
            appendLine(
                "Use ${request.goal.compoundReps} reps for compound lifts and " +
                    "${request.goal.isolationReps} reps for isolation exercises."
            )
            appendLine("Split preference: ${request.splitPreference.name}")
            appendLine("Available equipment: $equipment")
            appendLine("Muscle fatigue (0.0-1.0): $fatigue")
            appendLine("Choose every exercise by its number from this list:")
            availableExercises.forEachIndexed { index, exercise ->
                appendLine("${index + 1}. ${exercise.id} (${exercise.name})")
            }
            appendLine(
                "\"exerciseId\" is that number written as a string, for example \"1\". " +
                    "Never invent a number and never repeat one inside a day."
            )
            val recentlyUsedNumbers = request.recentExerciseIdsByPattern.values
                .flatten()
                .mapNotNull { id ->
                    availableExercises.indexOfFirst { it.id == id }
                        .takeIf { it >= 0 }
                        ?.plus(1)
                }
                .distinct()
                .sorted()
            if (recentlyUsedNumbers.isNotEmpty()) {
                appendLine(
                    "Avoid reusing these list numbers unless no other suitable exercise exists: " +
                        recentlyUsedNumbers.joinToString(", ")
                )
            }
            if (request.includeWorkoutData && request.recentWeights.isNotEmpty()) {
                val weights = request.recentWeights.joinToString("; ") {
                    "${it.exerciseId} ${isoDateUtc(it.performedAtMillis)}: " +
                        "${it.weightKg}kg x ${it.reps}"
                }
                appendLine(
                    "Recent working weights (suggest a sensible weight for each exercise): $weights"
                )
            }
            appendLine(
                "Produce exactly $days day items and 4 to 6 exercises in every day. " +
                    "The example below only shows the shape; do not copy its counts:"
            )
            appendLine("""{"days":[<day>, <day>, ...]}""")
            appendLine(DAY_SHAPE)
            appendLine(
                """<exercise> = {"exerciseId":"<list number>","sets":$sets,"reps":6}"""
            )
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
        const val MIN_EXERCISES_PER_DAY = 4
        const val MAX_EXERCISES_PER_DAY = 6

        const val DAY_SHAPE =
            """<day> = {"focus":"<PUSH|PULL|LEGS|UPPER|LOWER|FULL_BODY>",""" +
                """"exercises":[<exercise>, <exercise>, ...]}"""

        /**
         * Constrained decoding compiles this schema into a grammar. `minItems`/`maxItems` force the
         * requested day and exercise counts, and the `enum` of list numbers makes a value outside
         * the prompt's list impossible. Numbers keep the grammar small enough to stay fast; an
         * empty catalog keeps a plain string so the enum stays valid.
         */
        fun planSchema(days: Int, exerciseCount: Int): String {
            val idSchema = if (exerciseCount <= 0) {
                """{"type": "string"}"""
            } else {
                val numbers = (1..exerciseCount).joinToString(", ") { "\"$it\"" }
                """{"type": "string", "enum": [$numbers]}"""
            }
            return """
                {
                  "type": "object",
                  "properties": {
                    "days": {
                      "type": "array",
                      "minItems": $days,
                      "maxItems": $days,
                      "items": {
                        "type": "object",
                        "properties": {
                          "focus": {
                            "type": "string",
                            "enum": ["PUSH", "PULL", "LEGS", "UPPER", "LOWER", "FULL_BODY"]
                          },
                          "exercises": {
                            "type": "array",
                            "minItems": $MIN_EXERCISES_PER_DAY,
                            "maxItems": $MAX_EXERCISES_PER_DAY,
                            "items": {
                              "type": "object",
                              "properties": {
                                "exerciseId": $idSchema,
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
}
