package com.hydrafit.app.core.userdata.equipment

import com.hydrafit.app.core.domain.equipment.ExerciseExclusion
import kotlinx.coroutines.flow.Flow

/**
 * The user's persistent exercise exclusions (EX-01). Stored separately from the catalog, so editing
 * or resetting an exercise never clears an exclusion. Expired rows are kept, not deleted.
 */
interface ExerciseExclusionRepository {
    fun observe(): Flow<List<ExerciseExclusion>>

    suspend fun exclusion(exerciseId: String): ExerciseExclusion?

    /** Inserts or replaces the exclusion (a fresh window resets any previous expiry). */
    suspend fun set(exclusion: ExerciseExclusion)

    /** Re-enables the exercise by removing its exclusion row entirely. */
    suspend fun clear(exerciseId: String)
}
