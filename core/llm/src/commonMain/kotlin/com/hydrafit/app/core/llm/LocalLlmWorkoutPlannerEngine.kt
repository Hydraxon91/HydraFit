package com.hydrafit.app.core.llm

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.OnDevicePlanProgressReporter
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.PlannerExerciseCounts
import com.hydrafit.app.core.domain.engine.PlannerPromptFragments
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.engine.SplitResolver
import com.hydrafit.app.core.domain.engine.WeeklyPlan
import com.hydrafit.app.core.domain.engine.WeeklyPlanSanitizer
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.parseWeeklyPlan
import com.hydrafit.app.core.domain.equipment.Exercise
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalLlmWorkoutPlannerEngine(
    private val generator: OnDeviceTextGenerator,
    private val fallback: WorkoutPlannerEngine,
    private val catalog: ExerciseCatalog,
    private val sanitizer: WeeklyPlanSanitizer,
    private val logger: OnDevicePlannerLogger = NoopOnDevicePlannerLogger,
    private val progressReporter: OnDevicePlanProgressReporter = OnDevicePlanProgressReporter.Noop
) : WorkoutPlannerEngine {

    override val id: PlannerEngineId = PlannerEngineId.LOCAL_LLM

    override suspend fun generatePlan(request: PlanRequest): WeeklyPlan {
        if (!generator.isAvailable()) return fallback.generatePlan(request)
        try {
            return generateOnDevice(request)
        } finally {
            // Clear no matter how the attempt ends (success, fallback, OOM, cancellation).
            progressReporter.clear()
        }
    }

    private suspend fun generateOnDevice(request: PlanRequest): WeeklyPlan {
        val availableExercises = catalog.all()
            .filter { it.isAvailableWith(request.availableEquipment) }
        val availableIds = availableExercises.map { it.id }
        val focusSequence = SplitResolver.focusSequence(
            request.splitPreference,
            request.daysPerWeek
        )
        var attempt = 0
        var lastFailure: Throwable? = null
        var lastRejection: String? = null
        var prompt = prompt(request, availableExercises, focusSequence)
        val schema = planSchema(
            focusSequence,
            availableExercises.size,
            request.includeWorkoutData
        )
        while (attempt < MAX_ATTEMPTS) {
            attempt++
            // Loading the model and generating are blocking, so keep them off the main thread.
            val output = try {
                withContext(Dispatchers.Default) {
                    generator.generate(prompt, schema) { progress ->
                        progressReporter.report(progress)
                    }
                }
            } catch (outOfMemory: OutOfMemoryError) {
                logger.onFallback(OnDevicePlannerFallback.OUT_OF_MEMORY, outOfMemory)
                return fallback.generatePlan(request)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                lastFailure = failure
                null
            }
            if (output == null) {
                // A generation exception (native/backend failure) is not fixed by a retry, and a
                // second attempt doubles an already minutes-long wait; fall straight back instead.
                // The retry loop is kept for parsed-but-rejected (incomplete) plans below.
                break
            }

            val parsed = try {
                parseWeeklyPlan(output, PlannerEngineId.LOCAL_LLM)
            } catch (malformed: IllegalArgumentException) {
                // parseWeeklyPlan raises a kotlinx.serialization.SerializationException (an
                // IllegalArgumentException) on truncated or syntactically invalid JSON. A retry
                // will not parse it, and a second full generation doubles an already minutes-long
                // wait on a phone. Fall straight back to the built-in plan.
                lastFailure = malformed
                break
            }
            val mapped = parsed.withCatalogIds(availableIds)
            val sanitized = sanitizer.sanitize(mapped, request)
            if (sanitized != null) return sanitized
            lastRejection = describeRejection(mapped, request, availableIds)
            // Tell the model why the previous answer was rejected instead of repeating it verbatim.
            prompt = prompt(request, availableExercises, focusSequence) + correction(request)
        }
        val cause = lastFailure
            ?: IllegalStateException(
                "On-device plan did not satisfy the request" +
                    (lastRejection?.let { ": $it" } ?: "")
            )
        logger.onFallback(OnDevicePlannerFallback.UNEXPECTED_FAILURE, cause)
        return fallback.generatePlan(request)
    }

    /** A compact description of why a parsed plan was rejected, for on-device log triage. */
    private fun describeRejection(
        plan: WeeklyPlan,
        request: PlanRequest,
        availableIds: List<String>
    ): String {
        val known = availableIds.toHashSet()
        val perDay = plan.days.joinToString(prefix = "[", postfix = "]") { day ->
            val unknown = day.exercises.count { it.exerciseId !in known }
            "${day.exercises.size}($unknown unknown)"
        }
        return "days=${plan.days.size}/${request.daysPerWeek}, per-day=$perDay"
    }

    private fun correction(request: PlanRequest): String =
        "\nYour previous answer was rejected. Return exactly ${request.daysPerWeek} day items, " +
            "and give every day ${PlannerExerciseCounts.TARGET_MIN_PER_DAY} to " +
            "${PlannerExerciseCounts.TARGET_MAX_PER_DAY} exercises by their list number."

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

    private fun prompt(
        request: PlanRequest,
        availableExercises: List<Exercise>,
        focusSequence: List<SplitFocus>
    ): String {
        val days = request.daysPerWeek
        return buildString {
            appendLine("You are a strength coach.")
            appendLine("Reply with one JSON object only. No markdown. No prose.")
            appendLine("The object has one key \"days\": a list of exactly $days day items.")
            appendLine("Each day item has \"focus\" and \"exercises\".")
            appendLine("Use these focuses, one per day, in this exact order:")
            focusSequence.forEachIndexed { index, focus ->
                appendLine("Day ${index + 1}: ${focus.name}")
            }
            appendLine(
                "Each \"exercises\" list has ${PlannerExerciseCounts.TARGET_MIN_PER_DAY} to " +
                    "${PlannerExerciseCounts.TARGET_MAX_PER_DAY} different exercise items."
            )
            appendLine(
                "Each exercise item has only \"exerciseId\". Do not include sets or reps; " +
                    "the app fills those in from the training goal."
            )
            appendLine("Training goal: ${request.goal.name}")
            appendLine(PlannerPromptFragments.periodizationLine(request))
            PlannerPromptFragments.deloadInstruction(request)?.let { appendLine(it) }
            appendLine(PlannerPromptFragments.volumeRepsGuidance(request))
            appendLine(PlannerPromptFragments.equipmentLine(request))
            PlannerPromptFragments.equipmentCapsLine(request)?.let { appendLine(it) }
            appendLine(PlannerPromptFragments.fatigueLine(request, "Muscle fatigue"))
            appendLine("Choose every exercise by its number from this list:")
            availableExercises.forEachIndexed { index, exercise ->
                appendLine("${index + 1}. ${exercise.id} (${exercise.name})")
            }
            appendLine(
                "\"exerciseId\" is that number written as a string, for example \"1\". " +
                    "Never invent a number and never repeat one inside a day."
            )
            appendLine(
                "Give each day a different focus from the schedule above, and do not reuse a " +
                    "compound lift across days; isolation exercises may repeat."
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
            PlannerPromptFragments.recentWeightsList(request)?.let { weights ->
                appendLine(
                    "Recent working weights (suggest a sensible weight for each exercise): $weights"
                )
                appendLine(
                    "Also give every exercise a \"suggestedWeightKg\" number based on that history."
                )
            }
            PlannerPromptFragments.progressedWeightsList(request)?.let { progressed ->
                appendLine(
                    "Progressed starting weights by list number (already adjusted for " +
                        "progressive overload): $progressed"
                )
                appendLine(PlannerPromptFragments.PROGRESSED_WEIGHTS_NOTE)
            }
            appendLine(
                "Produce exactly $days day items and " +
                    "${PlannerExerciseCounts.TARGET_MIN_PER_DAY} to " +
                    "${PlannerExerciseCounts.TARGET_MAX_PER_DAY} exercises in every day. " +
                    "The example below only shows the shape; do not copy its counts:"
            )
            appendLine("""{"days":[<day>, <day>, ...]}""")
            appendLine(DAY_SHAPE)
            appendLine("""<exercise> = {"exerciseId":"<list number>"}""")
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 2

        const val DAY_SHAPE =
            """<day> = {"focus":"<PUSH|PULL|LEGS|UPPER|LOWER|FULL_BODY>",""" +
                """"exercises":[<exercise>, <exercise>, ...]}"""

        /**
         * Constrained decoding compiles this schema into a grammar. `minItems`/`maxItems` force the
         * requested day and exercise counts, each day's `focus` enum pins it to the resolved split
         * (so the model cannot repeat one focus all week), and the `enum` of list numbers makes a
         * value outside the prompt's list impossible. Numbers keep the grammar small enough to stay
         * fast; an empty catalog keeps a plain string so the enum stays valid.
         */
        fun planSchema(
            focusSequence: List<SplitFocus>,
            exerciseCount: Int,
            includeSuggestedWeight: Boolean
        ): String {
            val days = focusSequence.size
            val idSchema = if (exerciseCount <= 0) {
                """{"type": "string"}"""
            } else {
                val numbers = (1..exerciseCount).joinToString(", ") { "\"$it\"" }
                """{"type": "string", "enum": [$numbers]}"""
            }
            val weightField = if (includeSuggestedWeight) {
                """,
                                "suggestedWeightKg": {"type": "number"}"""
            } else {
                ""
            }
            val daySchemas = focusSequence.joinToString(",\n                      ") { focus ->
                daySchema(focus.name, idSchema, weightField)
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
                        "anyOf": [
                      $daySchemas
                        ]
                      }
                    }
                  },
                  "required": ["days"]
                }
            """.trimIndent()
        }

        private fun daySchema(focus: String, idSchema: String, weightField: String): String = """
            {
              "type": "object",
              "properties": {
                "focus": {"type": "string", "enum": ["$focus"]},
                "exercises": {
                  "type": "array",
                  "minItems": ${PlannerExerciseCounts.TARGET_MIN_PER_DAY},
                  "maxItems": ${PlannerExerciseCounts.TARGET_MAX_PER_DAY},
                  "items": {
                    "type": "object",
                    "properties": {
                      "exerciseId": $idSchema$weightField
                    },
                    "required": ["exerciseId"]
                  }
                }
              },
              "required": ["focus", "exercises"]
            }
        """.trimIndent()
    }
}
