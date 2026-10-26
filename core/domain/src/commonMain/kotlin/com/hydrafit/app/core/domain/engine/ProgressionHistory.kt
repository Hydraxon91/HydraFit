package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutPrescriptionSnapshot
import com.hydrafit.app.core.domain.workout.WorkoutSession
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.math.round

enum class ProgressionExposureOutcome { SUCCESS, MISS, UNKNOWN }

data class ProgressionExposure(
    val target: WorkoutPrescriptionSnapshot?,
    val outcome: ProgressionExposureOutcome
)

/** One coherent read; legacyTargets contains only unambiguously attributed frozen occurrence work. */
data class ProgressionHistory(
    val sets: List<WorkoutSet> = emptyList(),
    val sessions: List<WorkoutSession> = emptyList(),
    val legacyTargets: Map<Long, WorkoutPrescriptionSnapshot> = emptyMap()
) {
    fun exposures(
        exerciseId: String,
        nowMillis: Long,
        loadKind: LoadKind = LoadKind.EXTERNAL
    ): List<ProgressionExposure> {
        val sessionsById = sessions.associateBy { it.id }
        val work = sets.filter {
            it.exerciseId == exerciseId && !it.isWarmup && it.performedAtMillis <= nowMillis
        }
        return work.withIndex().groupBy { (index, set) ->
            set.sessionId?.let { true to it } ?: (false to index.toString())
        }.values.map { group -> group.map { it.value } }
            .sortedWith(
                compareBy<List<WorkoutSet>> { group ->
                    group.minOf { it.performedAtMillis }
                }.thenBy { group -> group.first().sessionId.orEmpty() }
            )
            .map { group ->
                val session = group.first().sessionId?.let(sessionsById::get)
                val ended = session?.endedAtMillis
                val targets = group.map { it.prescriptionSnapshot ?: legacyTargets[it.id] }
                targets.filterNotNull().forEach { it.requireValid() }
                val target = targets.firstOrNull()
                val valid = target != null &&
                    targets.all { it == target } &&
                    target.exerciseId == exerciseId &&
                    !target.isDeload &&
                    target.loadKind == loadKind &&
                    target.reps != null &&
                    target.durationSeconds == null &&
                    target.distanceMeters == null &&
                    target.forceNewtons == null &&
                    (target.weightKg ?: 0.0) > 0.0 &&
                    group.all {
                        // A blank or explicit-zero recorded load cannot establish weighted target
                        // success or failure, so it makes the whole exposure unjudgeable rather
                        // than a miss. Work of another load kind, and timed, distance or force work,
                        // is excluded the same way.
                        it.loadKind == loadKind &&
                            it.reps != null &&
                            it.durationSeconds == null &&
                            it.distanceMeters == null &&
                            it.forceNewtons == null &&
                            (it.weightKg ?: 0.0) > 0.0
                    }
                val outcome = if (ended == null ||
                    ended > nowMillis ||
                    ended < group.maxOf { it.performedAtMillis } ||
                    !valid ||
                    group.size < requireNotNull(target).sets
                ) {
                    ProgressionExposureOutcome.UNKNOWN
                } else {
                    val prescribed = requireNotNull(target)
                    val completed = group.count {
                        requireNotNull(it.reps) >= requireNotNull(prescribed.reps) &&
                            (it.weightKg ?: -1.0) >= requireNotNull(prescribed.weightKg)
                    }
                    if (completed >= prescribed.sets) {
                        ProgressionExposureOutcome.SUCCESS
                    } else {
                        ProgressionExposureOutcome.MISS
                    }
                }
                ProgressionExposure(target, outcome)
            }
    }

    /**
     * One session judged against an ordered prescription: how many of its prescribed slots it
     * satisfied. A session is comparable only when every recorded set names one of that
     * prescription's own slots, exactly once, so work performed against a different or legacy
     * prescription is unjudgeable rather than stitched into this one's streak.
     */
    fun orderedExposures(
        exerciseId: String,
        targets: SlotSetTargets,
        nowMillis: Long
    ): List<OrderedExposure> {
        val sessionsById = sessions.associateBy { it.id }
        val work = sets.filter {
            it.exerciseId == exerciseId && !it.isWarmup && it.performedAtMillis <= nowMillis
        }
        return work.withIndex().groupBy { (index, set) ->
            set.sessionId?.let { true to it } ?: (false to index.toString())
        }.values.map { group -> group.map { it.value } }
            .sortedWith(
                compareBy<List<WorkoutSet>> { group ->
                    group.minOf { it.performedAtMillis }
                }.thenBy { group -> group.first().sessionId.orEmpty() }
            )
            .map { group ->
                group.toOrderedExposure(exerciseId, targets, sessionsById, nowMillis)
            }
    }

    private fun List<WorkoutSet>.toOrderedExposure(
        exerciseId: String,
        targets: SlotSetTargets,
        sessionsById: Map<String, WorkoutSession>,
        nowMillis: Long
    ): OrderedExposure {
        val session = first().sessionId?.let(sessionsById::get)
        val ended = session?.endedAtMillis
        val closed = ended != null && ended <= nowMillis && ended >= maxOf { it.performedAtMillis }
        val indexed = mapNotNull { set ->
            set.prescriptionSnapshot?.setIndex?.let { index -> index to set }
        }
        val comparable = indexed.size == size &&
            indexed.map { it.first }.distinct().size == indexed.size &&
            indexed.all { (index, set) ->
                index in targets.targets.indices &&
                    set.matchesOrderedTarget(exerciseId, targets.targets[index])
            }
        val satisfied = indexed.count { (index, set) ->
            index in targets.targets.indices && set.reached(targets.targets[index])
        }
        return OrderedExposure(
            satisfiedSlots = satisfied,
            prescribedSlots = targets.sets,
            closed = closed,
            comparable = comparable
        )
    }

    /** Whether this set was performed against exactly the ordered slot it claims. */
    private fun WorkoutSet.matchesOrderedTarget(exerciseId: String, target: SetTarget): Boolean {
        val recorded = prescriptionSnapshot ?: return false
        return recorded.exerciseId == exerciseId &&
            !recorded.isDeload &&
            recorded.reps == target.reps &&
            recorded.durationSeconds == target.durationSeconds &&
            recorded.distanceMeters == target.distanceMeters &&
            recorded.loadKind == target.loadKind &&
            // Loads are compared on the gram, the same precision the write path rounds to, so a
            // reformatted value never silently degrades an exposure to unjudgeable.
            sameLoad(recorded.weightKg, target.weightKg) &&
            recorded.targetRir == target.targetRir
    }

    private fun sameLoad(first: Double?, second: Double?): Boolean = when {
        first == null || second == null -> first == second
        else -> round(first * 1_000.0) == round(second * 1_000.0)
    }

    /**
     * A performed set reaches its prescribed slot only when it meets that slot's own reps and load.
     * A blank or zero recorded load, a non-external or timed slot, and a heavier set logged against
     * another slot all fail, so a surplus set can never substitute for a missed one.
     */
    private fun WorkoutSet.reached(target: SetTarget): Boolean {
        if (target.loadKind != LoadKind.EXTERNAL) return false
        val prescribedLoad = target.weightKg ?: return false
        if (prescribedLoad <= 0.0) return false
        val prescribedReps = target.reps ?: return false
        if (loadKind != LoadKind.EXTERNAL) return false
        val performedLoad = weightKg ?: return false
        val performedReps = reps ?: return false
        return performedReps >= prescribedReps && performedLoad >= prescribedLoad
    }

    fun replay(
        exerciseId: String,
        nowMillis: Long,
        config: ProgressionConfig,
        loadKind: LoadKind = LoadKind.EXTERNAL
    ): ProgressionReplay {
        var streak = 0
        var increments = 0
        var failureApplied = false
        var previous: WorkoutPrescriptionSnapshot? = null
        var hasEligibleExposure = false
        for (exposure in exposures(exerciseId, nowMillis, loadKind)) {
            if (exposure.outcome == ProgressionExposureOutcome.UNKNOWN) {
                streak = 0
                previous = null
                continue
            }
            hasEligibleExposure = true
            if (previous != exposure.target) streak = 0
            previous = exposure.target
            if (exposure.outcome == ProgressionExposureOutcome.SUCCESS) {
                streak = if (streak < 0) 1 else streak + 1
                if (streak % config.successStreak == 0) increments++
            } else {
                streak = if (streak > 0) -1 else streak - 1
                if (-streak % config.failureStreak == 0) {
                    increments--
                    failureApplied = true
                }
            }
        }
        return ProgressionReplay(
            increments.coerceIn(0, config.maxIncrements),
            failureApplied,
            hasEligibleExposure
        )
    }
}

data class ProgressionReplay(
    val increments: Int,
    val failureApplied: Boolean,
    val hasEligibleExposure: Boolean
)

/**
 * One session's judgement against an ordered prescription. [comparable] is false when the session was
 * not performed against this exact prescription, and an unjudgeable session is never a miss.
 */
data class OrderedExposure(
    val satisfiedSlots: Int,
    val prescribedSlots: Int,
    val closed: Boolean,
    val comparable: Boolean
) {
    val outcome: ProgressionExposureOutcome
        get() = when {
            !comparable || !closed || prescribedSlots <= 0 -> ProgressionExposureOutcome.UNKNOWN
            satisfiedSlots >= prescribedSlots -> ProgressionExposureOutcome.SUCCESS
            else -> ProgressionExposureOutcome.MISS
        }
}
