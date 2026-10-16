package com.hydrafit.app.feature.settings

import com.hydrafit.app.core.domain.transfer.SourceCivilDate
import com.hydrafit.app.core.domain.transfer.WorkoutCsvDiagnostic
import com.hydrafit.app.core.domain.transfer.WorkoutCsvFailure
import com.hydrafit.app.core.domain.transfer.WorkoutCsvSource
import com.hydrafit.app.core.domain.transfer.WorkoutImportCommitFailure
import com.hydrafit.app.core.domain.transfer.WorkoutImportProblem
import com.hydrafit.app.core.domain.transfer.WorkoutImportResult
import com.hydrafit.app.core.domain.workout.LoadKind

/** Why a chosen file could not be read into a review; the UI maps it to a localized message. */
sealed interface WorkoutImportFailure {
    /** The parser rejected the whole file; [failure] is the typed whole-file category. */
    data class Csv(val failure: WorkoutCsvFailure) : WorkoutImportFailure

    /** Reading or preparing the file failed for a reason that is not a typed CSV condition. */
    data object Unknown : WorkoutImportFailure
}

/** One importable source row, projected for review. [timeText]/[weightText] are null when absent. */
data class WorkoutImportRowUi(
    val sourceRowIndex: Int,
    val exerciseName: String,
    val dayLabel: String,
    val timeText: String?,
    val weightText: String?,
    val unitLabel: String?,
    val reps: Int,
    val warmup: Boolean?,
    val diagnostics: List<WorkoutCsvDiagnostic>,
    val included: Boolean
)

/** A whole-file row the parser could not turn into a set; the user must exclude it before import. */
data class RejectedImportRowUi(
    val sourceRowIndex: Int,
    val diagnostics: List<WorkoutCsvDiagnostic>,
    val excluded: Boolean
)

/** How the catalog matched a source exercise name, independent of the chosen interpretation. */
sealed interface MappingState {
    data class Suggested(val exerciseId: String, val label: String) : MappingState

    data class Ambiguous(val labels: List<String>) : MappingState

    data object Unmatched : MappingState
}

/** A catalog exercise offered as a mapping target. */
data class MappingCandidateUi(val exerciseId: String, val label: String)

/**
 * Identifies one source workout draft: its civil day plus the source workout title when the provider
 * carries one. Two workouts on the same day stay separate; a provider without titles groups by day.
 */
data class SessionKey(val day: SourceCivilDate, val workoutTitle: String?)

/** One distinct source exercise name and the interpretation the user has (or has not) chosen. */
data class MappingUi(
    val exerciseName: String,
    val occurrenceCount: Int,
    val state: MappingState,
    val chosenExerciseId: String?,
    val loadKind: LoadKind,
    val useSourceWeight: Boolean,
    val confirmedLoadMeaning: Boolean,
    val unknownWarmupIsWorking: Boolean
)

/** One source civil day treated as a single session, with the user's time/session decisions. */
data class SessionDraftUi(
    val key: SessionKey,
    val dayLabel: String,
    val workoutTitle: String?,
    val rowIndexes: List<Int>,
    /** The raw UTC-offset text exactly as typed; [utcOffsetMinutes] is the last value it parsed to. */
    val utcOffsetText: String,
    val utcOffsetMinutes: Int,
    val dateOnlySecondOfDay: Int?,
    val confirmedSession: Boolean,
    val confirmedInferredSetTimes: Boolean
)

/**
 * A blocking review problem: one the domain preview reported, or one the review raised itself. The
 * UI resolves each to a localized message; the domain problem types are reused unchanged.
 */
sealed interface ReviewProblem {
    val sourceRow: Int?

    data class Domain(val problem: WorkoutImportProblem) : ReviewProblem {
        override val sourceRow: Int? get() = problem.sourceRow
    }

    /** The typed session UTC offset is not a whole number of minutes in -1080..1080. */
    data class InvalidOffset(val sessionKey: SessionKey, override val sourceRow: Int?) :
        ReviewProblem
}

/** The full review shown before an import: source rows plus every derived choice and disclosure. */
data class WorkoutImportReview(
    val rows: List<WorkoutImportRowUi>,
    val mappings: List<MappingUi>,
    val sessions: List<SessionDraftUi>,
    val rejectedRows: List<RejectedImportRowUi>,
    val excludedRows: Set<Int>,
    val catalogOptions: List<MappingCandidateUi>,
    val acknowledgedUnsupportedFields: Boolean,
    val acknowledgedHistoryEffects: Boolean,
    val problems: List<ReviewProblem>,
    val notices: List<WorkoutImportProblem>,
    val canImport: Boolean,
    val commitFailure: WorkoutImportCommitFailure? = null
)

/** The screen's phase: no file, an editable review, a finished import, or a read/parse failure. */
sealed interface WorkoutImportPhase {
    data object Idle : WorkoutImportPhase

    data class Reviewing(val review: WorkoutImportReview) : WorkoutImportPhase

    data class Imported(val result: WorkoutImportResult) : WorkoutImportPhase

    data class Failed(val failure: WorkoutImportFailure) : WorkoutImportPhase
}

data class WorkoutImportUiState(
    val isSupported: Boolean = true,
    val inProgress: Boolean = false,
    val provider: WorkoutCsvSource = WorkoutCsvSource.FITNOTES,
    val phase: WorkoutImportPhase = WorkoutImportPhase.Idle
)

/** Stable identities for the import controls; technical metadata, never spoken labels. */
object WorkoutImportTestTags {
    const val PROVIDER_FITNOTES = "workout_import_provider_fitnotes"
    const val PROVIDER_STRONG = "workout_import_provider_strong"
    const val PROVIDER_HEVY = "workout_import_provider_hevy"
    const val CHOOSE_FILE = "workout_import_choose_file"
    const val APPLY = "workout_import_apply"
    const val SUMMARY = "workout_import_summary"
    const val PROBLEMS = "workout_import_problems"
    const val NOTICES = "workout_import_notices"
    const val RESET = "workout_import_reset"
}
