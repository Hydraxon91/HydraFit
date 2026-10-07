package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.workout.LoadKind

/** A user-entered best set for one exercise, used to seed the suggested-weight baseline. */
data class PersonalRecord(
    val exerciseId: String,
    val weightKg: Double,
    val reps: Int,
    /** Personal records are external-load only; added-load records are not part of this slice. */
    val loadKind: LoadKind = LoadKind.EXTERNAL
)
