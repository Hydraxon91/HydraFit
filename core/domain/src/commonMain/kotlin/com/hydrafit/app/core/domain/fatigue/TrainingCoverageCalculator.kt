package com.hydrafit.app.core.domain.fatigue

import com.hydrafit.app.core.domain.time.localEpochDay

object TrainingCoverageCalculator {
    /** Seven civil days including today, using the request's fixed current UTC offset. */
    fun calculate(
        sets: List<CoverageSet>,
        nowMillis: Long,
        utcOffsetMillis: Long
    ): TrainingCoverage {
        val today = localEpochDay(nowMillis, utcOffsetMillis)
        val start = today - 6L
        val counts = mutableMapOf<MuscleGroup, Int>()
        val clusters = mutableMapOf<MuscleGroup, Int>()
        val credits = mutableMapOf<MuscleGroup, Double>()
        val lastTrained = mutableMapOf<MuscleGroup, Long>()
        var unclassifiedInWindow = 0
        var hasUnclassifiedHistory = false
        for (set in sets) {
            if (set.isWarmup || set.performedAtMillis > nowMillis) continue
            val inWindow = localEpochDay(set.performedAtMillis, utcOffsetMillis) in start..today
            val involvement = set.involvements
            if (involvement == null) {
                hasUnclassifiedHistory = true
                if (inWindow) unclassifiedInWindow++
                continue
            }
            for ((muscle, weight) in involvement) {
                if (!weight.isFinite() || weight <= 0.0) continue
                val previous = lastTrained[muscle]
                if (previous == null || set.performedAtMillis > previous) {
                    lastTrained[muscle] = set.performedAtMillis
                }
                if (inWindow) {
                    if (set.isCluster) {
                        clusters[muscle] = (clusters[muscle] ?: 0) + 1
                        continue
                    }
                    counts[muscle] = (counts[muscle] ?: 0) + 1
                    credits[muscle] = (credits[muscle] ?: 0.0) + weight
                }
            }
        }
        return TrainingCoverage(
            entries = MuscleGroup.entries.map { muscle ->
                MuscleTrainingCoverage(
                    muscle = muscle,
                    workingSets = counts[muscle] ?: 0,
                    estimatedInvolvementCredits = credits[muscle] ?: 0.0,
                    lastTrainedAtMillis = lastTrained[muscle],
                    clusters = clusters[muscle] ?: 0
                )
            },
            fromEpochDay = start,
            throughEpochDay = today,
            unclassifiedSetsInWindow = unclassifiedInWindow,
            hasUnclassifiedHistory = hasUnclassifiedHistory
        )
    }
}
