package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.engine.PeriodizationConfig
import com.hydrafit.app.core.domain.engine.ProgressionHistory
import com.hydrafit.app.core.domain.workout.WorkoutPrescriptionSnapshot

/**
 * The one coherent progression read: recorded sets, their sessions and the frozen targets that seed
 * legacy rows. Shared so the planner's exercise-level replay and a slot's own filtered history are
 * always derived from the same snapshot, never from two slightly different queries.
 */
internal fun HydraFitDatabase.readProgressionHistory(): ProgressionHistory = transactionWithResult {
    // Station-attributed work is excluded from the generic progression replay. Its own station
    // history is a separate comparison population and must not seed this exercise-level rule.
    val sets = workoutLogQueries.selectAllSets().executeAsList()
        .map { it.toDomain() }
        .filter { it.station == null && it.cluster == null }
    val sessions = workoutSessionQueries.selectAllSessions().executeAsList()
        .map { it.toDomain() }
    val entries = trainingScheduleQueries.selectAllOccurrenceEntries().executeAsList()
        .associateBy { it.id }
    val occurrences = trainingScheduleQueries.selectAllOccurrences().executeAsList()
        .associateBy { it.id }
    val activations = trainingScheduleQueries.selectAllActivations().executeAsList()
        .associateBy { it.id }
    val sessionsById = sessions.associateBy { it.id }
    val targets = sets.mapNotNull { set ->
        if (set.prescriptionSnapshot != null) return@mapNotNull null
        val entry = set.occurrenceEntryId?.let(entries::get) ?: return@mapNotNull null
        if (set.occurrenceId != entry.occurrenceId || set.exerciseId != entry.exerciseId) {
            return@mapNotNull null
        }
        val session = set.sessionId?.let(sessionsById::get) ?: return@mapNotNull null
        if (session.occurrenceId != null && session.occurrenceId != entry.occurrenceId) {
            return@mapNotNull null
        }
        val occurrence = occurrences[entry.occurrenceId] ?: return@mapNotNull null
        val activation = activations[occurrence.activationId] ?: return@mapNotNull null
        val week = activation.weekNumber?.toInt() ?: return@mapNotNull null
        val target = WorkoutPrescriptionSnapshot(
            exerciseId = entry.exerciseId,
            sets = entry.sets.toInt(),
            reps = entry.reps?.toInt(),
            durationSeconds = entry.durationSeconds?.toInt(),
            distanceMeters = entry.distanceMeters?.toInt(),
            forceNewtons = entry.forceNewtons,
            loadKind = decodeLoadKind(entry.loadKind),
            weightKg = entry.weightKg,
            isDeload = PeriodizationConfig().isDeload(week)
        )
        target.requireValid()
        set.id to target
    }.toMap()
    ProgressionHistory(sets, sessions, targets)
}
