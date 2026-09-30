package com.hydrafit.app.core.domain.engine

/** A user-entered best set for one exercise, used to seed the suggested-weight baseline. */
data class PersonalRecord(val exerciseId: String, val weightKg: Double, val reps: Int)
