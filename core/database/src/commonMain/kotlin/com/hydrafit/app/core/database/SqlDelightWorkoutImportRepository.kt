package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.transfer.WorkoutCsvSource
import com.hydrafit.app.core.domain.transfer.WorkoutImportBucket as DomainImportBucket
import com.hydrafit.app.core.domain.transfer.WorkoutImportCommitFailure
import com.hydrafit.app.core.domain.transfer.WorkoutImportException
import com.hydrafit.app.core.domain.transfer.WorkoutImportPreview
import com.hydrafit.app.core.domain.transfer.WorkoutImportProvenance
import com.hydrafit.app.core.domain.transfer.WorkoutImportRepository
import com.hydrafit.app.core.domain.transfer.WorkoutImportResult
import com.hydrafit.app.core.domain.workout.WorkoutTimingProvenance
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Commits an approved import plan in one transaction.
 *
 * Every session is written already closed with its own identity, so imported history never appears as
 * a live session and never touches the logger's open-session lifecycle. Set rows carry the mapping
 * that was resolved at preview time (`effectiveInvolvements`), which freezes the attribution the user
 * approved instead of following later catalog edits. The provenance ledger is re-read inside the
 * transaction and re-checked before any write: a session recorded — or changed — since the preview
 * fails the whole commit with no partial history.
 */
