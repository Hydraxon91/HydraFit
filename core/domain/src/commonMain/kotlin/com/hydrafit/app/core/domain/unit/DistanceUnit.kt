package com.hydrafit.app.core.domain.unit

import kotlin.math.round

/** Display unit for distance. Distances are always stored as whole metres. */
enum class DistanceUnit(val label: String) {
    METERS("m"),
    FEET("ft");

    fun metresToDisplay(meters: Int): Double = when (this) {
        METERS -> meters.toDouble()
        FEET -> meters * FEET_PER_METRE
    }

    fun displayToMetres(value: Double): Int = when (this) {
        METERS -> round(value).toInt()
        FEET -> round(value / FEET_PER_METRE).toInt()
    }

    /** The whole display number for [meters], used to seed an entry field without inventing decimals. */
    fun displayWhole(meters: Int): Int = round(metresToDisplay(meters)).toInt()

    companion object {
        const val FEET_PER_METRE: Double = 3.280839895
    }
}

/** Renders a stored whole-metre distance in the selected unit, without inventing precision. */
fun formatDistance(meters: Int, unit: DistanceUnit): String = formatDistance(meters.toLong(), unit)

/** The same rendering for a summed whole-metre total, which may exceed a single set's range. */
fun formatDistance(meters: Long, unit: DistanceUnit): String = when (unit) {
    DistanceUnit.METERS -> "$meters m"
    DistanceUnit.FEET -> "${round(meters * DistanceUnit.FEET_PER_METRE).toLong()} ft"
}
