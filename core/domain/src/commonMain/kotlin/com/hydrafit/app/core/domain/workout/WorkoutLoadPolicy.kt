package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability

/** A load cannot be recorded for an exercise under its capability. */
class WorkoutLoadException(message: String) : IllegalArgumentException(message)

/**
 * The single place that maps an exercise's load capability to the [LoadKind] a prescription or
 * performed set may carry. Pure: it has no ports and touches no storage, so every consumer applies
 * the same rule.
 */
object WorkoutLoadPolicy {

    /** True when automatic Epley-based weight suggestions may be generated for this capability. */
    fun allowsAutomaticLoad(capability: ExerciseLoadCapability): Boolean =
        capability == ExerciseLoadCapability.EXTERNAL

    /**
     * A recorded numeric load may seed the baseline/progression only when the exercise is external
     * today. This is the approved read-time compatibility exception for historical rows; it never
     * rewrites their stored meaning.
     */
    fun legacyPlannerEligible(capability: ExerciseLoadCapability): Boolean =
        capability == ExerciseLoadCapability.EXTERNAL

    /** The load kind a fresh prescription defaults to when the user has not chosen one. */
    fun defaultKind(capability: ExerciseLoadCapability): LoadKind = when (capability) {
        ExerciseLoadCapability.EXTERNAL -> LoadKind.EXTERNAL
        ExerciseLoadCapability.BODYWEIGHT_ONLY,
        ExerciseLoadCapability.BODYWEIGHT_ADDABLE -> LoadKind.BODYWEIGHT
        ExerciseLoadCapability.UNSPECIFIED -> LoadKind.LEGACY_UNSPECIFIED
    }

    /** True when a new write may carry [kind] for an exercise with [capability]. */
    fun permits(capability: ExerciseLoadCapability, kind: LoadKind): Boolean = when (capability) {
        ExerciseLoadCapability.EXTERNAL ->
            kind == LoadKind.EXTERNAL || kind == LoadKind.LEGACY_UNSPECIFIED
        ExerciseLoadCapability.BODYWEIGHT_ONLY ->
            kind == LoadKind.BODYWEIGHT || kind == LoadKind.LEGACY_UNSPECIFIED
        ExerciseLoadCapability.BODYWEIGHT_ADDABLE ->
            kind == LoadKind.BODYWEIGHT ||
                kind == LoadKind.ADDED ||
                kind == LoadKind.LEGACY_UNSPECIFIED
        ExerciseLoadCapability.UNSPECIFIED -> kind == LoadKind.LEGACY_UNSPECIFIED
    }

    /**
     * Shape-only validation that needs no capability: bodyweight work carries no number, and a
     * numeric load is finite and non-negative. Used at the persistence boundary, where the source
     * authority (current catalog vs a frozen snapshot) is already settled.
     */
    fun validateShape(kind: LoadKind, weightKg: Double?) {
        when (kind) {
            LoadKind.BODYWEIGHT -> if (weightKg != null) {
                throw WorkoutLoadException("Bodyweight work cannot record a numeric load")
            }
            LoadKind.EXTERNAL, LoadKind.ADDED, LoadKind.LEGACY_UNSPECIFIED ->
                if (weightKg != null && (!weightKg.isFinite() || weightKg < 0.0)) {
                    throw WorkoutLoadException("Weight must be zero or positive")
                }
        }
    }

    /** Throws [WorkoutLoadException] when [kind]/[weightKg] is not valid for [capability]. */
    fun validate(capability: ExerciseLoadCapability, kind: LoadKind, weightKg: Double?) {
        if (!permits(capability, kind)) {
            throw WorkoutLoadException("$kind load is not allowed for a $capability exercise")
        }
        validateShape(kind, weightKg)
    }

    /**
     * True when a numeric weight may enter load-derived calculations: an external measurement, or a
     * legacy number on an exercise that is external today.
     */
    fun contributesToLoadMath(capability: ExerciseLoadCapability, kind: LoadKind): Boolean =
        when (kind) {
            LoadKind.EXTERNAL -> true
            LoadKind.LEGACY_UNSPECIFIED -> legacyPlannerEligible(capability)
            LoadKind.BODYWEIGHT, LoadKind.ADDED -> false
        }

    /**
     * Reconciles a requested load against a capability at a boundary that must never store an invalid
     * prescription (activation, occurrence re-prescription). A permitted load is kept unchanged; an
     * incompatible one falls back to the capability's default kind with no number. This is only for
     * snapping an editable source onto the current catalog — it never rewrites stored history.
     */
    fun reconcile(
        capability: ExerciseLoadCapability,
        kind: LoadKind,
        weightKg: Double?
    ): Pair<LoadKind, Double?> =
        if (runCatching { validate(capability, kind, weightKg) }.isSuccess) {
            kind to weightKg
        } else {
            defaultKind(capability) to null
        }
}
