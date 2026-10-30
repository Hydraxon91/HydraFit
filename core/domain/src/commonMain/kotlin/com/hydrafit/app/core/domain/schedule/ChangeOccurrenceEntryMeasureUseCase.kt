package com.hydrafit.app.core.domain.schedule

import com.hydrafit.app.core.domain.engine.SetTarget
import com.hydrafit.app.core.domain.workout.WorkoutLoadPolicy
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository

/** The measurement a user can choose for one unstarted occurrence slot. */
enum class OccurrenceEntryMeasure { REPETITIONS, SECONDS, DISTANCE_METERS, FORCE_NEWTONS }

/**
 * Corrects one unstarted occurrence slot's measurement without touching the routine, the frozen
 * activation, future workouts or recorded history. The user picks the metric and enters the value;
 * a stored number is never converted into another measure. The slot must belong to the selected,
 * unresolved occurrence of the active guided block and carry no recorded set (warm-ups included),
 * ordered per-set targets or cluster, whose structure this slice cannot preserve.
 */
class ChangeOccurrenceEntryMeasureUseCase(
    private val schedule: WorkoutScheduleRepository,
    private val log: WorkoutLogRepository,
    private val targets: GuidedWorkoutTargetsRepository
) {
    suspend operator fun invoke(
        occurrenceId: Long,
        entryId: Long,
        measure: OccurrenceEntryMeasure,
        value: Double
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
        if (occurrence.status == OccurrenceStatus.IN_PROGRESS) {
            throw ScheduleException("This workout has already started")
        }
        val entry = occurrence.entries.firstOrNull { it.id == entryId }
            ?: throw ScheduleException("That occurrence entry no longer exists")
        if (entry.setTargets != null) {
            throw ScheduleException(
                "This slot has ordered per-set targets, so its measurement stays as prescribed"
            )
        }
        if (entry.cluster != null) {
            throw ScheduleException(
                "This slot's cluster shape cannot be kept with another measurement"
            )
        }
        if (log.setsForOccurrence(occurrenceId).isNotEmpty()) {
            throw ScheduleException("This workout has recorded sets")
        }
        targets.changeEntryMeasure(
            OccurrenceEntryMeasureRequest(
                occurrenceId = occurrenceId,
                occurrenceRevision = occurrence.revision,
                entryId = entryId,
                target = entry.measureTarget(measure, value)
            )
        )
    }
}

/**
 * The new prescription for [entry] when [measure] is chosen with [value], applying the settled load
 * rules: seconds and force are load-free, while reps and distance keep the existing load only when
 * the frozen capability permits it. [SetTarget.requireValid] and [WorkoutLoadPolicy] validate the
 * shape and bounds, so the same rule protects the use case and the write boundary.
 */
fun OccurrenceEntry.measureTarget(measure: OccurrenceEntryMeasure, value: Double): SetTarget {
    require(value.isFinite() && value > 0.0) { "Enter a positive value" }
    val keepsLoad = measure == OccurrenceEntryMeasure.REPETITIONS ||
        measure == OccurrenceEntryMeasure.DISTANCE_METERS
    val loadPermitted = keepsLoad &&
        runCatching {
            WorkoutLoadPolicy.validate(loadCapability, loadKind, weightKg)
        }.isSuccess
    val nextLoadKind = if (loadPermitted) {
        loadKind
    } else {
        WorkoutLoadPolicy.defaultKind(
            loadCapability
        )
    }
    val nextWeightKg = if (loadPermitted) weightKg else null
    val target = when (measure) {
        OccurrenceEntryMeasure.REPETITIONS -> SetTarget(
            reps = value.wholeValue("Reps"),
            loadKind = nextLoadKind,
            weightKg = nextWeightKg
        )

        OccurrenceEntryMeasure.SECONDS -> SetTarget(
            durationSeconds = value.wholeValue("Seconds"),
            loadKind = nextLoadKind,
            weightKg = nextWeightKg
        )

        OccurrenceEntryMeasure.DISTANCE_METERS -> SetTarget(
            distanceMeters = value.wholeValue("Distance"),
            loadKind = nextLoadKind,
            weightKg = nextWeightKg
        )

        OccurrenceEntryMeasure.FORCE_NEWTONS -> SetTarget(
            forceNewtons = value,
            loadKind = nextLoadKind,
            weightKg = nextWeightKg
        )
    }
    target.requireValid()
    WorkoutLoadPolicy.validate(loadCapability, target.loadKind, target.weightKg)
    return target
}

private fun Double.wholeValue(label: String): Int {
    require(this <= Int.MAX_VALUE.toDouble()) { "$label is too large" }
    val whole = toInt()
    require(whole.toDouble() == this) { "$label must be a whole number" }
    return whole
}
