package com.hydrafit.app.core.domain.schedule

import com.hydrafit.app.core.domain.engine.AcceptedPlan
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
     * Atomically accepts [acceptedPlan] (when non-null), then writes [activation] with one pending
     * occurrence per workout (dates by queue position from [scheduledEpochDays]) and points the
     * cursor at the first occurrence. When a block is already active it must be replaced
     * ([replaceActive]) or the call is rejected. All writes commit as one transaction. Returns the
     * new activation id.
     */
    suspend fun acceptAndActivate(
        acceptedPlan: AcceptedPlan?,
        activation: TrainingActivation,
        scheduledEpochDays: List<Long?>,
        replaceActive: Boolean
    ): Long

    /**
     * Atomically concludes an occurrence in the active block: verifies it belongs to the active
     * activation and still carries [expectedRevision], writes the remaining-work [entries] and the
     * terminal [status], then advances the cursor to the oldest unresolved occurrence. A stale or
     * foreign occurrence is rejected with [ScheduleException] and nothing is written.
     */
    suspend fun resolveOccurrence(
        occurrenceId: Long,
        expectedRevision: Int,
        status: OccurrenceStatus,
        resolvedAtMillis: Long,
        entries: List<OccurrenceEntry>
    ): WorkoutScheduleState

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
