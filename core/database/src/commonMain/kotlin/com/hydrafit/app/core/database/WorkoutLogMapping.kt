package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.workout.WorkoutSession as DomainWorkoutSession
import com.hydrafit.app.core.domain.workout.WorkoutSet as DomainWorkoutSet

internal fun WorkoutSet.toDomain(): DomainWorkoutSet = DomainWorkoutSet(
    id = id,
    exerciseId = exerciseId,
    reps = reps.toInt(),
    weightKg = weightKg,
    loadKind = decodeLoadKind(loadKind),
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
    completedAtElapsedMillis = completedAtElapsedMillis
)

internal fun WorkoutSession.toDomain(): DomainWorkoutSession = DomainWorkoutSession(
    id = id,
    startedAtMillis = startedAtMillis,
    endedAtMillis = endedAtMillis,
    localEpochDay = localEpochDay,
    occurrenceId = occurrenceId
)
