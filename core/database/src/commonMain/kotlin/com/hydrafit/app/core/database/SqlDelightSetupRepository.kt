package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import com.hydrafit.app.core.domain.setup.SetupDisposition
import com.hydrafit.app.core.domain.setup.SetupState as CommittedSetupState
import com.hydrafit.app.core.domain.setup.SetupStep
import com.hydrafit.app.core.userdata.settings.SetupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SqlDelightSetupRepository(private val database: HydraFitDatabase) : SetupRepository {
    override suspend fun current(): CommittedSetupState = database.setupQueries.selectState()
        .executeAsOne().toSetupState()

    override fun observe(): Flow<CommittedSetupState> = database.setupQueries.selectState()
        .asFlow().mapToOne(Dispatchers.Default).map { it.toSetupState() }
}

internal fun SetupState.toSetupState(): CommittedSetupState = CommittedSetupState(
    version = version.toInt(),
    disposition = SetupDisposition.valueOf(disposition),
    step = SetupStep.valueOf(step),
    revision = revision
)
