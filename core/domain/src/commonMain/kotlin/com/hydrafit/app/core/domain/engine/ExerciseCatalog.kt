package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.CatalogExerciseProfile
import com.hydrafit.app.core.domain.equipment.Exercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface ExerciseCatalog {
    suspend fun all(): List<Exercise>

    /**
     * Seeded identities with canonical matching labels and their effective, user-overridden profiles.
     * Implementations without this read seam return no candidates rather than treating an effective
     * display name as a canonical identity. Reading candidates never writes or merges exercises.
     */
    suspend fun profileCandidates(): List<CatalogExerciseProfile> = emptyList()

    /**
     * Observes the catalog so consumers react to edits (custom exercises, overrides). The
     * database-backed catalog overrides this with a live query; this default emits once, which is
     * enough for the simple/test implementations.
     */
    fun observeAll(): Flow<List<Exercise>> = flow { emit(all()) }
}
