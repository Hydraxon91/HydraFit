package com.hydrafit.app.core.domain.fatigue

/** A muscle the set involves, with its involvement weight in (0.0, 1.0]. */
data class MuscleTarget(val muscle: MuscleGroup, val weight: Double)
