package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.time.localEpochDay
import com.hydrafit.app.core.userdata.measurements.BodyWeightException
import com.hydrafit.app.core.userdata.measurements.BodyWeightFailureReason
import com.hydrafit.app.core.userdata.measurements.BodyWeightMeasurement
import com.hydrafit.app.core.userdata.measurements.BodyWeightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** One row per local civil day; every validation and the write it guards share one transaction. */
class SqlDelightBodyWeightRepository(
    private val database: HydraFitDatabase,
    private val timeProvider: TimeProvider
) : BodyWeightRepository {
    private val queries = database.bodyWeightQueries

    override fun observeMeasurements(): Flow<List<BodyWeightMeasurement>> =
        queries.selectAllMeasurements().asFlow().mapToList(Dispatchers.Default).map { rows ->
            rows.map { BodyWeightMeasurement(it.localEpochDay, it.weightKg) }
        }

    override suspend fun measurements(): List<BodyWeightMeasurement> =
        queries.selectAllMeasurements().executeAsList().map {
            BodyWeightMeasurement(it.localEpochDay, it.weightKg)
        }

    override suspend fun add(localEpochDay: Long, weightKg: Double, replace: Boolean) {
        requireValidWeight(weightKg)
        requireNotFuture(localEpochDay)
        database.transaction {
            if (!replace && queries.selectMeasurement(localEpochDay).executeAsOneOrNull() != null) {
                throw BodyWeightException(
                    "A measurement already exists for this day",
                    BodyWeightFailureReason.DUPLICATE_DATE
                )
            }
            if (replace) {
                queries.replaceMeasurement(localEpochDay, weightKg)
            } else {
                queries.insertMeasurement(localEpochDay, weightKg)
            }
        }
    }

    override suspend fun update(
        previousLocalEpochDay: Long,
        localEpochDay: Long,
        weightKg: Double
    ) {
        requireValidWeight(weightKg)
        requireNotFuture(localEpochDay)
        database.transaction {
            queries.selectMeasurement(previousLocalEpochDay).executeAsOneOrNull()
                ?: throw BodyWeightException(
                    "The measurement no longer exists",
                    BodyWeightFailureReason.MISSING_MEASUREMENT
                )
            if (localEpochDay == previousLocalEpochDay) {
                queries.updateMeasurement(weightKg, previousLocalEpochDay)
            } else {
                if (queries.selectMeasurement(localEpochDay).executeAsOneOrNull() != null) {
                    throw BodyWeightException(
                        "A measurement already exists for this day",
                        BodyWeightFailureReason.DATE_OCCUPIED
                    )
                }
                queries.deleteMeasurement(previousLocalEpochDay)
                queries.insertMeasurement(localEpochDay, weightKg)
            }
        }
    }

    override suspend fun delete(localEpochDay: Long) {
        database.transaction {
            queries.deleteMeasurement(localEpochDay)
        }
    }

    override fun observeGoal(): Flow<Double?> =
        queries.selectGoal().asFlow().mapToOneOrNull(Dispatchers.Default).map { it?.weightKg }

    override suspend fun setGoal(weightKg: Double?) {
        if (weightKg != null) requireValidWeight(weightKg)
        database.transaction {
            if (weightKg == null) queries.clearGoal() else queries.upsertGoal(weightKg)
        }
    }

    private fun requireValidWeight(weightKg: Double) {
        if (!weightKg.isFinite() || weightKg <= 0.0) {
            throw BodyWeightException(
                "Weight must be a positive number",
                BodyWeightFailureReason.INVALID_WEIGHT
            )
        }
    }

    private fun requireNotFuture(localEpochDay: Long) {
        val today = localEpochDay(timeProvider.nowMillis(), timeProvider.utcOffsetMillis())
        if (localEpochDay > today) {
            throw BodyWeightException(
                "A future date cannot be measured",
                BodyWeightFailureReason.FUTURE_DATE
            )
        }
    }
}
