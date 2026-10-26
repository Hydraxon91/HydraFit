package com.hydrafit.app.core.domain.transfer

import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.time.daysFromCivil
import com.hydrafit.app.core.domain.workout.WorkoutLoadException
import com.hydrafit.app.core.domain.workout.WorkoutLoadPolicy
import com.hydrafit.app.core.domain.workout.WorkoutSession
import com.hydrafit.app.core.domain.workout.WorkoutSet

/** Pure resolution: a preview never writes, merges sessions or changes catalog definitions. */
object PreviewWorkoutImport {
    fun build(
        parsed: ParsedWorkoutCsv,
        choices: WorkoutImportChoices,
        catalog: List<Exercise>,
        provenance: List<WorkoutImportProvenance> = emptyList(),
        existingSessions: List<WorkoutSession> = emptyList(),
        buckets: List<WorkoutImportBucket> = emptyList()
    ): WorkoutImportPreview {
        val problems = mutableListOf<WorkoutImportProblem>()
        val notices = mutableListOf<WorkoutImportProblem>()
        fun problem(issue: WorkoutImportIssue, row: Int? = null) {
            problems += WorkoutImportProblem(issue, row)
        }
        fun notice(issue: WorkoutImportIssue, row: Int? = null) {
            notices += WorkoutImportProblem(issue, row)
        }
        val allIndices = parsed.rows.map { it.sourceRowIndex }.toSet() +
            parsed.rejectedRows.map { it.sourceRowIndex }
        if (!allIndices.containsAll(choices.excludedRows) ||
            parsed.rejectedRows.any { it.sourceRowIndex !in choices.excludedRows }
        ) {
            problem(WorkoutImportIssue.UNRESOLVED_EXCLUSION)
        }
        if (!choices.acknowledgedHistoryEffects) {
            problem(WorkoutImportIssue.UNACKNOWLEDGED_HISTORY_EFFECTS)
        }
        val selected = parsed.rows.filterNot { it.sourceRowIndex in choices.excludedRows }
        val hasMetadata = selected.any { it.diagnostics.isNotEmpty() }
        if (!choices.acknowledgedUnsupportedFields && hasMetadata) {
            problem(WorkoutImportIssue.UNACKNOWLEDGED_METADATA)
        }
        val byIndex = selected.associateBy { it.sourceRowIndex }
        val assignments = choices.sessions.flatMap { it.sourceRows }
        if (choices.sessions.any { it.sourceRows.isEmpty() } ||
            assignments.size != assignments.toSet().size ||
            assignments.toSet() != byIndex.keys
        ) {
            problem(WorkoutImportIssue.INVALID_SESSION_MEMBERSHIP)
            return WorkoutImportPreview(parsed.source, emptyList(), problems)
        }
        val byExercise = catalog.associateBy { it.id }
        if (choices.sessions.any { session ->
                session.sourceRows.map { sourceCandidateKey(parsed.source, byIndex.getValue(it)) }
                    .distinct().size != 1
            }
        ) {
            problem(WorkoutImportIssue.INVALID_SESSION_MEMBERSHIP)
            return WorkoutImportPreview(parsed.source, emptyList(), problems)
        }
        val sourceBuckets = parsed.rows.groupBy { sourceCandidateKey(parsed.source, it) }
        val partitionChoices = choices.sessions.groupBy { session ->
            session.sourceRows.firstOrNull()?.let { byIndex[it] }
                ?.let { sourceCandidateKey(parsed.source, it) }
        }
        val proposedBuckets = sourceBuckets.mapNotNull { (key, sourceRows) ->
            val groups = partitionChoices[key].orEmpty()
            if (groups.isEmpty()) return@mapNotNull null
            val ordered = sourceRows.sortedBy { it.sourceRowIndex }
            val positions = ordered.mapIndexed { index, row -> row.sourceRowIndex to index }.toMap()
            val partition = WorkoutImportPartition(
                rowCount = ordered.size,
                sessions = groups.map { group -> group.sourceRows.map { positions.getValue(it) } },
                excluded = ordered.filter { it.sourceRowIndex in choices.excludedRows }
                    .map { positions.getValue(it.sourceRowIndex) }.toSet()
            ).canonical()
            val bucket = WorkoutImportBucket(
                source = parsed.source,
                candidateKey = key,
                content = canonicalBucketContent(ordered),
                partition = partition
            )
            val previous = buckets.filter { it.source == parsed.source && it.candidateKey == key }
            val bucketChanged = previous.any { stored ->
                stored.content != bucket.content ||
                    stored.partition != bucket.partition ||
                    stored.importedSessionIds.size != groups.size
            }
            if (previous.size > 1 || bucketChanged) {
                problem(WorkoutImportIssue.CHANGED_OR_AMBIGUOUS_SOURCE)
            }
            if (provenance.any { it.source == parsed.source && it.candidateKey == key } &&
                groups.size != 1
            ) {
                problem(WorkoutImportIssue.CHANGED_OR_AMBIGUOUS_SOURCE)
            }
            bucket
        }
        val resolved = choices.sessions.mapNotNull { session ->
            if (!session.confirmedSession || !session.confirmedInferredSetTimes) {
                problem(WorkoutImportIssue.UNCONFIRMED_SESSION)
            }
            val rows = session.sourceRows.mapNotNull { byIndex[it] }.sortedBy { it.sourceRowIndex }
            if (rows.isEmpty()) {
                problem(WorkoutImportIssue.INVALID_SESSION_MEMBERSHIP)
                return@mapNotNull null
            }
            val days = rows.map { it.day }.distinct()
            val titles = rows.map { it.workoutTitle }.distinct()
            if (days.size != 1 || titles.size != 1) {
                // One session is one local day of one source workout; a group that mixes source
                // workouts cannot be identified, let alone matched against the import ledger.
                problem(WorkoutImportIssue.INVALID_SESSION_MEMBERSHIP)
                return@mapNotNull null
            }
            if (session.utcOffsetMinutes !in -1080..1080 ||
                session.dateOnlySecondOfDay?.let { it !in 0..86399 } == true
            ) {
                problem(WorkoutImportIssue.INVALID_TIME)
                return@mapNotNull null
            }
            val date = days.single()
            val day = daysFromCivil(date.year, date.month, date.day)
            val exercises = linkedMapOf<String, Exercise>()
            val sets = rows.mapNotNull rowLoop@{ row ->
                val second = row.timeOfDaySeconds ?: session.dateOnlySecondOfDay
                if (second == null) {
                    problem(WorkoutImportIssue.INVALID_TIME, row.sourceRowIndex)
                    return@rowLoop null
                }
                val choice = choices.exercises[row.exerciseName]
                val exercise = choice?.let { byExercise[it.exerciseId] }
                if (choice == null || exercise == null) {
                    problem(WorkoutImportIssue.MISSING_EXERCISE, row.sourceRowIndex)
                    return@rowLoop null
                }
                if (!choice.confirmedLoadMeaning) {
                    problem(WorkoutImportIssue.UNCONFIRMED_LOAD, row.sourceRowIndex)
                }
                if (row.warmup == null && !choice.unknownWarmupIsWorking) {
                    problem(WorkoutImportIssue.UNKNOWN_WARMUP, row.sourceRowIndex)
                }
                val kilograms = if (choice.useSourceWeight) {
                    row.weight?.let(row.unit::displayToKilograms)
                } else {
                    null
                }
                try {
                    WorkoutLoadPolicy.validate(exercise.loadCapability, choice.loadKind, kilograms)
                } catch (_: WorkoutLoadException) {
                    problem(WorkoutImportIssue.INVALID_LOAD, row.sourceRowIndex)
                    return@rowLoop null
                }
                exercises[exercise.id] = exercise
                WorkoutSet(
                    exerciseId = exercise.id,
                    reps = row.reps,
                    weightKg = kilograms,
                    loadKind = choice.loadKind,
                    performedAtMillis = day * 86_400_000L + second * 1000L -
                        session.utcOffsetMinutes * 60_000L,
                    isWarmup = row.warmup ?: false,
                    durationSeconds = row.durationSeconds,
                    distanceMeters = row.distanceMeters
                )
            }
            if (sets.size != rows.size) return@mapNotNull null
            val candidate = sourceCandidateKey(parsed.source, rows.first())
            val content = canonicalFields(rows.map(::canonicalSourceRow))
            val matches = provenance.filter {
                it.source == parsed.source && it.candidateKey == candidate
            }
            val exact = matches.filter { it.content == content }
            val proposedBucket = proposedBuckets.firstOrNull { it.candidateKey == candidate }
            val bucketMatch = buckets.firstOrNull {
                it.source == parsed.source &&
                    it.candidateKey == candidate &&
                    proposedBucket != null &&
                    it.content == proposedBucket.content &&
                    it.partition == proposedBucket.partition
            }
            val ordinal = partitionChoices[candidate].orEmpty().indexOf(session)
            val skip = bucketMatch?.importedSessionIds?.getOrNull(ordinal)
                ?: if (exact.size == 1) exact.single().importedSessionId else null
            if (exact.size > 1 || (matches.isNotEmpty() && exact.isEmpty())) {
                problem(WorkoutImportIssue.CHANGED_OR_AMBIGUOUS_SOURCE)
            }
            WorkoutImportSession(
                candidateKey = candidate,
                content = content,
                sourceRows = rows.map { it.sourceRowIndex },
                sets = sets,
                exercises = exercises,
                localEpochDay = day,
                utcOffsetMinutes = session.utcOffsetMinutes,
                inferredSetTimes = true,
                skipExistingSessionId = skip
            )
        }
        resolved.forEachIndexed { index, session ->
            val skip = session.skipExistingSessionId
            if (skip != null) {
                // The source is unchanged, so it is never imported twice. Disclose which case it is:
                // history the user removed stays removed rather than silently reappearing.
                val stillPresent = existingSessions.any { it.id == skip }
                notice(
                    if (stillPresent) {
                        WorkoutImportIssue.UNCHANGED_SOURCE_SKIPPED
                    } else {
                        WorkoutImportIssue.REMOVED_HISTORY_SKIPPED
                    },
                    session.sourceRows.firstOrNull()
                )
                return@forEachIndexed
            }
            val overlapsExisting = existingSessions.any {
                session.startedAtMillis <= (it.endedAtMillis ?: Long.MAX_VALUE) &&
                    session.endedAtMillis >= it.startedAtMillis
            }
            val overlapsNew = resolved.take(index).filter { it.skipExistingSessionId == null }.any {
                session.startedAtMillis <= it.endedAtMillis &&
                    session.endedAtMillis >= it.startedAtMillis
            }
            if (overlapsExisting || overlapsNew) problem(WorkoutImportIssue.SESSION_OVERLAP)
        }
        return WorkoutImportPreview(
            source = parsed.source,
            sessions = resolved,
            problems = problems.distinct(),
            notices = notices.distinct(),
            buckets = proposedBuckets
        )
    }
}

