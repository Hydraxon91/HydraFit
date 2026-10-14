package com.hydrafit.app.core.userdata.settings

import com.hydrafit.app.core.domain.setup.SetupState
import kotlinx.coroutines.flow.Flow

interface SetupRepository {
    suspend fun current(): SetupState

    fun observe(): Flow<SetupState>
}
