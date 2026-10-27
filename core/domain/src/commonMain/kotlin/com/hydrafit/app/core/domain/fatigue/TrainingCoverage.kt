package com.hydrafit.app.core.domain.fatigue

import kotlinx.coroutines.flow.Flow

/** Recorded involvement only: null means no snapshot, not today's catalog profile. */
data class CoverageSet(
    val performedAtMillis: Long,
    val isWarmup: Boolean,
    val involvements: Map<MuscleGroup, Double>?,
    val isCluster: Boolean = false
)

interface TrainingCoverageRepository {
    fun observeSets(): Flow<List<CoverageSet>>
}

data class MuscleTrainingCoverage(
    val muscle: MuscleGroup,
    val workingSets: Int,
    val estimatedInvolvementCredits: Double,
    val lastTrainedAtMillis: Long?,
    val clusters: Int = 0
)

/** Counts and estimated credits are descriptive, not direct sets or physiological equivalents. */
data class TrainingCoverage(
    val entries: List<MuscleTrainingCoverage>,
    val fromEpochDay: Long,
    val throughEpochDay: Long,
    val unclassifiedSetsInWindow: Int,
    val hasUnclassifiedHistory: Boolean
)
