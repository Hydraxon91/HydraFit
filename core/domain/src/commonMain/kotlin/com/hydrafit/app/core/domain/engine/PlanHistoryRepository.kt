package com.hydrafit.app.core.domain.engine

import kotlinx.coroutines.flow.Flow

/** Stores plans the user explicitly accepted. Drafts are never persisted. */
interface PlanHistoryRepository {
    fun observeLatest(): Flow<AcceptedPlan?>

    /** Every accepted plan, newest first. */
    fun observeHistory(): Flow<List<AcceptedPlan>>

    suspend fun latest(): AcceptedPlan?

    suspend fun accept(plan: AcceptedPlan)

    suspend fun clear()
}
