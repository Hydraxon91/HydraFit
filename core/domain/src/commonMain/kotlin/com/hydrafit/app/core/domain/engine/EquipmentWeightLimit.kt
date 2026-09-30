package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise

/**
 * Clamps a suggested working weight to the heaviest weight the exercise's equipment can provide.
 * A machine or stack with a fixed maximum must never be prescribed a heavier load; equipment with
 * no recorded maximum (plate-loaded, bodyweight) imposes no ceiling.
 */
object EquipmentWeightLimit {

    /** The lowest maximum among the equipment the exercise requires, or null when unbounded. */
    fun ceilingFor(exercise: Exercise, maxWeights: Map<EquipmentTag, Double>): Double? =
        exercise.requiredEquipment.mapNotNull { maxWeights[it] }.minOrNull()

    fun clamp(weightKg: Double, ceilingKg: Double?): Double =
        if (ceilingKg == null) weightKg else minOf(weightKg, ceilingKg)
}
