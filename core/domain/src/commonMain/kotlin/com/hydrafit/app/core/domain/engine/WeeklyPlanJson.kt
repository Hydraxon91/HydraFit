package com.hydrafit.app.core.domain.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
data class PlannedDayDto(val focus: String, val exercises: List<PlannedExerciseDto> = emptyList())

@Serializable
data class PlannedExerciseDto(
    val exerciseId: String,
    // Defaulted so a local-LLM reply may omit them: the sanitizer applies the app's own set/rep
    // scheme before the plan is shown, so the model does not need to emit either.
    val sets: Int = 1,
    val reps: Int = 1,
    val suggestedWeightKg: Double? = null
)

private const val MIN_SETS = 1
private const val MAX_SETS = 10
private const val MIN_REPS = 1
private const val MAX_REPS = 100

private val planJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun parseWeeklyPlan(json: String, engine: PlannerEngineId): WeeklyPlan {
    val root = planJson.parseToJsonElement(extractJsonObject(json))
    return WeeklyPlan(
        engine = engine,
        days = collectDays(root).mapIndexed { index, day ->
            WorkoutDay(
                dayIndex = index,
                focus = parseSplitFocus(day.focus),
                exercises = day.exercises.map { exercise ->
                    PlannedExercise(
                        exerciseId = exercise.exerciseId,
                        sets = exercise.sets.coerceIn(MIN_SETS, MAX_SETS),
                        reps = exercise.reps.coerceIn(MIN_REPS, MAX_REPS),
                        suggestedWeightKg = exercise.suggestedWeightKg
                    )
                }
            )
        }
    )
}

/**
 * On-device models wrap the plan in prose, a markdown fence, and sometimes repeated
 * `{"days": [...]}` layers. Collect every day object wherever it sits, in order.
 */
private fun collectDays(element: JsonElement): List<PlannedDayDto> = when (element) {
    is JsonArray -> element.flatMap(::collectDays)
    is JsonObject -> if (element.containsKey("focus")) {
        listOfNotNull(decodeDay(element))
    } else {
        element.values.flatMap(::collectDays)
    }
    else -> emptyList()
}

private fun decodeDay(element: JsonObject): PlannedDayDto? = runCatching {
    planJson.decodeFromJsonElement<PlannedDayDto>(element)
}.getOrNull()

/**
 * Return the first balanced JSON object in [text] so a lenient decoder can read it. On-device
 * models sometimes stop just before the closing brackets; when the reply ends at a value boundary
 * the missing closers are appended so a complete prefix can still be read. A reply cut off inside a
 * string is left alone — the decoder rejects it and the planner falls back.
 */
internal fun extractJsonObject(text: String): String {
    val start = text.indexOf('{')
    if (start < 0) return text

    val closers = ArrayDeque<Char>()
    var inString = false
    var escaped = false
    for (index in start until text.length) {
        val character = text[index]
        when {
            escaped -> escaped = false
            inString && character == '\\' -> escaped = true
            character == '"' -> inString = !inString
            !inString && character == '{' -> closers.addLast('}')
            !inString && character == '[' -> closers.addLast(']')
            !inString && (character == '}' || character == ']') -> {
                if (closers.isNotEmpty() && closers.last() == character) {
                    closers.removeLast()
                    if (closers.isEmpty()) return text.substring(start, index + 1)
                }
            }
        }
    }

    if (inString) return text.substring(start)
    val body = text.substring(start).trimEnd()
    return buildString {
        append(body.removeSuffix(",").trimEnd())
        while (closers.isNotEmpty()) append(closers.removeLast())
    }
}

internal fun parseSplitFocus(value: String): SplitFocus =
    SplitFocus.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        ?: SplitFocus.FULL_BODY
