package com.hydrafit.app.core.domain.engine

data class WorkoutDay(
    val dayIndex: Int,
    val focus: SplitFocus,
    val exercises: List<PlannedExercise>
)
