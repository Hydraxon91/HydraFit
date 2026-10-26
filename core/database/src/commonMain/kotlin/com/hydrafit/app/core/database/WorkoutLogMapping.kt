package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.StationAttribution
import com.hydrafit.app.core.domain.equipment.StationLoadBasis
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
    distanceMeters = distanceMeters?.toInt()
)

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
