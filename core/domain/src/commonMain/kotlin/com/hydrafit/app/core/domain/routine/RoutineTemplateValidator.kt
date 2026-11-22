package com.hydrafit.app.core.domain.routine

import com.hydrafit.app.core.domain.workout.LoadKind

/**
 * Why a routine draft is not usable. The UI shows its own localized sentence per reason;
 * [RoutineTemplateException.message] stays English for logs and for any failure without a reason.
 */
enum class RoutineTemplateFailure {
    ROUTINE_NAME_BLANK,
    ROUTINE_NEEDS_WORKOUT,
    WORKOUT_NAME_BLANK,
    ROUTINE_NOT_FOUND,
    ROUTINE_NOT_SAVED,
    ROUTINE_IN_USE,
    SLOT_NEEDS_EXERCISE,
    SLOT_NEEDS_ONE_MEASUREMENT,
    SETS_OUT_OF_RANGE,
    REPS_OUT_OF_RANGE,
    DISTANCE_OUT_OF_RANGE,
    FORCE_OUT_OF_RANGE,
    WEIGHT_NOT_VALID,
    WEIGHT_NOT_A_NUMBER,
    DISTANCE_NOT_A_NUMBER,
    DISTANCE_NOT_POSITIVE,
    FORCE_NOT_A_NUMBER,
    FORCE_NOT_POSITIVE,
    FORCE_CANNOT_CARRY_LOAD,
    BODYWEIGHT_CANNOT_CARRY_LOAD,
    SUPERSET_NOT_VALID,
    CLUSTER_NEEDS_REPS,
    CLUSTER_NEEDS_EXTERNAL_LOAD,
    CLUSTER_NOT_VALID,
    ORDERED_NEEDS_REPS,
    ORDERED_NOT_VALID,
    ORDERED_TARGET_COUNT,
    ORDERED_KEEP_LOAD_KIND
}

/** A routine draft the user submitted is not a usable routine. */
class RoutineTemplateException(message: String, val reason: RoutineTemplateFailure? = null) :
    IllegalArgumentException(message)

/**
 * Validates and normalizes a routine draft before it is persisted: a non-blank name, at least one
 * workout, ordered positions with no gaps, and every slot within the shared set/rep bounds. A
 * workout may be empty while the user is still building it; a slot may not carry invalid work.
 */
internal object RoutineTemplateValidator {
    fun validate(template: RoutineTemplate): RoutineTemplate {
        val name = template.name.trim()
        if (name.isEmpty()) {
            throw RoutineTemplateException(
                "Routine name must not be blank",
                RoutineTemplateFailure.ROUTINE_NAME_BLANK
            )
        }
        if (template.workouts.isEmpty()) {
            throw RoutineTemplateException(
                "A routine needs at least one workout",
                RoutineTemplateFailure.ROUTINE_NEEDS_WORKOUT
            )
        }
        return template.copy(
            name = name,
            workouts = template.workouts.mapIndexed { index, workout ->
                val workoutName = workout.name.trim()
                if (workoutName.isEmpty()) {
                    throw RoutineTemplateException(
                        "Workout name must not be blank",
                        RoutineTemplateFailure.WORKOUT_NAME_BLANK
                    )
                }
                try {
                    validateSupersets(workout.entries.map { it.exerciseId to it.superset })
                } catch (error: IllegalArgumentException) {
                    throw RoutineTemplateException(
                        error.message ?: "Invalid superset",
                        RoutineTemplateFailure.SUPERSET_NOT_VALID
                    )
                }
                workout.copy(
                    position = index,
                    name = workoutName,
                    entries = workout.entries.mapIndexed { entryIndex, entry ->
                        validateEntry(entry).copy(position = entryIndex)
                    }
                )
            }
        )
    }

    private fun validateEntry(entry: RoutineEntry): RoutineEntry {
        if (entry.exerciseId.isBlank()) {
            throw RoutineTemplateException(
                "A slot needs an exercise",
                RoutineTemplateFailure.SLOT_NEEDS_EXERCISE
            )
        }
        PrescriptionBounds.validate(
            entry.sets,
            entry.reps,
            entry.durationSeconds,
            entry.weightKg,
            entry.distanceMeters,
            entry.forceNewtons
        )
        // Capability compatibility is checked at activation (which has the catalog); here only the
        // shape that holds regardless of capability is enforced.
        if (entry.loadKind == LoadKind.BODYWEIGHT && entry.weightKg != null) {
            throw RoutineTemplateException(
                "Bodyweight work cannot record a numeric load",
                RoutineTemplateFailure.BODYWEIGHT_CANNOT_CARRY_LOAD
            )
        }
        entry.cluster?.let { cluster ->
            // A cluster is rep-based external-load work. A timed, distance or force measure, or any
            // other load kind, describes a slot the cluster cannot prescribe.
            if (entry.reps == null ||
                entry.durationSeconds != null ||
                entry.distanceMeters != null ||
                entry.forceNewtons != null
            ) {
                throw RoutineTemplateException(
                    "A cluster prescription requires repetitions",
                    RoutineTemplateFailure.CLUSTER_NEEDS_REPS
                )
            }
            if (entry.loadKind != LoadKind.EXTERNAL) {
                throw RoutineTemplateException(
                    "A cluster prescription requires an external load",
                    RoutineTemplateFailure.CLUSTER_NEEDS_EXTERNAL_LOAD
                )
            }
            try {
                cluster.requireValid()
            } catch (error: IllegalArgumentException) {
                throw RoutineTemplateException(
                    error.message ?: "A cluster prescription is not valid",
                    RoutineTemplateFailure.CLUSTER_NOT_VALID
                )
            }
        }
        entry.setTargets?.let { targets ->
            // Ordered targets describe rep work: a slot measured by seconds, metres or newtons
            // cannot perform them, so it must not carry them.
            if (entry.reps == null) {
                throw RoutineTemplateException(
                    "An ordered prescription requires repetitions",
                    RoutineTemplateFailure.ORDERED_NEEDS_REPS
                )
            }
            try {
                targets.requireValid()
            } catch (error: IllegalArgumentException) {
                throw RoutineTemplateException(
                    error.message ?: "An ordered prescription is not valid",
                    RoutineTemplateFailure.ORDERED_NOT_VALID
                )
            }
            // The slot's set count and its ordered prescription are one value: a mismatch would let
            // logging address the wrong slots.
            if (targets.sets != entry.sets) {
                throw RoutineTemplateException(
                    "An ordered prescription must have one target per prescribed set",
                    RoutineTemplateFailure.ORDERED_TARGET_COUNT
                )
            }
            if (targets.targets.any { it.loadKind != entry.loadKind }) {
                throw RoutineTemplateException(
                    "An ordered prescription must keep the slot's own load kind",
                    RoutineTemplateFailure.ORDERED_KEEP_LOAD_KIND
                )
            }
        }
        return entry
    }
}
