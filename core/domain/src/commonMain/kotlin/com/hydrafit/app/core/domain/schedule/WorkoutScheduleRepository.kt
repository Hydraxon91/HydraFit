package com.hydrafit.app.core.domain.schedule

import kotlinx.coroutines.flow.Flow

/** Persists frozen activations, their occurrences and the single scheduling cursor. */
interface WorkoutScheduleRepository {
    fun observeScheduleState(): Flow<WorkoutScheduleState>

    fun observeActiveActivation(): Flow<TrainingActivation?>

    fun observeOccurrences(activationId: Long): Flow<List<WorkoutOccurrence>>

    suspend fun scheduleState(): WorkoutScheduleState

    suspend fun setScheduleState(state: WorkoutScheduleState)

    suspend fun activeActivation(): TrainingActivation?

    suspend fun getActivation(id: Long): TrainingActivation?

    suspend fun occurrences(activationId: Long): List<WorkoutOccurrence>

    suspend fun getOccurrence(id: Long): WorkoutOccurrence?

    /**
     * Writes a new activation with its frozen workouts/entries in one transaction; returns the
     * activation id so the caller can reload the assigned child ids before generating occurrences.
     */
    suspend fun insertActivation(activation: TrainingActivation): Long

    /** Writes generated occurrences (each with its working-copy entries); returns them with ids. */
    suspend fun insertOccurrences(occurrences: List<WorkoutOccurrence>): List<WorkoutOccurrence>

    /** Updates an activation's mutable header (name, mode, weekdays, status, revision, end time). */
    suspend fun updateActivationHeader(activation: TrainingActivation)

    /** Updates an occurrence's mutable header (schedule, queue order, status, times, revision). */
    suspend fun updateOccurrence(occurrence: WorkoutOccurrence)

    /**
     * Upserts an occurrence's working-copy entries by id (id == 0 inserts), preserving the ids of
     * kept rows so logged sets keep their attribution, and deletes rows no longer present.
     */
    suspend fun replaceOccurrenceEntries(occurrenceId: Long, entries: List<OccurrenceEntry>)

    suspend fun deleteOccurrencesForActivation(activationId: Long)

    /** True when any activation references this routine template; guards hard deletion. */
    suspend fun isTemplateReferenced(templateId: Long): Boolean
}
