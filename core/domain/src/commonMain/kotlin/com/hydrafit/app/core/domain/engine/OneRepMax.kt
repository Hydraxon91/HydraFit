package com.hydrafit.app.core.domain.engine

/** Estimates a one-rep max from a working set with the Epley formula. */
object OneRepMax {
    fun estimate(weightKg: Double, reps: Int): Double = weightKg * (1.0 + reps / 30.0)
}
