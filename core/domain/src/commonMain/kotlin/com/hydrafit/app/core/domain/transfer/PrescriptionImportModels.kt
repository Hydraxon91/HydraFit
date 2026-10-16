package com.hydrafit.app.core.domain.transfer

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.routine.RoutineTemplate

/**
 * What the user decided for one referenced source exercise identity.
 *
 * `MAP_TO` points at an existing local exercise and `CREATE` accepts the document's own custom
 * definition under its own fresh local identity; an identity the receiving catalog already holds
 * needs no choice at all. Nothing is chosen automatically: the preview never overwrites a definition
 * by id or name and never silently substitutes equipment. Accepting a missing-equipment gap is a
 * *separate* decision ([PrescriptionImportChoices.acceptedEquipmentGaps]) so it can combine with
 * either choice — most importantly with `CREATE`, whose new definition may itself need equipment the
 * active profile does not select.
 */
sealed interface PrescriptionIdentityChoice {
    /** Point the slots at an existing local exercise instead. */
    data class MapTo(val localExerciseId: String) : PrescriptionIdentityChoice

    /** Create the document's custom definition locally (never overwrites an existing row). */
    data object Create : PrescriptionIdentityChoice
}

/**
 * The user's decisions for one import. Anything not decided blocks the import, so no mapping,
 * definition or equipment gap is resolved silently. Weights are the document's own recorded choice:
 * a weights-free document simply imports weights-free.
 *
 * [acceptedEquipmentGaps] holds the source exercise ids whose missing equipment the user accepted. It
 * is independent of [identities], so a definition can be created and its equipment gap accepted in
 * the same import.
 */
data class PrescriptionImportChoices(
    val identities: Map<String, PrescriptionIdentityChoice> = emptyMap(),
    val acceptedEquipmentGaps: Set<String> = emptySet()
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

    /**
     * A local exercise already carries this custom id but is a *different* exercise. Local custom ids
     * are stable per install, not globally, so an id match alone is not proof of the same exercise;
     * the definition must be compared, and a mismatch needs an explicit decision.
     */
    data class IdCollision(val local: Exercise, val definition: PrescriptionExerciseRecord) :
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
    INVALID_CHOICE,

    /** A document definition and a local exercise share an id but are different exercises. */
    IDENTITY_CONFLICT,

    /** A routine could not be turned into a valid local routine (a dropped workout or slot). */
    INVALID_ROUTINE
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
    val choice: PrescriptionIdentityChoice?,
    /** Whether the user accepted [missingEquipment]; independent of [choice]. */
    val acceptedEquipmentGap: Boolean
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

    /**
     * The preview still carries unresolved problems, so committing it would import a partial plan.
     * A caller that ignores [PrescriptionImportPreview.canImport] must be refused.
     */
    PREVIEW_NOT_IMPORTABLE,

    /** A definition this preview would create now exists locally, so it must not be overwritten. */
    IDENTITY_ALREADY_EXISTS
}

class PrescriptionImportException(val failure: PrescriptionImportCommitFailure, message: String) :
    IllegalStateException(message)
