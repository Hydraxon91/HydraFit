package com.hydrafit.app.core.domain.cardio

import kotlinx.coroutines.flow.Flow

/**
 * Storage for manually recorded cardio activities.
 *
 * The port deliberately exposes no session, occurrence or set relationship: cardio never participates
 * in resistance-session completion, set counts or modelled fatigue.
 */
interface CardioRepository {
    fun activitiesFlow(): Flow<List<CardioActivity>>

    suspend fun all(): List<CardioActivity>

    /** Inserts a new activity and returns its local id. A duplicate identity is refused. */
    suspend fun add(activity: CardioActivity): Long

    /** Replaces an existing activity's recorded values; the identity and local id never change. */
    suspend fun update(activity: CardioActivity)

    suspend fun delete(id: Long)

    /** The activity carrying [identity], or null. Used by add-only merge to avoid duplicates. */
    suspend fun byIdentity(identity: String): CardioActivity?
}