/** Length-prefixing prevents delimiters, quotes and newlines in vendor text from colliding. */
internal fun canonicalFields(fields: List<String?>): String = fields.joinToString("") {
    if (it == null) "-1:" else "${it.length}:$it"
}

/**
 * Reads back a [canonicalFields] string. Returns null when a length prefix is missing, is not a
 * non-negative whole number, or runs past the end of the text, so a malformed stored manifest is
 * never partly trusted. A `null` field (`"-1:"`) is not produced by the partition encoding and is
 * reported as malformed rather than decoded.
 */
internal fun decodeCanonicalFields(encoded: String): List<String>? {
    val fields = mutableListOf<String>()
    var index = 0
    while (index < encoded.length) {
        val colon = encoded.indexOf(':', index)
        if (colon < 0) return null
        val lengthText = encoded.substring(index, colon)
        if (lengthText.isEmpty() || lengthText.any { it !in '0'..'9' }) return null
        val length = lengthText.toIntOrNull() ?: return null
        val start = colon + 1
        // Bound the length against the remaining text *before* adding, so an overflowing prefix such
        // as "2147483647:" is malformed rather than wrapping into a negative index and throwing.
        if (length > encoded.length - start) return null
        val end = start + length
        fields += encoded.substring(start, end)
        index = end
    }
    return fields
}

