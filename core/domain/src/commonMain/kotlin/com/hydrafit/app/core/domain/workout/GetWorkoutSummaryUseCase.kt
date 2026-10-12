package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleRepository

/**
 * Reads one workout occurrence into its post-finish summary: the frozen prescription, the recorded
 * sets, the sessions they span with their stored notes, and the current catalog names for recorded
 * sets that belong to no slot.
 *
 * Read-only: it writes nothing and changes no prescription, accepted plan or occurrence. Returns
 * null when [occurrenceId] no longer exists, so a finish that raced a deletion shows no summary
 * rather than inventing one.
 */
class GetWorkoutSummaryUseCase(
    private val scheduleRepository: WorkoutScheduleRepository,
    private val workoutLogRepository: WorkoutLogRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val sessionNoteRepository: SessionNoteRepository,
    private val exerciseCatalog: ExerciseCatalog
) {
    suspend operator fun invoke(occurrenceId: Long): WorkoutSummary? {
        val occurrence = scheduleRepository.getOccurrence(occurrenceId) ?: return null
        val sets = workoutLogRepository.setsForOccurrence(occurrenceId)
        val sessions = sets.mapNotNull { it.sessionId }.distinct()
            .mapNotNull { sessionRepository.byId(it) }
        val slots = occurrence.entries.mapTo(mutableSetOf()) { it.id }
        return buildWorkoutSummary(
            occurrence = occurrence,
            sets = sets,
            sessions = sessions,
            sessionNotes = sessions.associate { it.id to sessionNoteRepository.note(it.id) },
            activation = scheduleRepository.getActivation(occurrence.activationId),
            // Current names are only needed for work that belongs to no prescribed slot, so an
            // attributed workout skips the catalog read entirely.
            exerciseNames = if (sets.any { it.isAdditionalTo(slots) }) {
                exerciseCatalog.all().associate { it.id to it.name }
            } else {
                emptyMap()
            }
        )
    }
}
