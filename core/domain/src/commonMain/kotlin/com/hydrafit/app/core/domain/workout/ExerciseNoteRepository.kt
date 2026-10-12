package com.hydrafit.app.core.domain.workout

/** Maximum length, in characters, of a single user-written note. */
const val NOTE_MAX_LENGTH: Int = 2_000

/**
 * A persistent setup note for one catalog exercise, keyed by exercise id.
 *
 * A note is plain text the user writes about how they set up or perform an exercise. It is current
 * context, never a historical snapshot of a logged set, and it survives reload and backup. [save]
 * with empty text and [clear] both store an explicit empty note; a note that was never set is
 * absent, which [note] reports as null and which is deliberately distinct from an explicit clear.
 */
interface ExerciseNoteRepository {
    /** The note for [exerciseId]: its text, "" for an explicit clear, or null when never set. */
    suspend fun note(exerciseId: String): String?

    /** Stores [text] as the note for [exerciseId]; an empty [text] is stored as an explicit clear. */
    suspend fun save(exerciseId: String, text: String)

    /** Explicitly clears the note for [exerciseId], persisting an empty note that survives reload. */
    suspend fun clear(exerciseId: String)
}
