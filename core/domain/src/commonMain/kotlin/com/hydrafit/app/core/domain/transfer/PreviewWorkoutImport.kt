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
        existingSessions: List<WorkoutSession> = emptyList()
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
        if (assignments.size != assignments.toSet().size || assignments.toSet() != byIndex.keys) {
            problem(WorkoutImportIssue.INVALID_SESSION_MEMBERSHIP)
            return WorkoutImportPreview(parsed.source, emptyList(), problems)
        }
        val byExercise = catalog.associateBy { it.id }
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
            if (days.size != 1 ||
                session.utcOffsetMinutes !in -1080..1080 ||
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
                    isWarmup = row.warmup ?: false
                )
            }
            if (sets.size != rows.size) return@mapNotNull null
            val candidate = sourceCandidateKey(parsed.source, rows.first())
            val content = canonicalFields(rows.map(::canonicalSourceRow))
            val matches = provenance.filter {
                it.source == parsed.source && it.candidateKey == candidate
            }
            val exact = matches.filter { it.content == content }
            val skip = if (exact.size == 1) exact.single().importedSessionId else null
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
            val repeatedSource = resolved.take(index).any {
                it.candidateKey == session.candidateKey && it.content == session.content
            }
            if (repeatedSource) {
                problem(WorkoutImportIssue.CHANGED_OR_AMBIGUOUS_SOURCE)
            }
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
            notices = notices.distinct()
        )
    }
}

/** Length-prefixing prevents delimiters, quotes and newlines in vendor text from colliding. */
internal fun canonicalFields(fields: List<String?>): String = fields.joinToString("") {
    if (it == null) "-1:" else "${it.length}:$it"
}

internal fun sourceCandidateKey(source: WorkoutCsvSource, row: WorkoutCsvRow): String =
    canonicalFields(listOf(source.name, row.day.toString()))

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
        row.reps.toString(),
        row.warmup?.toString(),
        canonicalFields(
            row.diagnostics.map { canonicalFields(listOf(it.code.name, it.field, it.value)) }
        )
    )
)
