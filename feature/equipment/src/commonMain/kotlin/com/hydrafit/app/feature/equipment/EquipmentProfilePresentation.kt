package com.hydrafit.app.feature.equipment

import com.hydrafit.app.core.domain.equipment.EquipmentStation
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileState
import com.hydrafit.app.core.userdata.equipment.StaleEquipmentProfileException

/**
 * One row in the equipment-profile list: identity plus which profile the writes target.
 *
 * Profile identities are global. Availability, ceilings and loading configuration belong to the
 * profile, so every editor row names the profile it was opened against.
 */
data class EquipmentProfileRow(val id: Long, val name: String, val isActive: Boolean)

/**
 * The observed equipment-profile state projected for presentation.
 *
 * [selected], [maxWeightsKg] and [loadingInventory] all come from the same active-profile snapshot,
 * so a selection toggle and its ceilings are never mixed across two revisions.
 */
data class EquipmentProfileSummary(
    val activeId: Long,
    val activeName: String,
    val rows: List<EquipmentProfileRow>,
    val selected: Set<EquipmentTag>,
    val maxWeightsKg: Map<EquipmentTag, Double>,
    val loadingInventory: LoadingInventory,
    val stations: List<EquipmentStation> = emptyList()
) {
    /** A profile deletion is refused while it would remove the last profile. */
    val canDelete: Boolean
        get() = rows.size > 1

    /** Replacement choices for deleting the active profile; never the deleted profile itself. */
    fun replacementCandidates(profileId: Long): List<EquipmentProfileRow> =
        rows.filter { it.id != profileId }
}

/** Null when the emitted context names a profile the snapshot does not contain. */
fun EquipmentProfileState.toSummary(): EquipmentProfileSummary? {
    val active = profiles.firstOrNull { it.id == context.profileId } ?: return null
    return EquipmentProfileSummary(
        activeId = active.id,
        activeName = active.name,
        rows = profiles.map { EquipmentProfileRow(it.id, it.name, it.id == active.id) },
        selected = active.selected,
        maxWeightsKg = active.maxWeightsKg,
        loadingInventory = active.loadingInventory,
        stations = active.stations
    )
}

enum class EquipmentProfileEditorMode {
    /** Create an empty profile that keeps nothing from the current one. */
    CREATE,

    /** Create a profile that starts from another profile's whole configuration. */
    COPY,

    /** Rename the active profile; never another profile's row. */
    RENAME
}

/**
 * Typed, localized profile-editor failures. Raw repository or exception messages never reach the UI.
 */
enum class EquipmentProfileFailure {
    NAME_REQUIRED,
    NAME_TOO_LONG,
    LAST_PROFILE,
    REPLACEMENT_REQUIRED,
    STALE,
    FAILED
}

/** The profile name dialog: creation, copy-source or rename of the active profile. */
data class EquipmentProfileEditorState(
    val mode: EquipmentProfileEditorMode? = null,
    /** Identifies one open draft so a late save result cannot close a newer draft. */
    val token: Long = 0L,
    /** The copy source for [EquipmentProfileEditorMode.COPY] or the rename target. */
    val targetProfileId: Long? = null,
    val name: String = "",
    val isSaving: Boolean = false,
    val failure: EquipmentProfileFailure? = null
) {
    val isOpen: Boolean
        get() = mode != null

    /** One submission per open draft: a pending save cannot be doubled. */
    val canSave: Boolean
        get() = mode != null && !isSaving && name.isNotBlank()
}

/** The visible confirmation for a profile deletion, including the active-profile replacement. */
data class EquipmentProfileDeleteState(
    val profileId: Long,
    val name: String,
    /** Deleting the active profile needs an explicit replacement; other profiles do not. */
    val requiresReplacement: Boolean,
    val candidates: List<EquipmentProfileRow> = emptyList(),
    val replacementId: Long? = null,
    val isDeleting: Boolean = false,
    val failure: EquipmentProfileFailure? = null
) {
    val canConfirm: Boolean
        get() = !isDeleting && (!requiresReplacement || replacementId != null)
}

/** True when this exception means another writer changed the active profile first. */
internal fun Throwable.isStaleProfileContext(): Boolean = this is StaleEquipmentProfileException