class SqlDelightWorkoutImportRepository(private val database: HydraFitDatabase) :
    WorkoutImportRepository {

    private val provenanceQueries = database.workoutImportQueries
    private val sessionQueries = database.workoutSessionQueries
    private val setQueries = database.workoutLogQueries
    private val bucketQueries = database.workoutImportBucketQueries

    override suspend fun buckets(
        source: WorkoutCsvSource,
        candidateKeys: Set<String>
    ): List<DomainImportBucket> = bucketQueries.selectBuckets(source.name).executeAsList()
        .filter { it.candidateKey in candidateKeys }
        .map { bucket ->
            DomainImportBucket(
                source = source,
                candidateKey = bucket.candidateKey,
                content = bucket.content,
                partition = bucket.partition,
                importedSessionIds = bucketQueries.selectLinks(source.name, bucket.candidateKey)
                    .executeAsList().map { it.importedSessionId }
            )
        }

    override suspend fun provenance(
        source: WorkoutCsvSource,
        candidateKeys: Set<String>
    ): List<WorkoutImportProvenance> =
        provenanceQueries.selectProvenanceBySource(source.name).executeAsList()
            .filter { it.candidateKey in candidateKeys }
            .map { it.toDomain() }

    override suspend fun commit(preview: WorkoutImportPreview): WorkoutImportResult {
        if (preview.sessions.isEmpty()) {
            throw WorkoutImportException(
                WorkoutImportCommitFailure.NOTHING_TO_IMPORT,
                "There is nothing to import"
            )
        }
        // A large import writes many thousands of rows, so the transaction runs off the caller's
        // dispatcher instead of stalling the UI thread; this matches the write-path convention in
        // SqlDelightBackupStagingRepository and SqlDelightEquipmentProfileRepository.
        return withContext(Dispatchers.Default) { commitTransaction(preview) }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun commitTransaction(preview: WorkoutImportPreview): WorkoutImportResult {
        var importedSessions = 0
        var importedSets = 0
        var skippedSessions = 0
        database.transaction {
            if (preview.problems.isNotEmpty()) {
                throw WorkoutImportException(
                    WorkoutImportCommitFailure.NOT_IMPORTABLE,
                    "The import preview still has unresolved problems"
                )
            }
            val storedBuckets = bucketQueries.selectBuckets(preview.source.name).executeAsList()
            val skippedBuckets = mutableSetOf<String>()
            preview.buckets.forEach { bucket ->
                val previous = storedBuckets.singleOrNull { it.candidateKey == bucket.candidateKey }
                if (previous != null) {
                    if (previous.content != bucket.content ||
                        previous.partition != bucket.partition
                    ) {
                        throw WorkoutImportException(
                            WorkoutImportCommitFailure.SOURCE_CHANGED,
                            "The source bucket or its approved partition changed"
                        )
                    }
                    skippedBuckets += bucket.candidateKey
                }
            }
            val recorded = provenanceQueries.selectProvenanceBySource(preview.source.name)
                .executeAsList()
            // Commit-time revalidation of the approved non-overlap invariant: history may have been
            // created or corrected since the preview was prepared, so re-read it inside the
            // transaction and refuse rather than write interleaved history.
            val existingBounds = sessionQueries.selectAllSessions().executeAsList()
                .map { it.startedAtMillis to it.endedAtMillis }
            val writtenBounds = mutableListOf<Pair<Long, Long?>>()
            val newLinks = mutableMapOf<String, MutableList<String>>()
            preview.sessions.forEach { session ->
                if (session.candidateKey in skippedBuckets) {
                    skippedSessions++
                    return@forEach
                }
                val matches = recorded.filter { it.candidateKey == session.candidateKey }
                val exact = matches.filter { it.content == session.content }
                when {
                    exact.size > 1 -> throw WorkoutImportException(
                        WorkoutImportCommitFailure.SOURCE_CHANGED,
                        "More than one imported session matches this source workout"
                    )
                    exact.size == 1 -> {
                        skippedSessions++
                        return@forEach
                    }
                    matches.isNotEmpty() -> throw WorkoutImportException(
                        WorkoutImportCommitFailure.SOURCE_CHANGED,
                        "This source workout changed after the preview was prepared"
                    )
                }
                val overlaps = (existingBounds + writtenBounds).any { (start, end) ->
                    session.startedAtMillis <= (end ?: Long.MAX_VALUE) &&
                        session.endedAtMillis >= start
                }
                if (overlaps) {
                    throw WorkoutImportException(
                        WorkoutImportCommitFailure.SESSION_OVERLAP,
                        "Imported history would overlap a session that now exists"
                    )
                }
                writtenBounds += session.startedAtMillis to session.endedAtMillis
                val sessionId = Uuid.random().toString()
                sessionQueries.insertSession(
                    id = sessionId,
                    startedAtMillis = session.startedAtMillis,
                    endedAtMillis = session.endedAtMillis,
                    localEpochDay = session.localEpochDay
                )
                session.sets.forEach { set ->
                    val exercise = requireNotNull(session.exercises[set.exerciseId])
                    setQueries.insertSetWithTimingProvenance(
                        exerciseId = set.exerciseId,
                        reps = set.reps?.toLong(),
                        weightKg = set.weightKg,
                        performedAt = set.performedAtMillis,
                        isWarmup = if (set.isWarmup) 1L else 0L,
                        involvements = frozenInvolvements(exercise),
                        weekNumber = null,
                        cycleNumber = null,
                        dayIndex = null,
                        rir = null,
                        sessionId = sessionId,
                        loadKind = set.loadKind.name,
                        timingProvenance = WorkoutTimingProvenance.CATCH_UP.name,
                        startedAtElapsedMillis = null,
                        completedAtElapsedMillis = null,
                        durationSeconds = set.durationSeconds?.toLong()
                    )
                }
                if (preview.buckets.any { it.candidateKey == session.candidateKey }) {
                    newLinks.getOrPut(session.candidateKey) { mutableListOf() }.add(sessionId)
                } else {
                    provenanceQueries.insertProvenance(
                        source = preview.source.name,
                        candidateKey = session.candidateKey,
                        content = session.content,
                        importedSessionId = sessionId
                    )
                }
                importedSessions++
                importedSets += session.sets.size
            }
            preview.buckets.filter { it.candidateKey in newLinks }.forEach { bucket ->
                bucketQueries.insertBucket(
                    source = preview.source.name,
                    candidateKey = bucket.candidateKey,
                    content = bucket.content,
                    partition = bucket.partition
                )
                newLinks.getValue(bucket.candidateKey).forEachIndexed { ordinal, sessionId ->
                    bucketQueries.insertLink(
                        source = preview.source.name,
                        candidateKey = bucket.candidateKey,
                        ordinal = ordinal.toLong(),
                        importedSessionId = sessionId
                    )
                }
            }
        }
        return WorkoutImportResult(importedSessions, importedSets, skippedSessions)
    }
}

/**
 * Encodes the resolved exercise's effective mapping for storage on the imported set. An exercise with
 * no explicit map derives it from its muscle tags, matching what the rest of the app reads back.
 */
private fun frozenInvolvements(exercise: Exercise): String =
    encodeInvolvements(exercise.effectiveInvolvements)

private fun com.hydrafit.app.core.database.WorkoutImportProvenance.toDomain() =
    WorkoutImportProvenance(
        source = WorkoutCsvSource.valueOf(source),
        candidateKey = candidateKey,
        content = content,
        importedSessionId = importedSessionId
    )
