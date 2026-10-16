package com.hydrafit.app.core.domain.transfer

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.routine.RoutineTemplate

/**
 * What the user decided for one referenced source exercise identity.
 *
 * `REUSE` and `MAP_TO` point at an existing local exercise; `CREATE` accepts the document's own custom
 * definition under its own fresh local identity. Nothing is chosen automatically: the preview never
 * overwrites a definition by id or name and never silently substitutes equipment.
 */
sealed interface PrescriptionIdentityChoice {
    /** Keep the document's identity because the local catalog already has it. */
    data object Reuse : PrescriptionIdentityChoice

    /** Point the slots at an existing local exercise instead. */
    data class MapTo(val localExerciseId: String) : PrescriptionIdentityChoice

    /** Create the document's custom definition locally (never overwrites an existing row). */
    data object Create : PrescriptionIdentityChoice

    /** Accept that the resolved exercise needs equipment this profile does not have. */
    data object AcceptEquipmentGap : PrescriptionIdentityChoice
}

/**
 * The user's decisions for one import. Anything not decided blocks the import, so no mapping,
 * definition or equipment gap is resolved silently. Weights are the document's own recorded choice:
 * a weights-free document simply imports weights-free.
 */
data class PrescriptionImportChoices(
    val identities: Map<String, PrescriptionIdentityChoice> = emptyMap()
)

/** How a source identity resolved against the receiving catalog and the document's definitions. */
sealed interface PrescriptionResolution {
    /** The local catalog already holds this exact identity. */
    data class Existing(val exercise: Exercise) : PrescriptionResolution

    /** The document declares a custom definition that must be created locally. */
    data class Create(val definition: PrescriptionExerciseRecord) : PrescriptionResolution

    /** A local exercise already uses the declared definition's name, so creating it would collide. */
    data class NameCollision(val local: Exercise, val definition: PrescriptionExerciseRecord) :
        PrescriptionResolution

    /** Nothing local matches and the document declares no definition for this id. */
    data object Unknown : PrescriptionResolution
}

enum class PrescriptionImportIssue {
    NO_ROUTINES,
    UNDECIDED_IDENTITY,
    NAME_COLLISION,
    UNKNOWN_IDENTITY,
    EQUIPMENT_GAP,
    INVALID_CHOICE
}

data class PrescriptionImportProblem(
    val issue: PrescriptionImportIssue,
    val exerciseId: String? = null
)

/** One referenced identity with the catalog state the user is deciding about. */
data class PrescriptionIdentityOption(
    val sourceExerciseId: String,
    val sourceName: String,
    val resolution: PrescriptionResolution,
    val usedByRoutineCount: Int,
    /** Equipment the resolved exercise needs that the active profile does not select. */
    val missingEquipment: Set<EquipmentTag>,
    val choice: PrescriptionIdentityChoice?
)

/** The resolved plan: routines in order, plus only the definitions that must be created locally. */
data class PrescriptionImportPreview(
    val document: PrescriptionDocument,
    val identities: List<PrescriptionIdentityOption>,
    val routines: List<RoutineTemplate>,
    val customExercisesToCreate: List<Exercise>,
    val problems: List<PrescriptionImportProblem>
) {
    val canImport: Boolean get() = problems.isEmpty() && routines.isNotEmpty()
}

/** Appends the approved routines and creates the approved definitions in one transaction. */
interface PrescriptionImportRepository {
    suspend fun importPrescriptions(preview: PrescriptionImportPreview): PrescriptionImportResult
}

data class PrescriptionImportResult(val importedRoutines: Int, val createdExercises: Int)

/** How a commit refused an approved preview. */
enum class PrescriptionImportCommitFailure {
    /** The preview carried nothing to write. */
    NOTHING_TO_IMPORT,

    /** A definition this preview would create now exists locally, so it must not be overwritten. */
    IDENTITY_ALREADY_EXISTS
}

class PrescriptionImportException(val failure: PrescriptionImportCommitFailure, message: String) :
    IllegalStateException(message)
