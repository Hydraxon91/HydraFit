package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hydrafit.app.core.domain.settings.TrainingGuidancePreferenceRepository
import com.hydrafit.app.core.domain.settings.TrainingGuidancePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map

class SqlDelightTrainingGuidancePreferenceRepository(database: HydraFitDatabase) :
    TrainingGuidancePreferenceRepository {
    private val queries = database.trainingGuidancePreferenceQueries

    override fun observe() = queries.selectPreferences().asFlow()
        .mapToOneOrNull(Dispatchers.Default)
        .map { it.toPreferences() }

    override suspend fun get() = queries.selectPreferences().executeAsOneOrNull().toPreferences()

    override suspend fun setRestAlertsEnabled(enabled: Boolean) {
        queries.setAlerts(if (enabled) 1L else 0L)
    }

    override suspend fun setShortRestCoachingEnabled(enabled: Boolean) {
        queries.setCoaching(if (enabled) 1L else 0L)
    }

    private fun TrainingGuidancePreference?.toPreferences() = TrainingGuidancePreferences(
        restAlertsEnabled = this?.restAlertsEnabled == 1L,
        shortRestCoachingEnabled = this?.shortRestCoachingEnabled == 1L
    )
}