/**
 * The complete canonical content of one source bucket, in source order. Two reads of the same source
 * workout produce the same string, which is what makes a repeat import recognisable. Exposed so the
 * caller preparing a review can tell an unchanged bucket from a changed one before it decides how to
 * seed that bucket's session drafts.
 */
fun canonicalBucketContent(rows: List<WorkoutCsvRow>): String =
    canonicalFields(rows.sortedBy { it.sourceRowIndex }.map(::canonicalSourceRow))

/**
 * Identifies the source workout a set row belongs to: the provider, the row's civil date and the
 * source workout title when the export carries one. A provider without a title field (FitNotes) can
 * only identify a day, so two workouts on that date share a key and a candidate key with different
 * content is reported as changed/ambiguous rather than merged.
 */
fun sourceCandidateKey(source: WorkoutCsvSource, row: WorkoutCsvRow): String =
    canonicalFields(listOf(source.name, row.day.toString(), row.workoutTitle))

private fun canonicalSourceRow(row: WorkoutCsvRow): String = canonicalFields(
    listOf(
        row.exerciseName,
        row.day.toString(),
        row.timeOfDaySeconds?.toString(),
        row.endDay?.toString(),
        row.endTimeOfDaySeconds?.toString(),
        row.workoutTitle,
        row.groupHint,
        row.weight?.toString(),
        row.unit.name,
        canonicalMeasurement(row),
        row.warmup?.toString(),
        canonicalFields(
            row.diagnostics.map { canonicalFields(listOf(it.code.name, it.field, it.value)) }
        )
    )
)

/**
 * The measurement slot of a canonical row. A counted row keeps the bare rep count it has always
 * carried, so a previously imported rep row still matches byte for byte; a timed or distance row is
 * prefixed so it can never collide with a rep count or with another measure.
 */
private fun canonicalMeasurement(row: WorkoutCsvRow): String {
    val reps = row.reps
    val duration = row.durationSeconds
    val distance = row.distanceMeters
    return when {
        reps != null -> reps.toString()
        duration != null -> "duration:$duration"
        distance != null -> "distance:$distance"
        else -> "unmeasured"
    }
}
