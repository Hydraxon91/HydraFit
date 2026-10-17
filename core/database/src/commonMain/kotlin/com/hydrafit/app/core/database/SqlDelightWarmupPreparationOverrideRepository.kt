package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hydrafit.app.core.domain.workout.WarmupAction
import com.hydrafit.app.core.domain.workout.WarmupPreparationItem
import com.hydrafit.app.core.domain.workout.WarmupPreparationOverrideRepository
import com.hydrafit.app.core.domain.workout.requireValidSetMeasurement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists the user's warm-up preparation overrides in the additive `warmupPreparationOverride`
 * table. The store holds one row per action, keyed by the action's stable name, so an edit survives
 * occurrences and app restarts.
 */
class SqlDelightWarmupPreparationOverrideRepository(database: HydraFitDatabase) :
    WarmupPreparationOverrideRepository {
    private val queries = database.warmupPreparationOverrideQueries

    override fun observeAll(): Flow<Map<WarmupAction, WarmupPreparationItem>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.Default).map { rows ->
            rows.mapNotNull { it.toOverrideOrNull() }.associateBy { it.action }
        }

    override suspend fun set(item: WarmupPreparationItem) {
        requireValidSetMeasurement(item.reps, item.durationSeconds)
        queries.upsert(
            action = item.action.name,
            reps = item.reps?.toLong(),
            durationSeconds = item.durationSeconds?.toLong()
        )
    }

    override suspend fun clear(action: WarmupAction) {
        queries.deleteByAction(action.name)
    }

    /**
     * A row whose action name is unknown to this build, or whose measure is not exactly one positive
     * value, has no valid interpretation and is skipped rather than guessed at. The write path
     * enforces both invariants, so this only guards a hand-edited or externally corrupted row.
     */
    private fun WarmupPreparationOverride.toOverrideOrNull(): WarmupPreparationItem? {
        val action = WarmupAction.entries.firstOrNull { it.name == this.action } ?: return null
        val hasReps = reps != null && reps > 0
        val hasDuration = durationSeconds != null && durationSeconds > 0
        if (hasReps == hasDuration) return null
        return WarmupPreparationItem(
            action = action,
            reps = reps?.toInt(),
            durationSeconds = durationSeconds?.toInt()
        )
    }
}
