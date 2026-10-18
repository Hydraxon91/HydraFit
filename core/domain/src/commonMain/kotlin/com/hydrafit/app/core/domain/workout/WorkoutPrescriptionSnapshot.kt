package com.hydrafit.app.core.domain.workout

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The target followed at submission, independent of mutable plans and local scheduling ids. */
@Serializable
data class WorkoutPrescriptionSnapshot(
    val version: Int = 1,
    val exerciseId: String,
    val sets: Int,
    val reps: Int? = null,
    val durationSeconds: Int? = null,
    val loadKind: LoadKind,
    val weightKg: Double?,
    val isDeload: Boolean
) {
    fun requireValid() {
        require(version == 1) { "Unsupported prescription snapshot version" }
        require(exerciseId.isNotBlank() && sets > 0) {
            "Invalid prescription identity or set count"
        }
        requireValidSetMeasurement(reps, durationSeconds)
        WorkoutLoadPolicy.validateShape(loadKind, weightKg)
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
