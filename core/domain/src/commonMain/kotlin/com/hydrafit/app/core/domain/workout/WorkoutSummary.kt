package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.schedule.OccurrenceStatus
import com.hydrafit.app.core.domain.schedule.RemainingDisposition
import com.hydrafit.app.core.domain.schedule.TrainingActivation
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence

/**
 * A workout reconciled against its frozen prescription for the post-finish summary: prescribed
 * versus recorded work per slot, additional and warm-up sets, the sessions it spans with their
 * stored notes, and the recorded start/finish instants.
 *
 * A pure projection: it reads no repository, changes nothing, and derives remaining work from the
 * recorded sets so the summary reconciles with what is stored. The stored [RemainingDisposition]
 * only labels why work was left undone and is meaningful while a slot still has remaining sets.
 */
data class WorkoutSummary(
    val occurrenceId: Long,
    /** The workout name frozen into the activation, or null when the activation is unavailable. */
    val workoutName: String?,
    val status: OccurrenceStatus,
    /** When the occurrence's first work was recorded, or null while it was never started. */
    val startedAtMillis: Long?,
    /** When the occurrence was concluded, or null while it is unresolved. */
    val resolvedAtMillis: Long?,
    /** Prescribed slots in their frozen order, each with its recorded work. */
    val exercises: List<WorkoutSummaryExercise>,
    /** Recorded working sets that belong to no prescribed slot, oldest first. */
    val extraSets: List<WorkoutSummarySet>,
    /** Recorded warm-up sets, oldest first; they never satisfy a prescription. */
    val warmupSets: List<WorkoutSummarySet>,
    /** The explicit sessions the workout spans, oldest first. */
    val sessions: List<WorkoutSummarySession>
) {
    val prescribedSets: Int get() = exercises.sumOf { it.prescribedSets }

    /** Recorded working sets, including additional work and sets beyond a slot's target. */
    val recordedWorkingSets: Int get() = exercises.sumOf { it.recordedSets } + extraSets.size

    /** Prescribed sets still unrecorded, recomputed from the stored work. */
    val remainingSets: Int get() = exercises.sumOf { it.remainingSets }

    val isComplete: Boolean get() = remainingSets == 0
}

/**
 * One prescribed exercise of a summarized workout with its frozen target and recorded sets.
 * [performedSets] holds every working set attributed to the slot, including sets beyond the
 * prescription, so no recorded set is ever hidden; see [setsBeyondTarget].
 */
data class WorkoutSummaryExercise(
    val occurrenceEntryId: Long,
    val exerciseId: String,
    /** The exercise name frozen into the occurrence entry at activation. */
    val exerciseName: String,
    val prescribedSets: Int,
    /** Repetitions for counted work, or null for a timed slot. Never a `0` sentinel. */
    val prescribedReps: Int? = null,
    /** Elapsed whole seconds for a timed slot, or null for counted work. */
    val prescribedDurationSeconds: Int? = null,
    /** Whole metres for a distance slot, or null for reps and timed work. */
    val prescribedDistanceMeters: Int? = null,
    /** Measured force in newtons for a force slot, or null for every other metric. */
    val prescribedForceNewtons: Double? = null,
    /** The frozen target load; null means the prescription carried no numeric load. */
    val prescribedWeightKg: Double?,
    /** What the frozen target load means. */
    val prescribedLoadKind: LoadKind,
    /** Recorded working sets attributed to this slot, oldest first. */
    val performedSets: List<WorkoutSummarySet>,
    /** Prescribed sets still unrecorded, recomputed from [performedSets] and never negative. */
    val remainingSets: Int,
    /** Why work was left undone at conclusion; only meaningful while [remainingSets] is positive. */
    val remainingDisposition: RemainingDisposition?,
    /**
     * True only when the slot's exercise differs from the activation entry it was frozen from,
     * which is how an occurrence-only replacement is proven. A slot without that provenance makes
     * no replaced claim.
     */
    val isReplaced: Boolean
) {
    val recordedSets: Int get() = performedSets.size

    /** Recorded working sets beyond the prescription. */
    val setsBeyondTarget: Int get() = (performedSets.size - prescribedSets).coerceAtLeast(0)

    val isComplete: Boolean get() = remainingSets == 0
}

/**
 * One recorded set as the summary shows it, preserving the stored load, RIR and timing meaning.
 * [exerciseName] is the name frozen into the set's prescription slot, or the current catalog name
 * for a set that belongs to no slot; null means the exercise no longer resolves.
 */
data class WorkoutSummarySet(
    val id: Long,
    val exerciseId: String,
    val exerciseName: String?,
    val reps: Int?,
    val weightKg: Double?,
    val loadKind: LoadKind,
    val rir: Int?,
    val performedAtMillis: Long,
    val timingProvenance: WorkoutTimingProvenance,
    val isWarmup: Boolean,
    /** Elapsed whole seconds for a timed set, or null for counted work. */
    val durationSeconds: Int? = null,
    /** Whole metres for a distance set, or null for reps and timed work. */
    val distanceMeters: Int? = null,
    /** Measured force in newtons for a force set, or null for every other metric. */
    val forceNewtons: Double? = null,
    /** The frozen measuring device identity and rating, or null when none was recorded. */
    val device: DeviceAttribution? = null,
    /** The recorded session this set belongs to; null for legacy or unsegmented rows. */
    val sessionId: String?
)

