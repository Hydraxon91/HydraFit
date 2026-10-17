package com.hydrafit.app.core.domain.workout

import kotlinx.coroutines.flow.Flow

/**
 * Persisted preparation overrides for the warm-up guidance.
 *
 * There is one override per [WarmupAction], and it is global rather than scoped to an occurrence or
 * workout: a measure the user edits into a preparation row is what that action means from then on, so
 * a later occurrence renders the persisted measure instead of the built-in default. An action with no
 * stored override falls back to its built-in default, which is what "Reset to default" restores by
 * clearing the row.
 *
 * A stored measure keeps the same invariant as every other prescription slot: a positive rep count or
 * positive elapsed whole seconds, exactly one of them, never both and never a zero sentinel.
 */
interface WarmupPreparationOverrideRepository {
    /** Every stored override keyed by its action; emits again after each write. */
    fun observeAll(): Flow<Map<WarmupAction, WarmupPreparationItem>>

    /** Stores [item] as the override for its own action, replacing any existing one. */
    suspend fun set(item: WarmupPreparationItem)

    /** Removes the override for [action], so its built-in default applies again; a no-op if absent. */
    suspend fun clear(action: WarmupAction)
}
