package com.hydrafit.app.core.domain.unit

import kotlin.math.round

/** The unit a user prefers to see and enter weights in. Weights are always stored in kilograms. */
enum class WeightUnit(val label: String) {
    KG("kg"),
    LB("lb");

    fun kilogramsToDisplay(kilograms: Double): Double = when (this) {
        KG -> kilograms
        LB -> kilograms * POUNDS_PER_KILOGRAM
    }

    fun displayToKilograms(value: Double): Double = when (this) {
        KG -> value
        LB -> value / POUNDS_PER_KILOGRAM
    }

    /** Rounds a display value to the unit's natural plate step. */
    fun roundDisplay(value: Double): Double = round(value / step) * step

    private val step: Double
        get() = when (this) {
            KG -> KILOGRAM_STEP
            LB -> POUND_STEP
        }

    companion object {
        const val POUNDS_PER_KILOGRAM: Double = 2.2046226218
        const val KILOGRAM_STEP: Double = 2.5
        const val POUND_STEP: Double = 5.0
    }
}

/** Renders a weight for display: whole numbers lose the decimal, otherwise one decimal place. */
fun formatWeight(value: Double): String {
    val rounded = round(value * 10) / 10
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}
