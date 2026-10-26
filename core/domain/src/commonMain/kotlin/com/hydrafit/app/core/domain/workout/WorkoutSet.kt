package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.equipment.StationAttribution

data class WorkoutSet(
    val id: Long = 0,
    val exerciseId: String,
    /**
     * Repetitions for counted work, or null for a timed set. A timed set stores null here rather
     * than `0`, so an absent number is never read as a rep count.
     */
    val reps: Int? = null,
    val weightKg: Double?,
    /** What the recorded [weightKg] means (external, added, bodyweight, or legacy). */
    val loadKind: LoadKind = LoadKind.EXTERNAL,
    val performedAtMillis: Long,
    val isWarmup: Boolean = false,
    /** The accepted plan's week/cycle and day at log time, when a plan was active. */
    val weekNumber: Int? = null,
    val cycleNumber: Int? = null,
    val dayIndex: Int? = null,
    /** Optional reps in reserve (0..10); null when the user did not record effort. */
    val rir: Int? = null,
    /** The explicit workout session this set belongs to; null for legacy/unsegmented rows. */
    val sessionId: String? = null,
    /** The scheduled occurrence this set fulfils, when it was logged against one. */
    val occurrenceId: Long? = null,
    /** The occurrence's prescription slot this set fulfils, when it was logged against one. */
    val occurrenceEntryId: Long? = null,
    /** Timing source; legacy and manually backdated rows remain unknown unless explicitly classified. */
    val timingProvenance: WorkoutTimingProvenance = WorkoutTimingProvenance.UNKNOWN,
    /** Monotonic start/completion instants for an explicitly started live set; null otherwise. */
    val startedAtElapsedMillis: Long? = null,
    val completedAtElapsedMillis: Long? = null,
    /**
     * Elapsed whole seconds for a timed set, or null for counted work. The stored duration is the
     * value the user entered, never derived from [startedAtElapsedMillis]/[completedAtElapsedMillis]:
     * monotonic timing is provenance, not a duration source.
     */
    val durationSeconds: Int? = null,
    /** Immutable prescribed target, not the actual performance or a reference to today's plan. */
    val prescriptionSnapshot: WorkoutPrescriptionSnapshot? = null,
    /**
     * The physical station this set was performed on, frozen at logging, or null for legacy and
     * unattributed work. A station identity is independent of the mutable station row.
     */
    val station: StationAttribution? = null,
    /** Whole metres for a distance set, or null for reps and timed work. */
    val distanceMeters: Int? = null
) {
    /**
     * The set's measurement when its stored shape is valid, or null when the row is neither a
     * positive rep count nor a positive duration (including the both-present and empty shapes that
     * [requireValidSetMeasurement] rejects at the write boundary).
     */
    val measurement: SetMeasurement?
        get() = when {
            distanceMeters != null &&
                distanceMeters > 0 &&
                reps == null &&
                durationSeconds == null ->
                SetMeasurement.Distance(distanceMeters)

            durationSeconds != null &&
                durationSeconds > 0 &&
                reps == null &&
                distanceMeters == null ->
                SetMeasurement.Duration(durationSeconds)

            durationSeconds == null &&
                distanceMeters == null &&
                reps != null &&
                reps > 0 ->
                SetMeasurement.Repetitions(reps)

            else -> null
        }
}

/**
 * Requires exactly one positive measurement: a positive [reps] count or a positive
 * [durationSeconds], never both, never neither, and never a non-positive value.
 *
 * Rejecting the empty shape is what keeps `0` from meaning "not applicable": the unused metric
 * stays null rather than carrying a sentinel.
 */
fun requireValidSetMeasurement(reps: Int?, durationSeconds: Int?) {
    requireValidSetMeasurement(reps, durationSeconds, null)
}

fun requireValidSetMeasurement(reps: Int?, durationSeconds: Int?, distanceMeters: Int?) {
    val hasReps = reps != null && reps > 0
    val hasDuration = durationSeconds != null && durationSeconds > 0
    val hasDistance = distanceMeters != null && distanceMeters > 0
    require(listOf(hasReps, hasDuration, hasDistance).count { it } == 1) {
        "A set is measured by positive repetitions, positive whole seconds or positive metres, " +
            "exactly one of them"
    }
    require(reps == null || reps > 0) { "Reps must be positive when present" }
    require(durationSeconds == null || durationSeconds > 0) {
        "Duration must be positive whole seconds when present"
    }
    require(distanceMeters == null || distanceMeters > 0) {
        "Distance must be positive whole metres when present"
    }
}
