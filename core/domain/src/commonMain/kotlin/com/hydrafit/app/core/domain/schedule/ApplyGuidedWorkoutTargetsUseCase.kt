package com.hydrafit.app.core.domain.schedule

import com.hydrafit.app.core.domain.workout.WorkoutLogRepository

/** Applies only explicit editor changes to the remaining sets of the active guided occurrence. */
class ApplyGuidedWorkoutTargetsUseCase(
    private val schedule: WorkoutScheduleRepository,
    private val log: WorkoutLogRepository,
    private val targets: GuidedWorkoutTargetsRepository
) {
    suspend operator fun invoke(
        occurrenceId: Long,
        entryId: Long,
        resetRemaining: Boolean,
        patches: Map<Int, GuidedTargetPatch> = emptyMap()
    ) {
        val occurrence = schedule.getOccurrence(occurrenceId)
            ?: throw ScheduleException("That workout no longer exists")
        val state = schedule.scheduleState()
        if (occurrence.isResolved ||
            state.legacyFallbackEnabled ||
            state.activeActivationId != occurrence.activationId ||
            state.selectedOccurrenceId != occurrenceId
        ) {
            throw ScheduleException("That workout is not the active guided occurrence")
        }
        val entry = occurrence.entries.firstOrNull { it.id == entryId }
            ?: throw ScheduleException("That occurrence entry no longer exists")
        require(
            entry.guidedTargets == null ||
                entry.guidedTargets.overrides.all { override ->
                    override.setIndex in 0 until entry.sets
                }
        )
        val recorded = log.setsForOccurrence(occurrenceId)
            .filter { !it.isWarmup && it.occurrenceEntryId == entryId }
        val partial = recorded.any { it.cluster?.isComplete == false }
        if (partial) throw ScheduleException("Finish or resolve the partial cluster first")
        val completed = recorded.size
        targets.applyTargets(
            GuidedWorkoutTargetsRequest(
                occurrenceId = occurrenceId,
                occurrenceRevision = occurrence.revision,
                entryId = entryId,
                exerciseId = entry.exerciseId,
                entrySets = entry.sets,
                completedSetCount = completed,
                resetRemaining = resetRemaining,
                patches = patches
            )
        )
    }
}
