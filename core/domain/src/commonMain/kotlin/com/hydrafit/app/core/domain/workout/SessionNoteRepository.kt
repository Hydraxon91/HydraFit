package com.hydrafit.app.core.domain.workout

/** A persistent note keyed to one workout-session identity, not copied across sessions. */
interface SessionNoteRepository {
    /** The note text, an empty string for an explicit clear, or null when never set. */
    suspend fun note(sessionId: String): String?

    /** Saves [text] for an existing session; empty text is an explicit clear. */
    suspend fun save(sessionId: String, text: String)

    /** Persists an explicit clear for an existing session. */
    suspend fun clear(sessionId: String)
}
