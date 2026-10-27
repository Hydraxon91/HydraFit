package com.hydrafit.app.core.domain.cardio

/**
 * The kinds of manual cardio activity HydraFit records. This deliberately stays small: no wider
 * taxonomy, no GPS, no wearables and no calorie estimation.
 */
enum class CardioActivityType {
    WALKING,
    RUNNING,
    CYCLING,
    ROWING,
    OTHER
}

/**
 * One manually recorded cardio activity.
 *
 * A cardio activity is **not** a resistance set: it never joins a workout occurrence, the set
 * completion counter, modelled muscle fatigue, estimated 1RM or any strength record. Duration is
 * required and distance is optional; when both are present the two describe the same activity, so a
 * pace or speed can be derived from their totals.
 *
 * [identity] is a stable, device-generated identity. It exists so an add-only merge can recognise an
 * activity it already holds across two devices, where local database ids are meaningless.
 */
data class CardioActivity(
    val id: Long = 0,
    val identity: String,
    val type: CardioActivityType,
    val startedAtMillis: Long,
    val durationSeconds: Int,
    val distanceMeters: Int? = null,
    val note: String? = null
) {
    fun requireValid() {
        require(identity.isNotBlank() && identity.length <= MAX_IDENTITY_LENGTH) {
            "A cardio activity needs a bounded identity"
        }
        require(startedAtMillis > 0) { "A cardio activity needs a start time" }
        require(durationSeconds in MIN_DURATION_SECONDS..MAX_DURATION_SECONDS) {
            "A cardio activity needs a duration between $MIN_DURATION_SECONDS and " +
                "$MAX_DURATION_SECONDS seconds"
        }
        require(distanceMeters == null || distanceMeters in 1..MAX_DISTANCE_METERS) {
            "A cardio distance must be positive whole metres when present"
        }
        require(note == null || note.length <= MAX_NOTE_LENGTH) {
            "A cardio note is too long"
        }
    }

    /** Metres per second, or null when no distance was recorded. */
    val metresPerSecond: Double?
        get() = distanceMeters?.let { it.toDouble() / durationSeconds }

    companion object {
        const val MAX_IDENTITY_LENGTH: Int = 64
        const val MIN_DURATION_SECONDS: Int = 1

        /** One day: a longer single activity is almost certainly a mistyped duration. */
        const val MAX_DURATION_SECONDS: Int = 24 * 60 * 60
        const val MAX_DISTANCE_METERS: Int = 1_000_000
        const val MAX_NOTE_LENGTH: Int = 2000
    }
}
