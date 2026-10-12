package com.hydrafit.app.core.domain.workout

import kotlinx.coroutines.flow.Flow

/**
 * Read-only, bounded history projection over recorded sessions, sets, session notes and the current
 * exercise catalog. Query details such as literal search, inclusive local-day bounds and keyset
 * paging are validated by the request models in [WorkoutHistory].
 */
interface WorkoutHistoryRepository {
    /** Observes one bounded page of filtered history entries, newest first. */
    fun observeEntries(request: WorkoutHistoryRequest): Flow<WorkoutHistoryPage>

    /**
     * Observes one entry's metadata by explicit identity, or null when nothing matches. Used to
     * refresh a detail header independent of the currently loaded page.
     */
    fun observeEntry(key: WorkoutHistoryKey, utcOffsetMillis: Long): Flow<WorkoutHistoryEntry?>

    /**
     * Observes one bounded page of an entry's recorded sets, newest first. An unassigned-set key has
     * at most one set.
     */
    fun observeEntrySets(
        key: WorkoutHistoryKey,
        request: WorkoutHistorySetRequest
    ): Flow<WorkoutHistorySetPage>
}
