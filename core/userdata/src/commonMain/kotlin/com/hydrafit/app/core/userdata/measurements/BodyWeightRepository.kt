package com.hydrafit.app.core.userdata.measurements

import kotlinx.coroutines.flow.Flow

/**
 * One measured body weight on a local civil day. Weights are canonical kilograms; the day is the
 * local epoch-day index that buckets the measurement to exactly one civil date.
 */
data class BodyWeightMeasurement(val localEpochDay: Long, val weightKg: Double)

/**
 * The user's body-weight history and optional current goal. Shared user data: features read and
 * write it through this port, never the database directly.
 *
 * Rules enforced here, not by callers: weights must be finite and positive; past and today are
 * allowed but future days are rejected against the injected clock; a second measurement for a day
 * requires an explicit [add] with `replace = true`; moving an entry onto an occupied day fails
 * without overwriting the occupant.
 */
interface BodyWeightRepository {
    fun observeMeasurements(): Flow<List<BodyWeightMeasurement>>

    suspend fun measurements(): List<BodyWeightMeasurement>

    /**
     * Records [weightKg] on [localEpochDay]. Without [replace] an existing measurement for that day
     * fails the call with [BodyWeightFailureReason.DUPLICATE_DATE] and writes nothing.
     */
    suspend fun add(localEpochDay: Long, weightKg: Double, replace: Boolean = false)

    /**
     * Edits the measurement on [previousLocalEpochDay], optionally moving it to [localEpochDay].
     * A target day occupied by another measurement fails with
     * [BodyWeightFailureReason.DATE_OCCUPIED] without overwriting it; a missing source fails with
     * [BodyWeightFailureReason.MISSING_MEASUREMENT].
     */
    suspend fun update(previousLocalEpochDay: Long, localEpochDay: Long, weightKg: Double)

    suspend fun delete(localEpochDay: Long)

    /** The current goal in kilograms, or null when none is set or it was explicitly cleared. */
    fun observeGoal(): Flow<Double?>

    /** Sets or replaces the current goal; null clears it. */
    suspend fun setGoal(weightKg: Double?)
}

/** Thrown when a body-weight change cannot be applied: invalid input or a conflicting date. */
class BodyWeightException(message: String, val reason: BodyWeightFailureReason) : Exception(message)

/** Typed body-weight failures the editor renders with localized guidance. */
enum class BodyWeightFailureReason {
    INVALID_WEIGHT,
    FUTURE_DATE,
    DUPLICATE_DATE,
    DATE_OCCUPIED,
    MISSING_MEASUREMENT
}
