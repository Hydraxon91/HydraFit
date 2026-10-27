package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.StationAttribution
import com.hydrafit.app.core.domain.equipment.StationLoadBasis
import com.hydrafit.app.core.domain.routine.ClusterKind
import com.hydrafit.app.core.domain.workout.DeviceAttribution
import com.hydrafit.app.core.domain.workout.WorkoutClusterSummary
import com.hydrafit.app.core.domain.workout.WorkoutPrescriptionSnapshotJson
import com.hydrafit.app.core.domain.workout.WorkoutSession as DomainWorkoutSession
import com.hydrafit.app.core.domain.workout.WorkoutSet as DomainWorkoutSet

internal fun WorkoutSet.toDomain(): DomainWorkoutSet = DomainWorkoutSet(
    id = id,
    exerciseId = exerciseId,
    reps = reps?.toInt(),
    weightKg = weightKg,
    loadKind = decodeLoadKind(loadKind),
    station = stationAttributionOf(stationId, stationName, stationLoadBasis),
    performedAtMillis = performedAt,
    isWarmup = isWarmup != 0L,
    weekNumber = weekNumber?.toInt(),
    cycleNumber = cycleNumber?.toInt(),
    dayIndex = dayIndex?.toInt(),
    rir = rir?.toInt(),
    sessionId = sessionId,
    occurrenceId = occurrenceId,
    occurrenceEntryId = occurrenceEntryId,
    timingProvenance = decodeWorkoutTimingProvenance(timingProvenance),
    startedAtElapsedMillis = startedAtElapsedMillis,
    completedAtElapsedMillis = completedAtElapsedMillis,
    durationSeconds = durationSeconds?.toInt(),
    prescriptionSnapshot = prescriptionSnapshot?.let(WorkoutPrescriptionSnapshotJson::decode),
    distanceMeters = distanceMeters?.toInt(),
    forceNewtons = forceNewtons,
    device = deviceAttributionOf(deviceName, deviceRatingKg),
    cluster = clusterSummaryOf(clusterKind, clusterPlannedSegments, clusterPerformedSegments)
)

/**
 * Strict persisted cluster decoding. All three fields are absent for an ordinary set; a partial or
 * inconsistent triple is corrupt data rather than an inferred cluster.
 */
internal fun clusterSummaryOf(
    kind: String?,
    plannedSegments: Long?,
    performedSegments: Long?
): WorkoutClusterSummary? {
    if (kind == null && plannedSegments == null && performedSegments == null) return null
    require(kind != null && plannedSegments != null && performedSegments != null) {
        "Incomplete stored cluster identity"
    }
    val decodedKind = requireNotNull(
        runCatching { ClusterKind.valueOf(kind) }.getOrNull()
    ) { "Unknown stored cluster kind" }
    return WorkoutClusterSummary(
        kind = decodedKind,
        plannedSegments = plannedSegments.toInt(),
        performedSegments = performedSegments.toInt()
    ).also { it.requireValid() }
}

/**
 * Strict persisted device decoding. Both fields are absent for a set with no measuring device; a
 * rating without a name is corrupt data and must not become an anonymous device identity.
 */
internal fun deviceAttributionOf(name: String?, ratingKg: Double?): DeviceAttribution? {
    if (name == null && ratingKg == null) return null
    require(name != null) { "Incomplete stored device attribution" }
    return DeviceAttribution(name = name, ratingKg = ratingKg)
}

/**
 * Strict persisted station decoding. All three fields are absent for unattributed rows; a partial
 * attribution is corrupt data and must never become a null station (which would re-enter the generic
 * planner population). A local id or the merge-only zero identity both require a frozen name/basis.
 */
internal fun stationAttributionOf(
    stationId: Long?,
    stationName: String?,
    stationLoadBasis: String?
): StationAttribution? {
    if (stationId == null && stationName == null && stationLoadBasis == null) return null
    require(stationId != null && stationName != null && stationLoadBasis != null) {
        "Incomplete stored station attribution"
    }
    val basis = StationLoadBasis.fromStored(stationLoadBasis)
        ?: error("Unknown stored station load basis")
    return StationAttribution(stationId, stationName, basis)
}

internal fun WorkoutSession.toDomain(): DomainWorkoutSession = DomainWorkoutSession(
    id = id,
    startedAtMillis = startedAtMillis,
    endedAtMillis = endedAtMillis,
    localEpochDay = localEpochDay,
    occurrenceId = occurrenceId
)
