package com.hydrafit.app.core.domain.workout

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
    val progressionPolicy: SlotProgressionPolicy? = null
) {
    fun requireValid() {
        require(version in MIN_SNAPSHOT_VERSION..SNAPSHOT_VERSION) {
            "Unsupported prescription snapshot version"
        }
        require(version >= POLICY_SNAPSHOT_VERSION || progressionPolicy == null) {
            "A format-$MIN_SNAPSHOT_VERSION target cannot carry a progression policy"
        }
        require(exerciseId.isNotBlank() && sets > 0) {
            "Invalid prescription identity or set count"
        }
        requireValidSetMeasurement(reps, durationSeconds)
        WorkoutLoadPolicy.validateShape(loadKind, weightKg)
        progressionPolicy?.requireValid()
    }

    companion object {
        /** The format this build writes. */
        const val SNAPSHOT_VERSION: Int = 2

        /** The oldest format this build still reads. */
        const val MIN_SNAPSHOT_VERSION: Int = 1

        /** The first format that may carry a progression policy. */
        const val POLICY_SNAPSHOT_VERSION: Int = 2
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
