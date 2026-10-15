package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hydrafit.app.core.domain.fatigue.CoverageSet
import com.hydrafit.app.core.domain.fatigue.TrainingCoverageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** No catalog join: missing recorded involvement must remain visibly unclassified. */
class SqlDelightTrainingCoverageRepository(private val database: HydraFitDatabase) :
    TrainingCoverageRepository {
    override fun observeSets(): Flow<List<CoverageSet>> =
        database.workoutLogQueries.selectAllSets().asFlow().mapToList(Dispatchers.Default)
            .map { rows ->
                rows.map { row ->
                    CoverageSet(
                        performedAtMillis = row.performedAt,
                        isWarmup = row.isWarmup != 0L,
                        involvements = row.involvements?.let(::decodeInvolvements)
                    )
                }
            }.flowOn(Dispatchers.Default)
}
