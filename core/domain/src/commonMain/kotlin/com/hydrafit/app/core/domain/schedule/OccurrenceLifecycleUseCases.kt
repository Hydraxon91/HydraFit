package com.hydrafit.app.core.domain.schedule

import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSet

/** Moves the cursor to an unresolved occurrence of the active block without starting it. */
class SelectWorkoutOccurrenceUseCase(private val scheduleRepository: WorkoutScheduleRepository) {
    suspend operator fun invoke(occurrenceId: Long) {
        val occurrence = requireActive(occurrenceId)
        val state = scheduleRepository.scheduleState()
        scheduleRepository.setScheduleState(state.copy(selectedOccurrenceId = occurrence.id))
    }

    private suspend fun requireActive(occurrenceId: Long): WorkoutOccurrence {
        val occurrence = scheduleRepository.getOccurrence(occurrenceId)
            ?: throw ScheduleException("That workout no longer exists")
        if (occurrence.isResolved) throw ScheduleException("That workout is already finished")
        if (scheduleRepository.scheduleState().activeActivationId != occurrence.activationId) {
            throw ScheduleException("That workout is not in the active block")
        }
        return occurrence
    }
}

/** Marks an occurrence in progress on first work; idempotent once started. */
class StartWorkoutOccurrenceUseCase(
    private val scheduleRepository: WorkoutScheduleRepository,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(occurrenceId: Long): WorkoutOccurrence {
        val occurrence = scheduleRepository.getOccurrence(occurrenceId)
            ?: throw ScheduleException("That workout no longer exists")
        if (occurrence.isResolved) throw ScheduleException("That workout is already finished")
        if (scheduleRepository.scheduleState().activeActivationId != occurrence.activationId) {
            throw ScheduleException("That workout is not in the active block")
        }
        val started = if (occurrence.status == OccurrenceStatus.IN_PROGRESS) {
            occurrence
        } else {
            occurrence.copy(
                status = OccurrenceStatus.IN_PROGRESS,
                startedAtMillis = timeProvider.nowMillis(),
                revision = occurrence.revision + 1
            ).also { scheduleRepository.updateOccurrence(it) }
        }
        val state = scheduleRepository.scheduleState()
        scheduleRepository.setScheduleState(state.copy(selectedOccurrenceId = occurrenceId))
        return started
    }
}

/** Completion bookkeeping for finishing and skipping. */
private class CompletionContext(
    val occurrence: WorkoutOccurrence,
    val performedWorkingSetsByEntry: Map<Long, Int>,
    /** Any recorded set attributed to this occurrence, warm-up or not, entry-linked or not. */
    val recordedSetCount: Int
) {
    val totalPerformed: Int get() = performedWorkingSetsByEntry.values.sum()

    fun isFullyRecorded(): Boolean =
        occurrence.entries.all { (performedWorkingSetsByEntry[it.id] ?: 0) >= it.sets }
}

private suspend fun loadCompletion(
    scheduleRepository: WorkoutScheduleRepository,
    workoutLogRepository: WorkoutLogRepository,
    occurrenceId: Long
): CompletionContext {
    val occurrence = scheduleRepository.getOccurrence(occurrenceId)
        ?: throw ScheduleException("That workout no longer exists")
    val recorded = workoutLogRepository.setsForOccurrence(occurrenceId)
    val performed = recorded
        .filterNot(WorkoutSet::isWarmup)
        // A partial cluster is recorded work that does not satisfy its prescribed position; the user
        // resumes it or concludes the workout partially.
        .filterNot { it.cluster?.isComplete == false }
        .groupingBy { it.occurrenceEntryId }
        .eachCount()
        .filterKeys { it != null }
        .mapKeys { requireNotNull(it.key) }
    return CompletionContext(occurrence, performed, recorded.size)
}

/**
 * Concludes a workout. [FinishMode.FULL] requires every prescribed set to be recorded; partial and
 * skip-remaining conclude early, preserving the work done and recording what was left unperformed.
 * A workout with no logged work must be skipped, not finished. Resolving is atomic and idempotent;
 * a stale or foreign occurrence is rejected.
 */
class FinishWorkoutOccurrenceUseCase(
    private val scheduleRepository: WorkoutScheduleRepository,
    private val workoutLogRepository: WorkoutLogRepository,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(occurrenceId: Long, mode: FinishMode): WorkoutScheduleState {
        val context = loadCompletion(scheduleRepository, workoutLogRepository, occurrenceId)
        val occurrence = context.occurrence
        if (occurrence.isResolved) return scheduleRepository.scheduleState()
        if (context.totalPerformed == 0) {
            throw ScheduleException("Nothing logged yet; skip this workout instead")
        }
        if (mode == FinishMode.FULL && !context.isFullyRecorded()) {
            throw ScheduleException("Not every prescribed set is recorded yet")
        }

        val entries = if (mode == FinishMode.FULL) {
            occurrence.entries
        } else {
            val disposition = if (mode == FinishMode.PARTIAL) {
                RemainingDisposition.OMITTED
            } else {
                RemainingDisposition.SKIPPED
            }
            occurrence.entries.map { entry ->
                val remaining = (entry.sets - (context.performedWorkingSetsByEntry[entry.id] ?: 0))
                    .coerceAtLeast(0)
                entry.copy(
                    remainingDisposition = if (remaining > 0) disposition else null,
                    terminalRemainingSets = remaining.takeIf { it > 0 }
                )
            }
        }
        val status = if (mode == FinishMode.FULL) {
            OccurrenceStatus.FINISHED
        } else {
            OccurrenceStatus.FINISHED_PARTIAL
        }
        return scheduleRepository.resolveOccurrence(
            occurrenceId = occurrenceId,
            expectedRevision = occurrence.revision,
            status = status,
            resolvedAtMillis = timeProvider.nowMillis(),
            entries = entries
        )
    }
}

/**
 * Skips a workout. An untouched workout is marked [OccurrenceStatus.SKIPPED] and closes no session;
 * a workout with any recorded work - prescribed working sets, warm-ups only, or work whose entry
 * link was lost - is concluded partially with the unfinished prescribed sets marked skipped. The
 * working-set bookkeeping still counts only prescribed, entry-attributed sets.
 */
class SkipWorkoutOccurrenceUseCase(
    private val scheduleRepository: WorkoutScheduleRepository,
    private val workoutLogRepository: WorkoutLogRepository,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(occurrenceId: Long): WorkoutScheduleState {
        val context = loadCompletion(scheduleRepository, workoutLogRepository, occurrenceId)
        val occurrence = context.occurrence
        if (occurrence.isResolved) return scheduleRepository.scheduleState()

        val hasRecordedWork = context.recordedSetCount > 0
        val entries = if (hasRecordedWork) {
            occurrence.entries.map { entry ->
                val remaining = (entry.sets - (context.performedWorkingSetsByEntry[entry.id] ?: 0))
                    .coerceAtLeast(0)
                entry.copy(
                    remainingDisposition = if (remaining >
                        0
                    ) {
                        RemainingDisposition.SKIPPED
                    } else {
                        null
                    },
                    terminalRemainingSets = remaining.takeIf { it > 0 }
                )
            }
        } else {
            occurrence.entries
        }
        val status = if (hasRecordedWork) {
            OccurrenceStatus.FINISHED_PARTIAL
        } else {
            OccurrenceStatus.SKIPPED
        }
        return scheduleRepository.resolveOccurrence(
            occurrenceId = occurrenceId,
            expectedRevision = occurrence.revision,
            status = status,
            resolvedAtMillis = timeProvider.nowMillis(),
            entries = entries
        )
    }
}
