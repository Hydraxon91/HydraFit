package com.hydrafit.app.core.domain.setup

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SkipSetupUseCase(
    private val repository: SetupWriteRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    suspend operator fun invoke(expected: SetupState, revisit: Boolean = false): SetupState =
        withContext(dispatcher) {
            val command = SetupCommit(expected, input = null, revisit = revisit)
            command.nextState()
            repository.commit(command)
        }
}
