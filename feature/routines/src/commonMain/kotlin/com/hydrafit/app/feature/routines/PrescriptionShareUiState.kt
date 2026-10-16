package com.hydrafit.app.feature.routines

import com.hydrafit.app.core.domain.transfer.PrescriptionFailure
import com.hydrafit.app.core.domain.transfer.PrescriptionImportCommitFailure
import com.hydrafit.app.core.domain.transfer.PrescriptionImportPreview

/** Per-routine export: which routine, the recorded weights choice, and the last result. */
data class RoutineExportState(
    val routineId: Long,
    val routineName: String,
    val includeWeights: Boolean = false,
    val inProgress: Boolean = false,
    val status: PrescriptionExportStatus? = null
)

/** Why a prescription export could not be written; the UI maps each to a localized message. */
enum class PrescriptionExportFailure {
    TOO_LARGE,
    OVER_LIMIT,
    IO
}

sealed interface PrescriptionExportStatus {
    data object Saved : PrescriptionExportStatus

    data class Failed(val failure: PrescriptionExportFailure) : PrescriptionExportStatus
}

/** One catalog exercise offered as a mapping target while resolving an import. */
data class PrescriptionExerciseOption(val id: String, val name: String)

/**
 * The resolved import awaiting explicit choices. [preview] is rebuilt after every choice so it is
 * never stale; [commitFailure] is set when the repository refused an otherwise importable preview.
 */
data class PrescriptionImportReview(
    val preview: PrescriptionImportPreview,
    val commitFailure: PrescriptionImportCommitFailure? = null
)

/** The import phase: nothing chosen, an editable review, a finished import, or a failure. */
sealed interface PrescriptionImportPhase {
    data object Idle : PrescriptionImportPhase

    data class Reviewing(val review: PrescriptionImportReview) : PrescriptionImportPhase

    data class Imported(val importedRoutines: Int, val createdExercises: Int) :
        PrescriptionImportPhase

    /** [failure] is the typed document failure, or null when reading the chosen file failed. */
    data class Failed(val failure: PrescriptionFailure?) : PrescriptionImportPhase
}

data class PrescriptionShareUiState(
    val isSupported: Boolean = true,
    /** The open per-routine export dialog, or null. */
    val export: RoutineExportState? = null,
    val importInProgress: Boolean = false,
    val importPhase: PrescriptionImportPhase = PrescriptionImportPhase.Idle,
    /** Catalog exercises offered as mapping targets, sorted by name. */
    val exercises: List<PrescriptionExerciseOption> = emptyList()
)

/** Stable identities for the transfer controls; technical metadata, never spoken labels. */
object PrescriptionShareTestTags {
    const val EXPORT_ROUTINE = "routines_prescription_export"
    const val EXPORT_WEIGHTS = "routines_prescription_export_weights"
    const val EXPORT_CONFIRM = "routines_prescription_export_confirm"
    const val EXPORT_CANCEL = "routines_prescription_export_cancel"
    const val IMPORT_CHOOSE = "routines_prescription_import"
    const val IMPORT_CONFIRM = "routines_prescription_import_confirm"
    const val IMPORT_CANCEL = "routines_prescription_import_cancel"
    const val IMPORT_MAP_CHOICE = "routines_prescription_import_map"
    const val IMPORT_CREATE_CHOICE = "routines_prescription_import_create"
    const val IMPORT_ACCEPT_EQUIPMENT = "routines_prescription_import_accept_equipment"
    const val IMPORT_SUMMARY = "routines_prescription_import_summary"
    const val IMPORT_PROBLEMS = "routines_prescription_import_problems"
}
