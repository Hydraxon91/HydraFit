package com.hydrafit.app.core.domain.engine

import kotlin.math.round

/**
 * Tunable defaults for the deterministic weight suggestion.
 *
 * The suggested weight follows the evidence-based NSCA reps-to-%1RM relationship, so fewer reps
 * earn a heavier load and more reps a lighter one. The NSCA table describes a set taken to failure
 * (RPE 10); [rirBuffer] discounts it to a working-set effort, and [roundToKg] keeps the result on
 * real plate increments.
 */
data class SuggestedWeightConfig(
    val nscaCurve: Map<Int, Double> = DEFAULT_NSCA_CURVE,
    val rirBuffer: Double = DEFAULT_RIR_BUFFER,
    val roundToKg: Double = DEFAULT_ROUND_TO_KG,
    val maxRepsForEstimate: Int = DEFAULT_MAX_REPS,
    val highRepRirBuffer: Double = rirBuffer * HIGH_REP_BUFFER_FRACTION
) {
    init {
        require(rirBuffer in 0.0..<1.0) { "rirBuffer must be in [0.0, 1.0)" }
        require(highRepRirBuffer in 0.0..rirBuffer) {
            "highRepRirBuffer must be between 0.0 and rirBuffer"
        }
        require(roundToKg > 0.0) { "roundToKg must be positive" }
        require(maxRepsForEstimate >= 1) { "maxRepsForEstimate must be at least 1" }
        require(nscaCurve.isNotEmpty()) { "nscaCurve must not be empty" }
        require(nscaCurve.values.all { it > 0.0 && it <= 1.0 }) {
            "curve intensities must be in (0.0, 1.0]"
        }
    }

    /** The %1RM to train at for the planned rep count: the NSCA curve discounted by the RIR buffer. */
    fun intensityForReps(reps: Int): Double = curveFor(reps) * (1.0 - bufferForReps(reps))

    /**
     * A flat percentage cut leaves far more reps in reserve at high reps than at low reps, so the
     * cut is [rirBuffer] up to [TAPER_START_REPS] and eases linearly to [highRepRirBuffer] by
     * [TAPER_END_REPS]. Product default fitted loosely to recorded plan-versus-logged loads; it is
     * not a validated prescription.
     */
    fun bufferForReps(reps: Int): Double = when {
        reps <= TAPER_START_REPS -> rirBuffer
        reps >= TAPER_END_REPS -> highRepRirBuffer
        else -> {
            val progress =
                (reps - TAPER_START_REPS).toDouble() / (TAPER_END_REPS - TAPER_START_REPS)
            rirBuffer + (highRepRirBuffer - rirBuffer) * progress
        }
    }

    fun roundToIncrement(weightKg: Double): Double = round(weightKg / roundToKg) * roundToKg

    fun hasUsableEstimate(reps: Int): Boolean = reps in 1..maxRepsForEstimate

    /** Converts an e1RM bound using the prescribed reps and the existing load rounding policy. */
    fun workingWeightFor(
        estimatedOneRepMaxKg: Double,
        reps: Int,
        intensityScale: Double = 1.0
    ): Double = roundToIncrement(estimatedOneRepMaxKg * intensityForReps(reps) * intensityScale)

    /** The closest tabulated NSCA intensity for a rep count, clamped to the table's bounds. */
    private fun curveFor(reps: Int): Double {
        val clamped = reps.coerceIn(nscaCurve.keys.min(), nscaCurve.keys.max())
        return nscaCurve[clamped] ?: DEFAULT_INTENSITY
    }

    companion object {
        const val DEFAULT_ROUND_TO_KG: Double = 2.5
        const val DEFAULT_MAX_REPS: Int = 15
        const val DEFAULT_INTENSITY: Double = 0.7
        const val DEFAULT_RIR_BUFFER: Double = 0.10

        /** The high-rep buffer is this share of the low-rep buffer (0.02 of the default 0.10). */
        const val HIGH_REP_BUFFER_FRACTION: Double = 0.2
        const val TAPER_START_REPS: Int = 6
        const val TAPER_END_REPS: Int = 12

        /** NSCA training-load chart: reps taken to failure, expressed as a fraction of 1RM. */
        val DEFAULT_NSCA_CURVE: Map<Int, Double> = mapOf(
            1 to 1.00,
            2 to 0.95,
            3 to 0.93,
            4 to 0.90,
            5 to 0.87,
            6 to 0.85,
            7 to 0.83,
            8 to 0.80,
            9 to 0.77,
            10 to 0.75,
            11 to 0.73,
            12 to 0.70,
            13 to 0.68,
            14 to 0.67,
            15 to 0.65,
            16 to 0.64,
            17 to 0.63,
            18 to 0.62,
            19 to 0.61,
            20 to 0.60
        )
    }
}
