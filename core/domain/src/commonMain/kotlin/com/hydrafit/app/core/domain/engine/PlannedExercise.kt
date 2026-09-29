package com.hydrafit.app.core.domain.engine

data class PlannedExercise(
    val exerciseId: String,
    val sets: Int,
    val reps: Int,
    val suggestedWeightKg: Double? = null
)
