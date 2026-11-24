package com.hydrafit.app.core.network

import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerExerciseCounts
import com.hydrafit.app.core.domain.engine.PlannerPromptFragments
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.engine.SplitResolver

/** The text sent to a model and the JSON schema its answer must follow. */
data class PlannerPrompt(val text: String, val schema: PlanSchema)

/**
 * Builds the model prompt and response schema for a weekly plan. Shared by every model-backed
 * engine; transport, retries and failure mapping stay with the engine that calls the provider.
 */
class PlannerPromptBuilder {

    fun build(request: PlanRequest, availableIds: List<String>): PlannerPrompt {
        val exerciseIds = availableIds.joinToString(", ")
        val focusSequence = SplitResolver.focusSequence(
            request.splitPreference,
            request.daysPerWeek
        )

        val prompt = buildString {
            appendLine("You are a strength coach. Build a weekly workout plan.")
            appendLine("Days per week: ${request.daysPerWeek}")
            appendLine("Use these focuses, one per day, in this exact order:")
            focusSequence.forEachIndexed { index, focus ->
                appendLine("Day ${index + 1}: ${focus.name}")
            }
            appendLine("Training goal: ${request.goal.name}")
            appendLine(PlannerPromptFragments.equipmentLine(request))
            PlannerPromptFragments.equipmentCapsLine(request)?.let { appendLine(it) }
            appendLine(PlannerPromptFragments.fatigueLine(request, "Current muscle fatigue"))
            appendLine(PlannerPromptFragments.periodizationLine(request))
            PlannerPromptFragments.deloadInstruction(request)?.let { appendLine(it) }
            appendLine("Choose ONLY exerciseId values from this list: $exerciseIds")
            appendLine(PlannerPromptFragments.exerciseCountGuidance(request))
            appendLine(PlannerPromptFragments.slotGuidance())
            appendLine(
                "Follow the focus schedule above exactly; a focus may appear on more than one " +
                    "day. Do not reuse a compound lift across days; isolation exercises may repeat."
            )
            appendLine(
                "Use ${request.setsPerExercise} sets for compound lifts and " +
                    "${request.accessorySetsPerExercise} sets for accessory exercises."
            )
            appendLine(PlannerPromptFragments.volumeRepsGuidance(request))
            appendLine(PlannerPromptFragments.directArmCoverageGuidance(request))
            appendLine(PlannerPromptFragments.weeklyTargetsLine(request))
            appendLine(PlannerPromptFragments.weeklyFloorsLine(request))
            PlannerPromptFragments.preferencesLine(request)?.let { appendLine(it) }
            appendLine("Prefer exercises whose muscles are less fatigued.")
            val recentlyUsed = request.recentExerciseIdsByPattern
            if (recentlyUsed.isNotEmpty()) {
                val history = recentlyUsed.entries
                    .sortedBy { it.key.name }
                    .joinToString("; ") { (pattern, ids) ->
                        "${pattern.name}: ${ids.sorted().joinToString(", ")}"
                    }
                appendLine(
                    "Used in the previous accepted week (prefer a different exercise for the " +
                        "same movement pattern when an equally suitable option exists): $history"
                )
            }
            PlannerPromptFragments.recentWeightsList(request)?.let { weights ->
                appendLine(
                    "Recent working weights (use them to suggest a sensible weight for each " +
                        "exercise): $weights"
                )
                appendLine(
                    "Give a \"suggestedWeightKg\" number only for exercises whose resistance is " +
                        "external weight; omit it for bodyweight and bodyweight-plus-added-load " +
                        "movements."
                )
            }
            PlannerPromptFragments.progressedWeightsList(request)?.let { progressed ->
                appendLine(
                    "Estimated one-rep maximums (already adjusted for progressive overload): " +
                        progressed
                )
                appendLine(PlannerPromptFragments.PROGRESSED_WEIGHTS_NOTE)
            }
        }

        return PlannerPrompt(text = prompt, schema = planSchema(request))
    }

    private fun planSchema(request: PlanRequest): PlanSchema = PlanSchema(
        type = "OBJECT",
        properties = mapOf(
            "days" to PlanSchema(
                type = "ARRAY",
                minItems = request.daysPerWeek,
                maxItems = request.daysPerWeek,
                items = PlanSchema(
                    type = "OBJECT",
                    properties = mapOf(
                        "focus" to PlanSchema(
                            type = "STRING",
                            enum = SplitFocus.entries.map { it.name }
                        ),
                        "exercises" to PlanSchema(
                            type = "ARRAY",
                            minItems = PlannerExerciseCounts.FLOOR_PER_DAY,
                            maxItems = PlannerExerciseCounts.mustHaveMax(request.sessionLength),
                            items = PlanSchema(
                                type = "OBJECT",
                                properties = exerciseSchemaProperties(request),
                                required = listOf("exerciseId", "sets", "reps")
                            )
                        )
                    ),
                    required = listOf("focus", "exercises")
                )
            )
        ),
        required = listOf("days")
    )

    private fun exerciseSchemaProperties(request: PlanRequest): Map<String, PlanSchema> = buildMap {
        // The prompt lists the valid ids and the engine repairs returned names; an `enum` here
        // is not used because a provider rejected the whole request (400) when it was present.
        put("exerciseId", PlanSchema(type = "STRING"))
        put("sets", PlanSchema(type = "INTEGER"))
        put("reps", PlanSchema(type = "INTEGER"))
        if (request.includeWorkoutData) {
            put("suggestedWeightKg", PlanSchema(type = "NUMBER"))
        }
    }
}
