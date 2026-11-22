package com.hydrafit.app.core.userdata.settings

import com.hydrafit.app.core.domain.engine.AutomaticSetPrescription

/**
 * The plan parameters the user chose in the split builder, kept between launches. A null value
 * means "not chosen", so the planner keeps using the training goal's default for it.
 */
data class PlanParameterPreferences(
    val setsPerExercise: Int? = null,
    val accessorySetsPerExercise: Int? = null,
    val automaticSetPrescription: AutomaticSetPrescription? = null
)

/** Stores [PlanParameterPreferences]. Not part of backups; a restore leaves them unchanged. */
interface PlanParameterPreferenceRepository {
    suspend fun current(): PlanParameterPreferences

    suspend fun setSetsPerExercise(sets: Int?)

    suspend fun setAccessorySetsPerExercise(sets: Int?)

    suspend fun setAutomaticSetPrescription(prescription: AutomaticSetPrescription?)
}
