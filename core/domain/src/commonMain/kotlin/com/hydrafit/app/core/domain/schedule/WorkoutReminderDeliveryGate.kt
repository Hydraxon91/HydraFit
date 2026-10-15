package com.hydrafit.app.core.domain.schedule

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-local linearization point between the reminder posting path and every writer that can
 * change whether a reminder may be posted or what it would say.
 *
 * The coordinator reads schedule state, the active activation, its occurrences, the reminder
 * preferences and the delivery ledger as one [snapshot], which returns the [Snapshot.token] for that
 * consistent view. Before any side effect it re-enters [ifCurrent] with that token: when a gated
 * writer committed in between, the token is stale and the action does not run, so a decision made
 * from an older view can never post.
 *
 * Every mutator that changes reminder-relevant state runs inside [mutate], which performs the write
 * and then invalidates every outstanding token. A mutation that fails or is cancelled still bumps the
 * revision, so an aborted or ABA-shaped change cannot leave an older token looking current. This is
 * linearization of database writes against posting, not a dispatcher assumption: a [Mutex] protects
 * the section regardless of which threads run the readers and writers.
 *
 * One instance must back the coordinator and every gated repository; a per-construction instance
 * would serialize nothing. The gate orders the database write against the posting decision, not the
 * operating system's notification display, and it cannot revoke an external effect already started.
 *
 * A gated action must not call a repository mutator that acquires the same gate: the non-reentrant
 * [Mutex] would deadlock, so mutator-to-mutator calls use an unlocked private helper instead.
 */
class WorkoutReminderDeliveryGate {
    private val lock = Mutex()
    private var revision = 0L

    /** Runs [read] with exclusive access and returns it with the revision it was read at. */
    suspend fun <T> snapshot(read: suspend () -> T): Snapshot<T> = lock.withLock {
        Snapshot(read(), revision)
    }

    /**
     * Runs [action] only while [token] is still the current revision, holding exclusive access for
     * its whole duration. Returns null when a mutation invalidated the token first, so the caller
     * re-reads instead of acting on the old view.
     */
    suspend fun <T> ifCurrent(token: Long, action: suspend () -> T): T? = lock.withLock {
        if (revision != token) return@withLock null
        action()
    }

    /**
     * Runs [mutation] with exclusive access, then invalidates every token captured before it,
     * returning its result. The revision is bumped even when [mutation] throws or is cancelled.
     */
    suspend fun <T> mutate(mutation: suspend () -> T): T = lock.withLock {
        try {
            mutation()
        } finally {
            revision++
        }
    }

    /** A consistent read and the revision token that must still be current to act on it. */
    data class Snapshot<T>(val value: T, val token: Long)
}
