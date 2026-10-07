package com.hydrafit.app.core.domain.routine

import kotlinx.coroutines.flow.Flow

/** Stores editable routine templates. Activations snapshot a template, so this owns drafts only. */
interface RoutineTemplateRepository {
    /** Every template, most recently updated first, archived entries included. */
    fun observeAll(): Flow<List<RoutineTemplate>>

    suspend fun get(id: Long): RoutineTemplate?

    /** Inserts ([RoutineTemplate.id] == 0) or updates a template; returns the persisted id. */
    suspend fun save(template: RoutineTemplate): Long

    suspend fun setArchived(id: Long, archivedAtMillis: Long?)

    /** Hard-deletes a template with its workouts and entries. */
    suspend fun delete(id: Long)
}
