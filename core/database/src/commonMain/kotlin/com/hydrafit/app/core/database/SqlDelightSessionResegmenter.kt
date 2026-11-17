package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.workout.SessionResegmenter
import com.hydrafit.app.core.domain.workout.WorkoutLoadPolicy
import com.hydrafit.app.core.domain.workout.WorkoutSetCorrection
import com.hydrafit.app.core.domain.workout.resegmentSessions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SqlDelightSessionResegmenter(private val database: HydraFitDatabase) : SessionResegmenter {
    private val setQueries = database.workoutLogQueries
    private val sessionQueries = database.workoutSessionQueries

    override suspend fun resegmentAfterSetCorrection(
        setId: Long,
        correction: WorkoutSetCorrection,
        utcOffsetMillis: Long
    ): Unit = withContext(Dispatchers.Default) {
        database.transaction {
            val set = requireNotNull(setQueries.selectSetById(setId).executeAsOneOrNull()) {
                "That set no longer exists"
            }.toDomain()
            WorkoutLoadPolicy.validateShape(set.loadKind, correction.weightKg)
            correction.clusterSegments?.let { segments ->
                val summary = requireNotNull(set.cluster) { "That set is not a cluster" }
                require(segments.size == summary.performedSegments) {
                    "A correction must preserve the recorded segment count"
                }
                segments.forEachIndexed { index, segment ->
                    require(segment.position == index) { "Cluster segments must remain ordered" }
                    segment.requireValid()
                }
                require(
                    segments.first().reps == correction.reps &&
                        segments.first().weightKg == correction.weightKg &&
                        segments.first().rir == correction.rir
                ) { "The parent must match the opening segment" }
                val clusterQueries = database.workoutClusterQueries
                clusterQueries.deleteSegmentsForSet(setId)
                segments.drop(1).forEach { segment ->
                    clusterQueries.insertSegment(
                        setId = setId,
                        position = segment.position.toLong(),
                        reps = segment.reps.toLong(),
                        weightKg = segment.weightKg,
                        rir = segment.rir?.toLong()
                    )
                }
            }
            val measureKindChanged =
                (set.reps != null) != (correction.reps != null) ||
                    (set.durationSeconds != null) != (correction.durationSeconds != null) ||
                    (set.distanceMeters != null) != (correction.distanceMeters != null) ||
                    (set.forceNewtons != null) != (correction.forceNewtons != null)
            if (set.performedAtMillis != correction.performedAtMillis) {
                resegmentTime(setId, correction.performedAtMillis, utcOffsetMillis)
            }
            if (
                set.reps != correction.reps ||
                set.durationSeconds != correction.durationSeconds ||
                set.distanceMeters != correction.distanceMeters ||
                set.forceNewtons != correction.forceNewtons ||
                set.weightKg != correction.weightKg ||
                set.rir != correction.rir
            ) {
                setQueries.updateSetValuesAndInvalidateTiming(
                    reps = correction.reps?.toLong(),
                    durationSeconds = correction.durationSeconds?.toLong(),
                    distanceMeters = correction.distanceMeters?.toLong(),
                    forceNewtons = correction.forceNewtons,
                    weightKg = correction.weightKg,
                    rir = correction.rir?.toLong(),
                    id = setId
                )
            } else {
                setQueries.updateSetValues(
                    reps = correction.reps?.toLong(),
                    durationSeconds = correction.durationSeconds?.toLong(),
                    distanceMeters = correction.distanceMeters?.toLong(),
                    forceNewtons = correction.forceNewtons,
                    weightKg = correction.weightKg,
                    rir = correction.rir?.toLong(),
                    id = setId
                )
            }
            // A correction carries the complete desired device state; writing it here keeps a force
            // set's descriptive identity in the same transaction as its measurement.
            if (set.device != correction.device) {
                setQueries.assignDevice(
                    deviceName = correction.device?.name,
                    deviceRatingKg = correction.device?.ratingKg,
                    id = setId
                )
            }
            // A target recorded for repaired reps cannot describe a set that now records seconds or
            // metres: the exposure becomes unjudgeable instead of silently mismatched, the same rule
            // the logger applies when it decides not to capture a target at all.
            if (measureKindChanged) {
                setQueries.clearPrescriptionSnapshot(setId)
            }
        }
    }

    override suspend fun resegmentAfterTimeCorrection(
        setId: Long,
        performedAtMillis: Long,
        utcOffsetMillis: Long
    ): Unit = withContext(Dispatchers.Default) {
        // Read, recompute, and write inside one transaction so the stored sessions can never be
        // observed interleaved or inconsistent with their sets.
        database.transaction {
            resegmentTime(setId, performedAtMillis, utcOffsetMillis)
        }
    }

    private fun resegmentTime(setId: Long, performedAtMillis: Long, utcOffsetMillis: Long) {
        // Only the corrected set and each session's earliest and latest other set matter: the nearest
        // session is chosen by those ranges and the bounds are their minimum and maximum, so the
        // result equals the one computed from every set without hydrating them all.
        val corrected = setQueries.selectSetById(setId).executeAsOneOrNull()?.toDomain()
        val boundaryIds = setQueries.selectSessionBoundarySetIds(setId).executeAsList()
        val sets =
            listOfNotNull(corrected) + boundaryIds.chunked(BOUNDARY_ID_CHUNK).flatMap { ids ->
                setQueries.selectSetsByIds(ids).executeAsList().map { it.toDomain() }
            }
        val sessions = sessionQueries.selectAllSessions().executeAsList().map { it.toDomain() }
        val resegmentation = resegmentSessions(
            sets = sets,
            sessions = sessions,
            correctedSetId = setId,
            performedAtMillis = performedAtMillis,
            utcOffsetMillis = utcOffsetMillis
        )
        setQueries.updateSetPerformedAt(performedAt = performedAtMillis, id = setId)
        resegmentation.assignments.forEach { (id, sessionId) ->
            setQueries.assignSession(sessionId = sessionId, id = id)
        }
        resegmentation.sessionBounds.forEach { (sessionId, bounds) ->
            sessionQueries.updateSessionBounds(
                startedAtMillis = bounds.startedAtMillis,
                endedAtMillis = bounds.endedAtMillis,
                localEpochDay = bounds.localEpochDay,
                id = sessionId
            )
        }
    }

    private companion object {
        // Stays under SQLite's bound-parameter limit on any supported Android version.
        const val BOUNDARY_ID_CHUNK = 500
    }
}
