package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.engine.SetTarget
import com.hydrafit.app.core.domain.engine.SlotProgressionPolicy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The target followed at submission, independent of mutable plans and local scheduling ids. */
@Serializable
data class WorkoutPrescriptionSnapshot(
    val version: Int = SNAPSHOT_VERSION,
    val exerciseId: String,
    val sets: Int,
    val reps: Int? = null,
    val durationSeconds: Int? = null,
    val loadKind: LoadKind,
    val weightKg: Double?,
    val isDeload: Boolean,
    /**
     * Format 2 and later: the slot's approved progression policy at submission. A format-1 target
     * cannot carry one, so a reader that predates the policy never receives a rule it cannot evaluate.
     */
    val progressionPolicy: SlotProgressionPolicy? = null,
    /**
     * Format 3 and later: this set's zero-based index in an ordered prescription, or null for a slot
     * whose sets share one repeated target. It is the only link between a performed set and the
     * prescribed slot it fulfils, so it is never inferred for a legacy row.
     */
    val setIndex: Int? = null,
    /** Format 3 and later: the prescribed reps in reserve for this set, or null when none. */
    val targetRir: Int? = null
) {
    fun requireValid() {
        require(version in MIN_SNAPSHOT_VERSION..SNAPSHOT_VERSION) {
            "Unsupported prescription snapshot version"
        }
        require(version >= POLICY_SNAPSHOT_VERSION || progressionPolicy == null) {
            "A format-$MIN_SNAPSHOT_VERSION target cannot carry a progression policy"
        }
        require(version >= ORDERED_SNAPSHOT_VERSION || (setIndex == null && targetRir == null)) {
            "A target before format $ORDERED_SNAPSHOT_VERSION cannot carry an ordered slot"
        }
        require(exerciseId.isNotBlank() && sets > 0) {
            "Invalid prescription identity or set count"
        }
        require(setIndex == null || setIndex in 0 until sets) {
            "An ordered slot index must address one of the slot's own sets"
        }
        require(targetRir == null || targetRir in SetTarget.MIN_RIR..SetTarget.MAX_RIR) {
            "A prescribed RIR must be between ${SetTarget.MIN_RIR} and ${SetTarget.MAX_RIR}"
        }
        requireValidSetMeasurement(reps, durationSeconds)
        WorkoutLoadPolicy.validateShape(loadKind, weightKg)
        progressionPolicy?.requireValid()
    }

    companion object {
        /** The format this build writes. */
        const val SNAPSHOT_VERSION: Int = 3

        /** The oldest format this build still reads. */
        const val MIN_SNAPSHOT_VERSION: Int = 1

        /** The first format that may carry a progression policy. */
        const val POLICY_SNAPSHOT_VERSION: Int = 2

        /** The first format that may carry an ordered slot index and a prescribed RIR. */
        const val ORDERED_SNAPSHOT_VERSION: Int = 3
    }
}

/** Strict persisted encoding; corrupt stored targets must never silently become eligible history. */
object WorkoutPrescriptionSnapshotJson {
    private val json = Json { encodeDefaults = true }

    fun encode(snapshot: WorkoutPrescriptionSnapshot): String {
        snapshot.requireValid()
        return json.encodeToString(WorkoutPrescriptionSnapshot.serializer(), snapshot)
    }

    fun decode(text: String): WorkoutPrescriptionSnapshot = json.decodeFromString(
        WorkoutPrescriptionSnapshot.serializer(),
        text
    ).also { it.requireValid() }
}
