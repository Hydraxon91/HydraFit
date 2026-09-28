package com.hydrafit.app.core.domain.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WeeklyPlanDto(val days: List<PlannedDayDto> = emptyList())

@Serializable
data class PlannedDayDto(val focus: String, val exercises: List<PlannedExerciseDto> = emptyList())

@Serializable
data class PlannedExerciseDto(val exerciseId: String, val sets: Int, val reps: Int)

private val planJson = Json { ignoreUnknownKeys = true }

fun parseWeeklyPlan(json: String, engine: PlannerEngineId): WeeklyPlan {
    val dto = planJson.decodeFromString<WeeklyPlanDto>(json)
    return WeeklyPlan(
        engine = engine,
        days = dto.days.mapIndexed { index, day ->
            WorkoutDay(
                dayIndex = index,
                focus = parseSplitFocus(day.focus),
                exercises = day.exercises.map {
                    PlannedExercise(exerciseId = it.exerciseId, sets = it.sets, reps = it.reps)
                }
            )
        }
    )
}

internal fun parseSplitFocus(value: String): SplitFocus =
    SplitFocus.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        ?: SplitFocus.FULL_BODY
