package com.hydrafit.app.core.domain.engine

/**
 * Tunable defaults for the weekly periodization cycle.
 *
 * A "week" is one accepted plan (accept-ordinal, not a calendar week): accepting a plan advances the
 * cycle by exactly one week, so skipping or accepting irregularly can never shift the deload.
 *
 * The deload scales and the cycle length are starting points, not physiological facts — like the
 * fatigue half-lives and the NSCA percentage table, they are expected to be tuned.
 */
data class PeriodizationConfig(
    val cycleLength: Int = DEFAULT_CYCLE_LENGTH,
    val deloadWeek: Int = DEFAULT_DELOAD_WEEK,
    val deloadVolumeScale: Double = DEFAULT_DELOAD_VOLUME_SCALE,
    val deloadIntensityScale: Double = DEFAULT_DELOAD_INTENSITY_SCALE
) {
    init {
        require(cycleLength >= 1) { "cycleLength must be at least 1" }
        require(deloadWeek in 1..cycleLength) { "deloadWeek must be within the cycle" }
        require(deloadVolumeScale > 0.0 && deloadVolumeScale <= 1.0) {
            "deloadVolumeScale must be in (0.0, 1.0]"
        }
        require(deloadIntensityScale > 0.0 && deloadIntensityScale <= 1.0) {
            "deloadIntensityScale must be in (0.0, 1.0]"
        }
    }

    fun isDeload(weekNumber: Int): Boolean = weekNumber == deloadWeek

    /** The week that follows [currentWeek] within the cycle, and whether it starts a new cycle. */
    fun nextWeek(currentWeek: Int): Int = (currentWeek % cycleLength) + 1

    companion object {
        const val DEFAULT_CYCLE_LENGTH: Int = 4
        const val DEFAULT_DELOAD_WEEK: Int = 4
        const val DEFAULT_DELOAD_VOLUME_SCALE: Double = 0.7
        const val DEFAULT_DELOAD_INTENSITY_SCALE: Double = 0.8
    }
}
