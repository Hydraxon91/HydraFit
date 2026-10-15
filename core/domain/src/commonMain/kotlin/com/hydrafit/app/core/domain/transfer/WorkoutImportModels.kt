package com.hydrafit.app.core.domain.transfer

import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutSet

/** Every interpretation is an explicit preview choice, never inferred from an exercise name. */
data class WorkoutImportExerciseChoice(
    val exerciseId: String,
    val loadKind: LoadKind,
    val useSourceWeight: Boolean,
    val confirmedLoadMeaning: Boolean,
    val unknownWarmupIsWorking: Boolean = false
)

/** Source row membership is explicit, including separate workouts on the same civil day. */
data class WorkoutImportSessionChoice(
    val sourceRows: List<Int>,
    val utcOffsetMinutes: Int,
    val confirmedSession: Boolean,
    val confirmedInferredSetTimes: Boolean,
    val dateOnlySecondOfDay: Int? = null
)

data class WorkoutImportChoices(
    val exercises: Map<String, WorkoutImportExerciseChoice>,
    val sessions: List<WorkoutImportSessionChoice>,
    val excludedRows: Set<Int> = emptySet(),
    val acknowledgedUnsupportedFields: Boolean = false,
    val acknowledgedHistoryEffects: Boolean = false
)

enum class WorkoutImportIssue {
    UNRESOLVED_EXCLUSION,
    UNACKNOWLEDGED_METADATA,
    UNACKNOWLEDGED_HISTORY_EFFECTS,
    INVALID_SESSION_MEMBERSHIP,
    UNCONFIRMED_SESSION,
    INVALID_TIME,
    MISSING_EXERCISE,
    UNCONFIRMED_LOAD,
    INVALID_LOAD,
    UNKNOWN_WARMUP,
    SESSION_OVERLAP,
    CHANGED_OR_AMBIGUOUS_SOURCE,

    /** Non-blocking: this source workout was already imported and is skipped. */
    UNCHANGED_SOURCE_SKIPPED,

    /** Non-blocking: this source workout was imported, then locally deleted, and stays deleted. */
    REMOVED_HISTORY_SKIPPED
}

data class WorkoutImportProblem(val issue: WorkoutImportIssue, val sourceRow: Int? = null)

/** How a commit refused a preview that was already shown to the user. */
enum class WorkoutImportCommitFailure {
    /** Another import recorded this source session between the preview and the commit. */
    SOURCE_ALREADY_IMPORTED,

    /** The recorded provenance for this source no longer matches what the preview showed. */
    SOURCE_CHANGED,

    /** The preview carries no session to write. */
    NOTHING_TO_IMPORT
}

class WorkoutImportException(val failure: WorkoutImportCommitFailure, message: String) :
    IllegalStateException(message)

/** Canonical source content is independent of filenames, export row offsets and local edits. */
data class WorkoutImportProvenance(
    val source: WorkoutCsvSource,
    val candidateKey: String,
    val content: String,
    val importedSessionId: String
)

data class WorkoutImportSession(
    val candidateKey: String,
    val content: String,
    val sourceRows: List<Int>,
    val sets: List<WorkoutSet>,
    val exercises: Map<String, Exercise>,
    val localEpochDay: Long,
    val utcOffsetMinutes: Int,
    val inferredSetTimes: Boolean,
    val skipExistingSessionId: String? = null
) {
    val startedAtMillis: Long get() = sets.minOf { it.performedAtMillis }
    val endedAtMillis: Long get() = sets.maxOf { it.performedAtMillis }
}

data class WorkoutImportPreview(
    val source: WorkoutCsvSource,
    val sessions: List<WorkoutImportSession>,
    val problems: List<WorkoutImportProblem>,
    /**
     * Non-blocking disclosures: a session that is skipped because its source was already imported
     * (whether or not the local session still exists). These never stop the import.
     */
    val notices: List<WorkoutImportProblem> = emptyList()
) {
    /** No blocking problem and at least one session to write; a fully skipped preview is not importable. */
    val canImport: Boolean
        get() = problems.isEmpty() && sessions.any { it.skipExistingSessionId == null }
}

/** Implementations revalidate the preview and persist all selected sessions in one transaction. */
interface WorkoutImportRepository {
    suspend fun provenance(
        source: WorkoutCsvSource,
        candidateKeys: Set<String>
    ): List<WorkoutImportProvenance>
    suspend fun commit(preview: WorkoutImportPreview): WorkoutImportResult
}

data class WorkoutImportResult(
    val importedSessions: Int,
    val importedSets: Int,
    val skippedSessions: Int
)