/** One explicit session a workout spans, with its recorded bounds and stored note. */
data class WorkoutSummarySession(
    val sessionId: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    /** Stored note encoding: null = never set, "" = explicit clear, otherwise the recorded text. */
    val note: String?
) {
    /** True only for recorded text; an explicit clear is a stored value but shows no note. */
    val hasNote: Boolean get() = !note.isNullOrEmpty()
}

/**
 * True when this recorded set's name cannot come from the occurrence's frozen entries, so the
 * projection has to look it up in the current catalog: the set is linked to no prescribed [slots]
 * entry, or to one this occurrence no longer has. Warm-ups are included, because they are named the
 * same way.
 */
internal fun WorkoutSet.needsCatalogName(slots: Set<Long>): Boolean =
    occurrenceEntryId?.let { it in slots } != true

/**
 * True when this recorded set becomes additional work: a working set that no prescribed [slots]
 * entry owns.
 */
internal fun WorkoutSet.isAdditionalTo(slots: Set<Long>): Boolean =
    !isWarmup && needsCatalogName(slots)

/**
 * Builds the post-finish summary of [occurrence] from its recorded [sets], the [sessions] it spans
 * with their stored [sessionNotes], and the [activation] its prescription was frozen from.
 *
 * A working set satisfies a slot only through [WorkoutSet.occurrenceEntryId]; warm-ups and
 * unattributed sets never do. A set with no slot, or with a slot id that no longer exists, becomes
 * additional work. [exerciseNames] supplies current catalog names for sets without a slot; a
 * prescribed slot keeps the name frozen into it. Pure: it reads nothing and mutates nothing.
 */
fun buildWorkoutSummary(
    occurrence: WorkoutOccurrence,
    sets: List<WorkoutSet>,
    sessions: List<WorkoutSession>,
    sessionNotes: Map<String, String?>,
    activation: TrainingActivation?,
    exerciseNames: Map<String, String> = emptyMap()
): WorkoutSummary {
    val entries = occurrence.entries.sortedBy { it.position }
    val entriesById = entries.associateBy { it.id }
    val slotIds = entriesById.keys
    val ordered = sets.sortedWith(compareBy({ it.performedAtMillis }, { it.id }))
    val workingSets = ordered.filterNot { it.isWarmup }
    val warmupSets = ordered.filter { it.isWarmup }
    val workingByEntry = workingSets
        .filter { it.occurrenceEntryId != null }
        // A partial cluster stays visible in history but never satisfies a prescribed position.
        .filterNot { it.cluster?.isComplete == false }
        .groupBy { requireNotNull(it.occurrenceEntryId) }
    val sourceExerciseByEntry = activation?.workouts.orEmpty()
        .flatMap { it.entries }
        .associate { it.id to it.exerciseId }

    fun nameOf(set: WorkoutSet): String? = set.occurrenceEntryId
        ?.let(entriesById::get)
        ?.exerciseName
        ?: exerciseNames[set.exerciseId]

    fun toSummarySet(set: WorkoutSet): WorkoutSummarySet = WorkoutSummarySet(
        id = set.id,
        exerciseId = set.exerciseId,
        exerciseName = nameOf(set),
        reps = set.reps,
        weightKg = set.weightKg,
        loadKind = set.loadKind,
        rir = set.rir,
        performedAtMillis = set.performedAtMillis,
        timingProvenance = set.timingProvenance,
        isWarmup = set.isWarmup,
        durationSeconds = set.durationSeconds,
        distanceMeters = set.distanceMeters,
        forceNewtons = set.forceNewtons,
        device = set.device,
        sessionId = set.sessionId
    )

    return WorkoutSummary(
        occurrenceId = occurrence.id,
        workoutName = activation?.workouts
            ?.firstOrNull { it.id == occurrence.activationWorkoutId }
            ?.name,
        status = occurrence.status,
        startedAtMillis = occurrence.startedAtMillis,
        resolvedAtMillis = occurrence.resolvedAtMillis,
        exercises = entries.map { entry ->
            val performed = workingByEntry[entry.id].orEmpty().map { toSummarySet(it) }
            val sourceExerciseId = entry.sourceActivationEntryId?.let(sourceExerciseByEntry::get)
            WorkoutSummaryExercise(
                occurrenceEntryId = entry.id,
                exerciseId = entry.exerciseId,
                exerciseName = entry.exerciseName,
                prescribedSets = entry.sets,
                prescribedReps = entry.reps,
                prescribedDurationSeconds = entry.durationSeconds,
                prescribedDistanceMeters = entry.distanceMeters,
                prescribedForceNewtons = entry.forceNewtons,
                prescribedWeightKg = entry.weightKg,
                prescribedLoadKind = entry.loadKind,
                performedSets = performed,
                remainingSets = (entry.sets - performed.size).coerceAtLeast(0),
                remainingDisposition = entry.remainingDisposition,
                isReplaced = sourceExerciseId != null && sourceExerciseId != entry.exerciseId
            )
        },
        extraSets = workingSets
            .filter { it.isAdditionalTo(slotIds) }
            .map { toSummarySet(it) },
        warmupSets = warmupSets.map { toSummarySet(it) },
        sessions = sessions
            .sortedWith(compareBy({ it.startedAtMillis }, { it.id }))
            .map {
                WorkoutSummarySession(
                    sessionId = it.id,
                    startedAtMillis = it.startedAtMillis,
                    endedAtMillis = it.endedAtMillis,
                    note = sessionNotes[it.id]
                )
            }
    )
}
