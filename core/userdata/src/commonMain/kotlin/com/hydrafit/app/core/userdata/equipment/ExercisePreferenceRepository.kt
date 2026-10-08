package com.hydrafit.app.core.userdata.equipment

import com.hydrafit.app.core.domain.equipment.ExercisePreference
import kotlinx.coroutines.flow.Flow

/**
 * The user's explicit per-exercise preference. It is stored separately from the catalog
 * `ExerciseOverrideRepository`, so resetting an exercise's catalog edits never clears a preference.
 * A missing exercise id means [ExercisePreference.NEUTRAL].
 */
interface ExercisePreferenceRepository {
    /** All explicitly stored preferences, keyed by exercise id; absent ids are neutral. */
    fun observe(): Flow<Map<String, ExercisePreference>>

    suspend fun preference(exerciseId: String): ExercisePreference

    /** Stores an explicit preference; [ExercisePreference.NEUTRAL] is stored explicitly. */
    suspend fun set(exerciseId: String, preference: ExercisePreference)
}
