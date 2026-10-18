package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutPrescriptionSnapshot
import com.hydrafit.app.core.domain.workout.WorkoutSession
import com.hydrafit.app.core.domain.workout.WorkoutSet

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
    fun exposures(exerciseId: String, nowMillis: Long): List<ProgressionExposure> {
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
                    target.loadKind == LoadKind.EXTERNAL &&
                    target.reps != null &&
                    target.durationSeconds == null &&
                    (target.weightKg ?: 0.0) > 0.0 &&
                    group.all {
                        // A blank or explicit-zero recorded load cannot establish weighted target
                        // success or failure, so it makes the whole exposure unjudgeable rather
                        // than a miss. Bodyweight/added/timed work is excluded the same way.
                        it.loadKind == LoadKind.EXTERNAL &&
                            it.reps != null &&
                            it.durationSeconds == null &&
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

    fun replay(exerciseId: String, nowMillis: Long, config: ProgressionConfig): ProgressionReplay {
        var streak = 0
        var increments = 0
        var failureApplied = false
        var previous: WorkoutPrescriptionSnapshot? = null
        var hasEligibleExposure = false
        for (exposure in exposures(exerciseId, nowMillis)) {
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
